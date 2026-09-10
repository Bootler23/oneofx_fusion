package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.domain.Position;

/**
 * DAO für die positions-Tabelle. Ersetzt POSSQL (statisch) durch Instanz-Methoden
 * mit modularem insert/update (nur non-null Felder) und PreparedStatements überall.
 */
public class PositionDAO {
    private static final Object MIGRATION_LOCK = new Object();
    private static final Set<String> MIGRATED_DATABASES = ConcurrentHashMap.newKeySet();

    private static final String BOT_SCOPE =
            " AND bot_id = (SELECT selected_bot_id FROM runtimeState WHERE id = 1) ";

    private Connection getConnection() throws SQLException {
        String url = dbUrl.getoneOfX();
        Connection con = SQLiteConnectionFactory.open(url);
        try {
            ensureBotScopeOnce(con, url);
            return con;
        } catch (SQLException ex) {
            try { con.close(); } catch (SQLException close) { ex.addSuppressed(close); }
            throw ex;
        }
    }

    private static void ensureBotScopeOnce(Connection con, String url) throws SQLException {
        if (MIGRATED_DATABASES.contains(url)) return;
        synchronized (MIGRATION_LOCK) {
            if (MIGRATED_DATABASES.contains(url)) return;
            ensureBotScope(con);
            MIGRATED_DATABASES.add(url);
        }
    }

