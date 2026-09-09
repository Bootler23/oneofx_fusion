package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.TradingPair;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.SQL_Database.TradingRulesSQL;
import com.oneofx.fusion.tradingbot.grid.GridMode;

/** Liest und speichert die Oberflächeneinstellungen in SQLite. */
public final class CurrencySettingsRepository {

    public List<CurrencySettings> loadAll() throws SQLException {
        String sql = "SELECT c.currency, c.buyStatus, c.buyAmount, c.maxBuyAmount, "
                + "c.gridMode, c.gridSpacing, "
                + "COALESCE(t.SL, 0) AS SL, COALESCE(t.TSL, 'false') AS TSL, "
                + "COALESCE(t.TSL_activate, 0) AS TSL_activate, "
                + "COALESCE(t.TSL_decline, 0) AS TSL_decline "
                + "FROM currency c LEFT JOIN tradeSettings t ON t.currency = c.currency "
                + "WHERE COALESCE(c.archived, 0) = 0 ORDER BY c.currency";
        List<CurrencySettings> result = new ArrayList<>();
        try (Connection con = open();
             Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new CurrencySettings(
                        rs.getString("currency"),
                        toBoolean(rs.getString("buyStatus")),
                        rs.getDouble("buyAmount"),
                        rs.getDouble("maxBuyAmount"),
                        GridMode.fromDatabase(rs.getString("gridMode")),
                        rs.getDouble("gridSpacing"),
                        Math.abs(rs.getDouble("SL")),
                        toBoolean(rs.getString("TSL")),
                        rs.getDouble("TSL_activate"),
                        rs.getDouble("TSL_decline")));
            }
        }
        return result;
    }

    public CurrencySettings load(String currency) throws SQLException {
        String normalized = CurrencySettings.normalizeCurrency(currency);
        return loadAll().stream()
                .filter(settings -> settings.currency().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new SQLException("Handelspaar nicht gefunden: " + normalized));
    }

    /**
     * Fügt ein zuvor bei Fusion geprüftes Paar samt Exchange-Regeln atomar ein.
     * Ein früher archiviertes Paar wird dadurch wieder aktiviert.
     */
    public void addVerified(CurrencySettings settings, TradingPair pair) throws SQLException {
        String verifiedSymbol = FusionSymbol.compactPair(pair.getPair());
        if (!settings.currency().equals(verifiedSymbol)) {
            throw new IllegalArgumentException("Das bestätigte Fusion-Paar passt nicht zur Eingabe.");
        }
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                upsertCurrency(con, settings);
                upsertTradeSettings(con, settings);
                upsertTradingRules(con, pair);
                con.commit();
                TradingRulesSQL.clearCache();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    /** Für kontrollierte Importe und Tests ohne externen API-Aufruf. */
    public void add(CurrencySettings settings) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                upsertCurrency(con, settings);
                upsertTradeSettings(con, settings);
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public void save(CurrencySettings settings) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE currency SET buyStatus = ?, buyAmount = ?, "
                                + "maxBuyAmount = ?, gridMode = ?, gridSpacing = ?, grid = ? "
                                + "WHERE currency = ? AND COALESCE(archived, 0) = 0")) {
                    ps.setString(1, settings.buyEnabled() ? "true" : "false");
                    ps.setDouble(2, settings.buyAmount());
                    ps.setDouble(3, settings.maxBuyAmount());
                    ps.setString(4, settings.gridMode().name());
                    ps.setDouble(5, settings.gridSpacing());
                    ps.setInt(6, legacyGrid(settings));
                    ps.setString(7, settings.currency());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Handelspaar nicht gefunden: " + settings.currency());
                    }
                }
                upsertTradeSettings(con, settings);
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    /**
     * Löscht ein unbenutztes Paar. Paare mit Positionsdaten werden deaktiviert
     * und archiviert, damit Orderabgleich und Historie erhalten bleiben.
     */
    public RemovalResult remove(String currency) throws SQLException {
        String normalized = CurrencySettings.normalizeCurrency(currency);
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                int positions = count(con,
                        "SELECT COUNT(*) FROM positions WHERE currency = ?", normalized);
                int unresolvedAttempts = tableExists(con, "buy_attempts")
                        ? count(con, "SELECT COUNT(*) FROM buy_attempts "
                                + "WHERE currency_pair = ? AND state IN "
                                + "('SUBMITTING', 'RECONCILIATION_REQUIRED')", normalized)
                        : 0;

                if (positions > 0 || unresolvedAttempts > 0) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE currency SET buyStatus = 'false', archived = 1 "
                                    + "WHERE currency = ?")) {
                        ps.setString(1, normalized);
                        requireOne(ps.executeUpdate(), normalized);
                    }
                    con.commit();
                    return RemovalResult.archived(positions, unresolvedAttempts);
                }

                deleteByCurrency(con, "tradeSettings", normalized);
                deleteByCurrency(con, "tradingRules", normalized);
                deleteByCurrency(con, "strategyState", normalized);
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM currency WHERE currency = ?")) {
                    ps.setString(1, normalized);
                    requireOne(ps.executeUpdate(), normalized);
                }
                con.commit();
                TradingRulesSQL.clearCache();
                return RemovalResult.deleted();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public DashboardStats loadDashboardStats() throws SQLException {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM currency WHERE COALESCE(archived, 0) = 0) AS currencies, "
                + "(SELECT COUNT(*) FROM currency WHERE COALESCE(archived, 0) = 0 "
                + "AND buyStatus IN ('true', '1')) AS enabled, "
                + "(SELECT COUNT(*) FROM positions WHERE Status = 0) AS pending, "
                + "(SELECT COUNT(*) FROM positions WHERE Status IN (1, 5, 7, 8)) AS positions, "
                + "(SELECT COALESCE(SUM(BuyAmount), 0) FROM positions) AS capital";
        try (Connection con = open();
             Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                throw new SQLException("Dashboardwerte konnten nicht geladen werden.");
            }
            return new DashboardStats(
                    rs.getInt("currencies"),
                    rs.getInt("enabled"),
                    rs.getInt("pending"),
                    rs.getInt("positions"),
                    rs.getDouble("capital"));
        }
    }

    private static void upsertCurrency(Connection con, CurrencySettings settings)
            throws SQLException {
        String sql = "INSERT INTO currency "
                + "(currency, buyStatus, buyAmount, maxBuyAmount, grid, gridMode, "
                + "gridSpacing, allTimeHigh, archived) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0.000000001, 0) "
                + "ON CONFLICT(currency) DO UPDATE SET "
                + "buyStatus = excluded.buyStatus, buyAmount = excluded.buyAmount, "
                + "maxBuyAmount = excluded.maxBuyAmount, grid = excluded.grid, "
                + "gridMode = excluded.gridMode, gridSpacing = excluded.gridSpacing, archived = 0";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, settings.currency());
            ps.setString(2, settings.buyEnabled() ? "true" : "false");
            ps.setDouble(3, settings.buyAmount());
            ps.setDouble(4, settings.maxBuyAmount());
            ps.setInt(5, legacyGrid(settings));
            ps.setString(6, settings.gridMode().name());
            ps.setDouble(7, settings.gridSpacing());
            ps.executeUpdate();
        }
    }

    private static void upsertTradeSettings(Connection con, CurrencySettings settings)
            throws SQLException {
        String sql = "INSERT INTO tradeSettings "
                + "(currency, TP, SL, TSL, TSL_activate, TSL_decline) "
                + "VALUES (?, 0, ?, ?, ?, ?) ON CONFLICT(currency) DO UPDATE SET "
                + "SL = excluded.SL, TSL = excluded.TSL, "
                + "TSL_activate = excluded.TSL_activate, "
                + "TSL_decline = excluded.TSL_decline";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, settings.currency());
            ps.setDouble(2, settings.stopLoss());
            ps.setString(3, settings.trailingStopEnabled() ? "true" : "false");
            ps.setDouble(4, settings.trailingStopActivation());
            ps.setDouble(5, settings.trailingStopDecline());
            ps.executeUpdate();
        }
    }

    private static void upsertTradingRules(Connection con, TradingPair pair)
            throws SQLException {
        String sql = "INSERT INTO tradingRules "
                + "(currency, tickSize, stepSize, minQty, amountIncrement, maxOrderSize, "
                + "minOrderAmount, maxOrderAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(currency) DO UPDATE SET tickSize = excluded.tickSize, "
                + "stepSize = excluded.stepSize, minQty = excluded.minQty, "
                + "amountIncrement = excluded.amountIncrement, "
                + "maxOrderSize = excluded.maxOrderSize, "
                + "minOrderAmount = excluded.minOrderAmount, "
                + "maxOrderAmount = excluded.maxOrderAmount";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, FusionSymbol.compactPair(pair.getPair()));
            ps.setString(2, pair.getTickSize());
            ps.setString(3, pair.getSizeIncrement());
            ps.setString(4, pair.getSizeIncrement());
            ps.setString(5, pair.getAmountIncrement());
            ps.setString(6, pair.getMaxOrderSize());
            ps.setString(7, pair.getMinOrderAmount());
            ps.setString(8, pair.getMaxOrderAmount());
            ps.executeUpdate();
        }
    }

    private static int legacyGrid(CurrencySettings settings) {
        if (settings.gridMode() != GridMode.GEOMETRIC) return 1;
        return Math.max(1, (int) Math.round(1.0 / settings.gridSpacing()));
    }

    private static int count(Connection con, String sql, String currency)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void deleteByCurrency(Connection con, String table, String currency)
            throws SQLException {
        if (!tableExists(con, table)) return;
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM " + table + " WHERE currency = ?")) {
            ps.setString(1, currency);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection con, String table) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void requireOne(int changedRows, String currency) throws SQLException {
        if (changedRows != 1) {
            throw new SQLException("Handelspaar nicht gefunden: " + currency);
        }
    }

    private static Connection open() throws SQLException {
        Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
        try (Statement statement = con.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return con;
    }

    private static boolean toBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static void rollback(Connection con, Exception original) {
        try {
            con.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    public record RemovalResult(boolean archived, int positions, int unresolvedAttempts) {
        static RemovalResult deleted() {
            return new RemovalResult(false, 0, 0);
        }

        static RemovalResult archived(int positions, int unresolvedAttempts) {
            return new RemovalResult(true, positions, unresolvedAttempts);
        }
    }
}
