package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.TradingPair;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.SQL_Database.TradingRulesSQL;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridPreviewService;

/** Liest und speichert die Oberflächeneinstellungen in SQLite. */
public final class CurrencySettingsRepository {

    public List<CurrencySettings> loadAll() throws SQLException {
        return loadAll(BotRuntime.activeBotId());
    }

    public List<CurrencySettings> loadAll(long botId) throws SQLException {
        String sql = "SELECT currency, buyStatus, buyAmount, maxBuyAmount, gridMode, "
                + "gridSpacing, SL, TSL, TSL_activate, TSL_decline "
                + "FROM botPairSettings WHERE bot_id = ? AND archived = 0 ORDER BY currency";
        List<CurrencySettings> result = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
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
        }
        return result;
    }

    public CurrencySettings load(String currency) throws SQLException {
        return load(BotRuntime.activeBotId(), currency);
    }

    public CurrencySettings load(long botId, String currency) throws SQLException {
        String normalized = CurrencySettings.normalizeCurrency(currency);
        return loadAll(botId).stream()
                .filter(settings -> settings.currency().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new SQLException("Handelspaar nicht gefunden: " + normalized));
    }

    public GridPreviewContext loadGridPreviewContext(String currency) throws SQLException {
        return loadGridPreviewContext(BotRuntime.activeBotId(), currency);
    }

    public GridPreviewContext loadGridPreviewContext(long botId, String currency) throws SQLException {
        String normalized = CurrencySettings.normalizeCurrency(currency);
        String sql = "SELECT c.allTimeHigh, r.tickSize, r.stepSize, "
                + "r.minOrderAmount, r.maxOrderAmount FROM botPairSettings b "
                + "JOIN currency c ON c.currency = b.currency "
                + "LEFT JOIN tradingRules r ON r.currency = c.currency "
                + "WHERE b.bot_id = ? AND b.currency = ? AND b.archived = 0";
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setString(2, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Handelspaar nicht gefunden: " + normalized);
                }
                return new GridPreviewContext(rs.getDouble("allTimeHigh"),
                        new GridPreviewService.Rules(decimal(rs.getString("tickSize")),
                                decimal(rs.getString("stepSize")),
                                number(rs.getString("minOrderAmount")),
                                number(rs.getString("maxOrderAmount"))));
            }
        }
    }

    /**
     * Fügt ein zuvor bei Fusion geprüftes Paar samt Exchange-Regeln atomar ein.
     * Ein früher archiviertes Paar wird dadurch wieder aktiviert.
     */
    public void addVerified(CurrencySettings settings, TradingPair pair) throws SQLException {
        addVerified(BotRuntime.activeBotId(), settings, pair);
    }

    public void addVerified(long botId, CurrencySettings settings, TradingPair pair)
            throws SQLException {
        String verifiedSymbol = FusionSymbol.compactPair(pair.getPair());
        if (!settings.currency().equals(verifiedSymbol)) {
            throw new IllegalArgumentException("Das bestätigte Fusion-Paar passt nicht zur Eingabe.");
        }
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                upsertCurrency(con, settings);
                upsertTradeSettings(con, settings);
                upsertBotPair(con, botId, settings);
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
        add(BotRuntime.activeBotId(), settings);
    }

    public void add(long botId, CurrencySettings settings) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                upsertCurrency(con, settings);
                upsertTradeSettings(con, settings);
                upsertBotPair(con, botId, settings);
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        }
    }

    public void save(CurrencySettings settings) throws SQLException {
        save(BotRuntime.activeBotId(), settings);
    }

    public void save(long botId, CurrencySettings settings) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE botPairSettings SET buyStatus = ?, buyAmount = ?, "
                                + "maxBuyAmount = ?, gridMode = ?, gridSpacing = ?, SL = ?, "
                                + "TSL = ?, TSL_activate = ?, TSL_decline = ? "
                                + "WHERE bot_id = ? AND currency = ? AND archived = 0")) {
                    ps.setString(1, settings.buyEnabled() ? "true" : "false");
                    ps.setDouble(2, settings.buyAmount());
                    ps.setDouble(3, settings.maxBuyAmount());
                    ps.setString(4, settings.gridMode().name());
                    ps.setDouble(5, settings.gridSpacing());
                    ps.setDouble(6, settings.stopLoss());
                    ps.setString(7, settings.trailingStopEnabled() ? "true" : "false");
                    ps.setDouble(8, settings.trailingStopActivation());
                    ps.setDouble(9, settings.trailingStopDecline());
                    ps.setLong(10, botId);
                    ps.setString(11, settings.currency());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "Handelspaar nicht gefunden: " + settings.currency());
                    }
                }
                // Kompatibilitätskopie für noch nicht migrierte Analysepfade.
                upsertCurrency(con, settings);
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
        return remove(BotRuntime.activeBotId(), currency);
    }

    public RemovalResult remove(long botId, String currency) throws SQLException {
        String normalized = CurrencySettings.normalizeCurrency(currency);
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                int positions = count(con,
                        "SELECT COUNT(*) FROM positions WHERE bot_id = ? AND currency = ?",
                        botId, normalized);
                if (tableExists(con, "paperPositions")) {
                    positions += count(con, "SELECT COUNT(*) FROM paperPositions "
                            + "WHERE bot_id = ? AND currency = ? AND status = 'OPEN'",
                            botId, normalized);
                }
                int unresolvedAttempts = tableExists(con, "buy_attempts")
                        ? count(con, "SELECT COUNT(*) FROM buy_attempts "
                                + "WHERE bot_id = ? AND currency_pair = ? AND state IN "
                                + "('SUBMITTING', 'RECONCILIATION_REQUIRED')", botId, normalized)
                        : 0;
                if (tableExists(con, "sell_attempts")) {
                    unresolvedAttempts += count(con, "SELECT COUNT(*) FROM sell_attempts "
                            + "WHERE bot_id = ? AND currency_pair = ? AND state IN "
                            + "('SUBMITTING', 'RECONCILIATION_REQUIRED')", botId, normalized);
                }
                if (tableExists(con, "paperOrders")) {
                    unresolvedAttempts += count(con, "SELECT COUNT(*) FROM paperOrders "
                            + "WHERE bot_id = ? AND currency = ? AND status = 'OPEN'",
                            botId, normalized);
                }

                if (positions > 0 || unresolvedAttempts > 0) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE botPairSettings SET buyStatus = 'false', archived = 1 "
                                    + "WHERE bot_id = ? AND currency = ?")) {
                        ps.setLong(1, botId);
                        ps.setString(2, normalized);
                        requireOne(ps.executeUpdate(), normalized);
                    }
                    con.commit();
                    return RemovalResult.archived(positions, unresolvedAttempts);
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM botPairSettings WHERE bot_id = ? AND currency = ?")) {
                    ps.setLong(1, botId);
                    ps.setString(2, normalized);
                    requireOne(ps.executeUpdate(), normalized);
                }
                // Globale Paar-/Regelmetadaten nur entfernen, wenn kein Bot sie mehr nutzt.
                if (count(con, "SELECT COUNT(*) FROM botPairSettings WHERE currency = ?",
                        normalized) == 0
                        && count(con, "SELECT COUNT(*) FROM positions WHERE currency = ?",
                                normalized) == 0) {
                    deleteByCurrency(con, "tradeSettings", normalized);
                    deleteByCurrency(con, "tradingRules", normalized);
                    deleteByCurrency(con, "strategyState", normalized);
                    try (PreparedStatement ps = con.prepareStatement(
                            "DELETE FROM currency WHERE currency = ?")) {
                        ps.setString(1, normalized);
                        ps.executeUpdate();
                    }
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
        return loadDashboardStats(BotRuntime.activeBotId());
    }

    public DashboardStats loadDashboardStats(long botId) throws SQLException {
        boolean paper = false;
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT mode FROM bots WHERE id = ?")) {
            ps.setLong(1, botId);
            try (ResultSet rs = ps.executeQuery()) {
                paper = rs.next() && "PAPER".equals(rs.getString(1));
            }
        }
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM botPairSettings WHERE bot_id = ? AND archived = 0) AS currencies, "
                + "(SELECT COUNT(*) FROM botPairSettings WHERE bot_id = ? AND archived = 0 "
                + "AND buyStatus IN ('true', '1')) AS enabled, "
                + (paper
                    ? "(SELECT COUNT(*) FROM paperOrders WHERE bot_id = ? AND status = 'OPEN') AS pending, "
                        + "(SELECT COUNT(*) FROM paperPositions WHERE bot_id = ? AND status = 'OPEN') AS positions, "
                        + "((SELECT COALESCE(SUM(buy_amount), 0) FROM paperPositions WHERE bot_id = ? AND status = 'OPEN') + "
                        + "(SELECT COALESCE(SUM(amount), 0) FROM paperOrders WHERE bot_id = ? AND status = 'OPEN')) AS capital"
                    : "(SELECT COUNT(*) FROM positions WHERE bot_id = ? AND Status = 0) AS pending, "
                        + "(SELECT COUNT(*) FROM positions WHERE bot_id = ? AND Status IN (1, 5, 7, 8)) AS positions, "
                        + "(SELECT COALESCE(SUM(BuyAmount), 0) FROM positions WHERE bot_id = ?) AS capital");
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            int parameterCount = paper ? 6 : 5;
            for (int i = 1; i <= parameterCount; i++) ps.setLong(i, botId);
            try (ResultSet rs = ps.executeQuery()) {
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
    }

    private static void upsertBotPair(Connection con, long botId, CurrencySettings settings)
            throws SQLException {
        String sql = "INSERT INTO botPairSettings (bot_id, currency, buyStatus, buyAmount, "
                + "maxBuyAmount, gridMode, gridSpacing, SL, TSL, TSL_activate, TSL_decline, archived) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0) "
                + "ON CONFLICT(bot_id, currency) DO UPDATE SET buyStatus = excluded.buyStatus, "
                + "buyAmount = excluded.buyAmount, maxBuyAmount = excluded.maxBuyAmount, "
                + "gridMode = excluded.gridMode, gridSpacing = excluded.gridSpacing, "
                + "SL = excluded.SL, TSL = excluded.TSL, TSL_activate = excluded.TSL_activate, "
                + "TSL_decline = excluded.TSL_decline, archived = 0";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setString(2, settings.currency());
            ps.setString(3, settings.buyEnabled() ? "true" : "false");
            ps.setDouble(4, settings.buyAmount());
            ps.setDouble(5, settings.maxBuyAmount());
            ps.setString(6, settings.gridMode().name());
            ps.setDouble(7, settings.gridSpacing());
            ps.setDouble(8, settings.stopLoss());
            ps.setString(9, settings.trailingStopEnabled() ? "true" : "false");
            ps.setDouble(10, settings.trailingStopActivation());
            ps.setDouble(11, settings.trailingStopDecline());
            ps.executeUpdate();
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
                + "minOrderAmount, maxOrderAmount, supportedOrderTypes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(currency) DO UPDATE SET tickSize = excluded.tickSize, "
                + "stepSize = excluded.stepSize, minQty = excluded.minQty, "
                + "amountIncrement = excluded.amountIncrement, "
                + "maxOrderSize = excluded.maxOrderSize, "
                + "minOrderAmount = excluded.minOrderAmount, "
                + "maxOrderAmount = excluded.maxOrderAmount, "
                + "supportedOrderTypes = excluded.supportedOrderTypes";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, FusionSymbol.compactPair(pair.getPair()));
            ps.setString(2, pair.getTickSize());
            ps.setString(3, pair.getSizeIncrement());
            ps.setString(4, pair.getSizeIncrement());
            ps.setString(5, pair.getAmountIncrement());
            ps.setString(6, pair.getMaxOrderSize());
            ps.setString(7, pair.getMinOrderAmount());
            ps.setString(8, pair.getMaxOrderAmount());
            String orderTypes=pair.getSupportedOrderTypes()==null||pair.getSupportedOrderTypes().isEmpty()
                    ?"LIMIT,MARKET,STOP_LIMIT,STOP_MARKET"
                    :pair.getSupportedOrderTypes().stream().map(Enum::name).sorted()
                            .collect(Collectors.joining(","));
            ps.setString(9,orderTypes);
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

    private static int count(Connection con, String sql, long botId, String currency)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            ps.setString(2, currency);
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
        return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
    }

    private static boolean toBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double number(String value) {
        try {
            return value == null ? 0.0 : Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
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

    public record GridPreviewContext(double allTimeHigh,
            GridPreviewService.Rules rules) { }
}