    /** Hält auch gezielt erzeugte Legacy-/Testdatenbanken lesbar. */
    private static void ensureBotScope(Connection con) throws SQLException {
        boolean botColumn = hasColumn(con, "positions", "bot_id");
        try (java.sql.Statement statement = con.createStatement()) {
            if (!botColumn) statement.executeUpdate("ALTER TABLE positions ADD COLUMN bot_id INTEGER");
            statement.executeUpdate("UPDATE positions SET bot_id = 1 WHERE bot_id IS NULL");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS runtimeState "
                    + "(id INTEGER PRIMARY KEY, selected_bot_id INTEGER NOT NULL)");
            statement.executeUpdate("INSERT OR IGNORE INTO runtimeState VALUES (1, 1)");
        }
    }

    // ===================== INSERT (modular) =====================

    public void insert(Position pos) throws SQLException {
        try (Connection con = getConnection()) {
            insert(con, pos);
        }
    }

    public void insert(Connection con, Position pos) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (hasColumn(con, "positions", "bot_id")) {
            columns.add("bot_id");
            values.add(BotRuntime.activeBotId());
        }

        columns.add("currency");  values.add(pos.getCurrency());
        columns.add("BuyOrderId"); values.add(pos.getBuyOrderId());

        if (pos.getOrderPrice() != null)  { columns.add("OrderPrice");  values.add(pos.getOrderPrice()); }
        if (pos.getOrigPrice() != null)   { columns.add("OrigPrice");   values.add(pos.getOrigPrice()); }
        if (pos.getQuantity() != null)    { columns.add("quantity");    values.add(pos.getQuantity()); }
        if (pos.getBuyAmount() != null)   { columns.add("BuyAmount");   values.add(pos.getBuyAmount()); }
        if (pos.getBuyPrice() != null)    { columns.add("BuyPrice");    values.add(pos.getBuyPrice()); }
        if (pos.getBuyDate() != null)     { columns.add("BuyDate");     values.add(pos.getBuyDate()); }
        if (pos.getBuyTime() != null)     { columns.add("BuyTime");     values.add(pos.getBuyTime()); }
        if (pos.getStatus() != null)      { columns.add("Status");      values.add(pos.getStatus()); }
        if (pos.getStatusCode() != null)  { columns.add("statusCode");  values.add(pos.getStatusCode()); }
        if (pos.getPeakPrice() != null)   { columns.add("peakPrice");   values.add(pos.getPeakPrice()); }
        if (pos.getTsl() != null)         { columns.add("TSL");         values.add(pos.getTsl()); }
        if (pos.getProfit() != null)      { columns.add("Profit");      values.add(pos.getProfit()); }
        if (pos.getOrderOrigin() != null) { columns.add("orderOrigin"); values.add(pos.getOrderOrigin()); }

        String cols = String.join(", ", columns);
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toArray(String[]::new));
        String sql = "INSERT INTO positions (" + cols + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("Insert in positions hat " + rows + " Zeilen geaendert; erwartet wurde 1");
            }
        }
    }

    private static boolean hasColumn(Connection con, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            return false;
        }
    }

    // ===================== UPDATE (modular) =====================

    public void update(Position pos) {
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (pos.getOrderPrice() != null)  { setClauses.add("OrderPrice = ?");  values.add(pos.getOrderPrice()); }
        if (pos.getOrigPrice() != null)   { setClauses.add("OrigPrice = ?");   values.add(pos.getOrigPrice()); }
        if (pos.getQuantity() != null)    { setClauses.add("quantity = ?");    values.add(pos.getQuantity()); }
        if (pos.getBuyAmount() != null)   { setClauses.add("BuyAmount = ?");   values.add(pos.getBuyAmount()); }
        if (pos.getBuyPrice() != null)    { setClauses.add("BuyPrice = ?");    values.add(pos.getBuyPrice()); }
        if (pos.getBuyDate() != null)     { setClauses.add("BuyDate = ?");     values.add(pos.getBuyDate()); }
        if (pos.getBuyTime() != null)     { setClauses.add("BuyTime = ?");     values.add(pos.getBuyTime()); }
        if (pos.getStatus() != null)      { setClauses.add("Status = ?");      values.add(pos.getStatus()); }
        if (pos.getStatusCode() != null)  { setClauses.add("statusCode = ?");  values.add(pos.getStatusCode()); }
        if (pos.getPeakPrice() != null)   { setClauses.add("peakPrice = ?");   values.add(pos.getPeakPrice()); }
        if (pos.getTsl() != null)         { setClauses.add("TSL = ?");         values.add(pos.getTsl()); }
        if (pos.getProfit() != null)      { setClauses.add("Profit = ?");      values.add(pos.getProfit()); }
        if (pos.getOrderOrigin() != null) { setClauses.add("orderOrigin = ?"); values.add(pos.getOrderOrigin()); }
        if (pos.getCurrency() != null)    { setClauses.add("currency = ?");   values.add(pos.getCurrency()); }

        if (setClauses.isEmpty()) return;

        String sql = "UPDATE positions SET " + String.join(", ", setClauses) + " WHERE BuyOrderId = ?";
        values.add(pos.getBuyOrderId());

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Update in positions: " + e.getMessage());
        }
    }

    // ===================== DELETE =====================

    public void delete(String buyOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM positions WHERE BuyOrderId = ?")) {
            ps.setString(1, buyOrderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Löschen aus positions: " + e.getMessage());
        }
    }

    public void delete(Long buyOrderId) {
        delete(String.valueOf(buyOrderId));
    }

    // ===================== Queries (aus POSSQL migriert) =====================

    public double getLastDownSidePrice(String currencyPair, List<Double> livePrice) {
        double priceMin = Double.MAX_VALUE;
        boolean found = false;

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT OrderPrice FROM positions WHERE Status IN (0, 1, 5) AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double p = rs.getDouble("OrderPrice");
                if (p < priceMin) { priceMin = p; found = true; }
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }

        if (!found && !livePrice.isEmpty()) {
            priceMin = livePrice.get(0);
        }
        return priceMin;
    }

    public boolean positionExistsAtPrice(String currencyPair, double price) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) AS count FROM positions WHERE Status IN (0, 1, 5) AND currency = ? AND ABS(OrderPrice - ?) < 0.0001" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            ps.setDouble(2, price);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler in positionExistsAtPrice: " + e.getMessage());
            return true;
        }
        return false;
    }

    /** Zählt offene PENDING-Orders (Status=0) für ein Währungspaar. */
    public int countPendingOrders(String currencyPair) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM positions WHERE Status = 0 AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("cnt");
            }
        } catch (SQLException e) {
            System.err.println("[FEHLER] countPendingOrders: " + e.getMessage());
            // Im Zweifel sperren: Bei unbekanntem Datenbankzustand keine
            // möglichen Doppelorders an die Börse senden.
            return Integer.MAX_VALUE;
        }
        return 0;
    }

    public int countOpenPositions(String currencyPair) {
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM positions WHERE Status IN (1,5,7,8) AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : Integer.MAX_VALUE; }
        } catch (SQLException ex) { return Integer.MAX_VALUE; }
    }

    public double getLowestOpenEntryPrice(String currencyPair) {
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(
                "SELECT MIN(BuyPrice) FROM positions WHERE Status IN (1,5,7,8) AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        } catch (SQLException ex) { return 0; }
    }

    public List<String> getBuyOrderIdsWhereStatusZero(String currencyPair) {
        List<String> ids = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId FROM positions WHERE Status = 0 AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("BuyOrderId"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return ids;
    }

    /** Manual orders are reconciled normally but must not be managed by grid/regime rules. */
    public boolean isManualOrder(String buyOrderId) {
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(
                "SELECT orderOrigin FROM positions WHERE BuyOrderId = ?" + BOT_SCOPE)) {
            ps.setString(1, buyOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "MANUAL".equalsIgnoreCase(rs.getString(1));
            }
        } catch (SQLException ex) {
            System.err.println("Order-Herkunft konnte nicht gelesen werden: " + ex.getMessage());
            return false;
        }
    }

    public List<String> getBuyTradeRecordsWhereStatusFive() {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId, currency FROM positions WHERE Status = 5" + BOT_SCOPE)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(rs.getString("BuyOrderId") + ", " + rs.getString("currency"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    public double getSumBuyAmount() {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT SUM(BuyAmount) AS SumBuyAmount FROM positions WHERE 1=1" + BOT_SCOPE)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("SumBuyAmount");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public double getSumQuantity() {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT SUM(quantity) AS SumQuantity FROM positions WHERE 1=1" + BOT_SCOPE)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("SumQuantity");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public double getSumQuantityForCurrency(String currencyPair) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT SUM(quantity) AS SumQuantity FROM positions WHERE Status IN (0, 1, 5) AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("SumQuantity");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public double getSumColumn(String url, String columnName, String tableName) {
        String sql = "SELECT SUM(" + sanitizeIdentifier(columnName) + ") AS val FROM " + sanitizeIdentifier(tableName);
        try (Connection con = SQLiteConnectionFactory.open(url);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("val");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0.0;
    }

    public int getCountPOS(String currencyPair) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) AS RecordCount FROM positions WHERE Status IN (0, 1) AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("RecordCount");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return 0;
    }

    public Double getAverageBuyAmount() {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT AVG(BuyAmount) AS AverageBuyAmount FROM positions WHERE 1=1" + BOT_SCOPE)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("AverageBuyAmount");
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return null;
    }

    public List<String> getDataRecordsWhereStatusOneOrSeven(String currencyPair) {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate, BuyTime " +
                      "FROM positions WHERE Status IN (1, 7) AND currency = ? AND BuyAmount > 0" + BOT_SCOPE)) {
            ps.setString(1, currencyPair);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(buildDataRecord(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    public List<String> getDataRecordsWithMaxInMinus(String currency, double livePrice) {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection()) {
            // Zuerst Status = 7
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate, BuyTime " +
                    "FROM positions WHERE Status = 7 AND currency = ?" + BOT_SCOPE)) {
                ps.setString(1, currency);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    records.add(buildDataRecord(rs));
                }
            }
            // Falls nichts mit Status 7, dann Status 1 mit max Minus
            if (records.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate, BuyTime " +
                        "FROM positions WHERE Status = 1 AND currency = ? AND BuyAmount > 10 " + BOT_SCOPE +
                        "ORDER BY (BuyPrice - ?) DESC LIMIT 1")) {
                    ps.setString(1, currency);
                    ps.setDouble(2, livePrice);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        records.add(buildDataRecord(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    // ---- Trailing Stop Loss peakPrice ----

    public double getPeakPrice(String buyOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT peakPrice FROM positions WHERE BuyOrderId = ?")) {
            ps.setString(1, buyOrderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("peakPrice");
        } catch (SQLException e) {
            System.err.println("Fehler beim Lesen von peakPrice: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Ermittelt das bereits gebundene Kapital anhand der Anschaffungskosten statt
     * des aktuellen Marktwerts. Offene und ungeklärte Kaufübermittlungen werden
     * mitgezählt, damit fallende Kurse kein künstliches Kaufbudget freigeben.
     */
    public double getCommittedBuyAmount(String currencyPair) {
        try (Connection con = getConnection()) {
            boolean hasAttemptJournal;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'buy_attempts'");
                 ResultSet rs = ps.executeQuery()) {
                hasAttemptJournal = rs.next();
            }

            if (!hasAttemptJournal) {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT COALESCE(SUM(BuyAmount), 0) FROM positions WHERE currency = ?" + BOT_SCOPE)) {
                    ps.setString(1, currencyPair);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getDouble(1) : 0.0;
                    }
                }
            }

            double committed = 0.0;
            String positionSql = "SELECT COALESCE(SUM(CASE "
                    + "WHEN p.BuyAmount IS NOT NULL AND p.BuyAmount > 0 THEN p.BuyAmount "
                    + "ELSE CAST(a.quantity AS REAL) * CAST(a.limit_price AS REAL) END), 0) "
                    + "FROM positions p LEFT JOIN buy_attempts a "
                    + "ON a.exchange_order_id = p.BuyOrderId WHERE p.currency = ?"
                    + " AND p.bot_id = (SELECT selected_bot_id FROM runtimeState WHERE id = 1)";
            try (PreparedStatement ps = con.prepareStatement(positionSql)) {
                ps.setString(1, currencyPair);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) committed += rs.getDouble(1);
                }
            }

            String attemptBotScope = columnExists(con, "buy_attempts", "bot_id")
                    ? "AND a.bot_id = (SELECT selected_bot_id FROM runtimeState WHERE id = 1) "
                    : "";
            String unresolvedSql = "SELECT COALESCE(SUM(CAST(a.quantity AS REAL) "
                    + "* CAST(a.limit_price AS REAL)), 0) FROM buy_attempts a "
                    + "LEFT JOIN positions p ON p.BuyOrderId = a.exchange_order_id "
                    + "WHERE a.currency_pair = ? " + attemptBotScope
                    + "AND a.state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED') "
                    + "AND p.BuyOrderId IS NULL";
            try (PreparedStatement ps = con.prepareStatement(unresolvedSql)) {
                ps.setString(1, currencyPair);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) committed += rs.getDouble(1);
                }
            }
            return committed;
        } catch (SQLException e) {
            System.err.println("Kapitalbindung fuer " + currencyPair
                    + " konnte nicht bestimmt werden: " + e.getMessage());
            // Im Zweifel sperren: Unbekannte Kapitalbindung darf keinen Kauf erlauben.
            return Double.POSITIVE_INFINITY;
        }
    }

    public boolean isTrailingStopActive(String buyOrderId) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT TSL FROM positions WHERE BuyOrderId = ?")) {
            ps.setString(1, buyOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "active".equalsIgnoreCase(rs.getString("TSL"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Lesen des TSL-Status: " + e.getMessage());
        }
        return false;
    }

    public void updatePeakPrice(String buyOrderId, double peakPrice) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE positions SET peakPrice = ? WHERE BuyOrderId = ?")) {
            ps.setDouble(1, peakPrice);
            ps.setString(2, buyOrderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Aktualisieren von peakPrice: " + e.getMessage());
        }
    }

    public List<String> getPositionWithMaxInMinus(String currencyPair, double livePrice) {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId, quantity, currency, BuyPrice, BuyAmount FROM positions " +
                     "WHERE Status = 1 AND BuyAmount >= 10 AND currency = ? " + BOT_SCOPE +
                     "ORDER BY (BuyPrice - ?) DESC LIMIT 1")) {
            ps.setString(1, currencyPair);
            ps.setDouble(2, livePrice);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(rs.getString("BuyOrderId") + ", " + rs.getString("quantity") + ", " + rs.getString("currency"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    public List<String> getTwoPositions(String currency) {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId, quantity, currency, BuyPrice, BuyAmount FROM positions " +
                     "WHERE Status = 1 AND BuyAmount < 10 AND currency = ?" + BOT_SCOPE)) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(rs.getString("BuyOrderId") + ", " + rs.getString("quantity") + ", "
                        + rs.getString("currency") + ", " + rs.getString("BuyPrice") + ", " + rs.getString("BuyAmount"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    public List<String> getPositionSmallerThen10AndMinus7Percent(String currency, double livePrice) {
        List<String> records = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT BuyOrderId, quantity, currency, BuyPrice, BuyAmount FROM positions " +
                     "WHERE currency = ? AND BuyAmount < 10 AND Status = 1 AND (BuyPrice - ?) / BuyPrice >= 0.07" + BOT_SCOPE)) {
            ps.setString(1, currency);
            ps.setDouble(2, livePrice);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(rs.getString("BuyOrderId") + ", " + rs.getString("quantity") + ", "
                        + rs.getString("currency") + ", " + rs.getString("BuyPrice") + ", " + rs.getString("BuyAmount"));
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler: " + e.getMessage());
        }
        return records;
    }

    // ===================== Hilfsmethoden =====================

    private String buildDataRecord(ResultSet rs) throws SQLException {
        return rs.getString("BuyOrderId") + ", " + rs.getString("OrderPrice") + ", "
                + rs.getString("OrigPrice") + ", " + rs.getString("quantity") + ", "
                + rs.getString("BuyAmount") + ", " + rs.getString("BuyPrice") + ", "
                + rs.getString("BuyDate") + ", " + rs.getString("BuyTime");
    }

    private String sanitizeIdentifier(String name) {
        return name.replaceAll("[^a-zA-Z0-9_€]", "");
    }

    private static boolean columnExists(Connection con, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
            return false;
        }
    }
}
