package com.oneofx.fusion.tradingbot.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Prüft die gemeinsame OneOfX-Datenbank und führt additive Schemaänderungen aus.
 * Vorhandene Tabellen und Daten werden hier niemals gelöscht oder umbenannt.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {
    }

    public static void initialize() {
        try {
            initializeTradingDatabase(dbUrl.getoneOfX());
            initializeSettingsDatabase(dbUrl.getSET());
            initializeWpdDatabase(dbUrl.getWPD());
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankschema konnte nicht initialisiert werden", e);
        }
    }

    static void initializeTradingDatabase(String jdbcUrl) throws SQLException {
        try (Connection con = open(jdbcUrl)) {
            con.setAutoCommit(false);
            try {
                createCoreTradingTables(con);
                createTradeSettings(con);
                createTradingRules(con);
                createPerformance(con);
                createStrategyState(con);

                addColumnIfMissing(con, "historyPosition", "Split", "REAL");
                addColumnIfMissing(con, "historyPosition", "Balance_atBuy", "REAL");
                addColumnIfMissing(con, "historyPosition", "Asset_atBuy", "REAL");
                addColumnIfMissing(con, "historyPosition", "BalanceToAsset_atBuy", "REAL");
                addColumnIfMissing(con, "historyPosition", "POS_count", "INTEGER");
                addColumnIfMissing(con, "historyPosition", "X", "REAL");

                // Persistenter Zustand des Trailing Stops je offener Position.
                addColumnIfMissing(con, "positions", "TSL", "TEXT DEFAULT 'inactive'");
                addColumnIfMissing(con, "positions", "peakPrice", "REAL");

                addColumnIfMissing(con, "currency", "maxBuyAmount", "REAL DEFAULT 500.0");
                addColumnIfMissing(con, "currency", "gridMode",
                        "TEXT NOT NULL DEFAULT 'GEOMETRIC'");
                addColumnIfMissing(con, "currency", "gridSpacing", "REAL");
                addColumnIfMissing(con, "currency", "archived",
                        "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(con, "currency", "exchange", "REAL");
                addColumnIfMissing(con, "currency", "database", "REAL");
                addColumnIfMissing(con, "currency", "differenz", "REAL");
                addColumnIfMissing(con, "currency", "k4h", "REAL");
                addColumnIfMissing(con, "currency", "d4h", "REAL");
                addColumnIfMissing(con, "currency", "k2h", "REAL");
                addColumnIfMissing(con, "currency", "d2h", "REAL");
                addColumnIfMissing(con, "currency", "k12h", "REAL");
                addColumnIfMissing(con, "currency", "d12h", "REAL");
                addColumnIfMissing(con, "currency", "k1d", "REAL");
                addColumnIfMissing(con, "currency", "d1d", "REAL");
                addColumnIfMissing(con, "currency", "k3d", "REAL");
                addColumnIfMissing(con, "currency", "d3d", "REAL");
                addColumnIfMissing(con, "currency", "k1w", "REAL");
                addColumnIfMissing(con, "currency", "d1w", "REAL");
                addColumnIfMissing(con, "currency", "k1m", "REAL");
                addColumnIfMissing(con, "currency", "d1m", "REAL");
                addColumnIfMissing(con, "currency", "k5m", "REAL");
                addColumnIfMissing(con, "currency", "d5m", "REAL");
                addColumnIfMissing(con, "currency", "CCI", "REAL");
                addColumnIfMissing(con, "currency", "ATR5m", "REAL");
                addColumnIfMissing(con, "currency", "RSI4h", "REAL");
                addColumnIfMissing(con, "currency", "volume24h", "REAL");
                addColumnIfMissing(con, "currency", "emafast", "REAL");
                addColumnIfMissing(con, "currency", "emaslow", "REAL");
                addColumnIfMissing(con, "currency", "updateTime", "TEXT");

                initializeGridSettings(con);

                requireTradeSettingsForActiveCurrencies(con);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    static void initializeSettingsDatabase(String jdbcUrl) throws SQLException {
        try (Connection con = open(jdbcUrl); Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS SETTING ("
                    + "Balance REAL DEFAULT 0, minBuyAmount REAL DEFAULT 5.5, "
                    + "maxBuyAmount REAL DEFAULT 500, percent_toAdd REAL DEFAULT 0, "
                    + "ROI_percent REAL DEFAULT 0, PercentToSell REAL DEFAULT 0, "
                    + "BaseStopLoss REAL DEFAULT -2, countPart INTEGER DEFAULT 0, "
                    + "Börse REAL DEFAULT 0, Datenbank REAL DEFAULT 0, Differenz REAL DEFAULT 0, "
                    + "BuyAmount REAL DEFAULT 5.5, Count INTEGER DEFAULT 0, y_Factor REAL DEFAULT 0, "
                    + "TP REAL DEFAULT 0, DesiredAmount REAL DEFAULT 0, "
                    + "PnL_reverense REAL DEFAULT 0, BUYING TEXT DEFAULT '0')");
            boolean empty;
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM SETTING")) {
                empty = rs.next() && rs.getInt(1) == 0;
            }
            if (empty) {
                statement.executeUpdate("INSERT INTO SETTING DEFAULT VALUES");
            }
        }
    }

    static void initializeWpdDatabase(String jdbcUrl) throws SQLException {
        try (Connection con = open(jdbcUrl); Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS WPD (Date TEXT PRIMARY KEY)");
        }
    }

    private static Connection open(String jdbcUrl) throws SQLException {
        Connection con = DriverManager.getConnection(jdbcUrl);
        try {
            try (Statement statement = con.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return con;
        } catch (SQLException e) {
            try {
                con.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    /**
     * Legt bei einer Neuinstallation die bisher vorausgesetzten Kerntabellen an.
     * CREATE TABLE IF NOT EXISTS verändert vorhandene Installationen nicht.
     */
    private static void createCoreTradingTables(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS currency ("
                    + "currency TEXT PRIMARY KEY, buyStatus TEXT DEFAULT 'false', "
                    + "sellStatus TEXT DEFAULT 'true', buyAmount REAL DEFAULT 10.0, "
                    + "allTimeHigh REAL DEFAULT 0.000000001, grid INTEGER DEFAULT 23)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS positions ("
                    + "currency TEXT NOT NULL, BuyOrderId TEXT PRIMARY KEY, "
                    + "OrderPrice REAL, quantity REAL, BuyAmount REAL, BuyPrice REAL, "
                    + "side TEXT, Profit REAL, SL REAL, BuyDate TEXT, BuyTime TEXT, "
                    + "Status INTEGER, statusCode TEXT, positionId TEXT, limitprice REAL, "
                    + "OrigPrice REAL, realisierterPNL REAL, unrealisierterPNL REAL)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS historyPosition ("
                    + "currency TEXT, BuyOrderId TEXT PRIMARY KEY, Quantity REAL, "
                    + "BuyAmount REAL, OrigPrice REAL, BuyPrice REAL, side TEXT, "
                    + "Profit REAL, strategie TEXT, tense TEXT, SellPrice REAL, "
                    + "SellOrderId TEXT, BuyDate TEXT, BuyTime TEXT, buyunixtime INTEGER, "
                    + "SellDate TEXT, SellAmount REAL, SellTime TEXT, Fee REAL, Tax REAL, "
                    + "Gewinn REAL, GewinnAfterTax REAL, LossAfterTax REAL, Status INTEGER, "
                    + "BuyFee REAL, SellFee REAL, statusCode TEXT, Exittype TEXT)");
        }
    }

    private static void requireTable(Connection con, String table) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Erforderliche Tabelle fehlt: " + table);
                }
            }
        }
    }

    private static void createTradeSettings(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS tradeSettings ("
                    + "currency TEXT PRIMARY KEY, TP REAL, SL REAL, TSL TEXT, "
                    + "TSL_activate REAL, TSL_decline REAL)");
        }
    }

    private static void createTradingRules(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS tradingRules ("
                    + "currency TEXT PRIMARY KEY, tickSize TEXT, stepSize TEXT, minQty TEXT, "
                    + "amountIncrement TEXT, maxOrderSize TEXT, minOrderAmount TEXT, maxOrderAmount TEXT)");
        }
        addColumnIfMissing(con, "tradingRules", "amountIncrement", "TEXT");
        addColumnIfMissing(con, "tradingRules", "maxOrderSize", "TEXT");
        addColumnIfMissing(con, "tradingRules", "minOrderAmount", "TEXT");
        addColumnIfMissing(con, "tradingRules", "maxOrderAmount", "TEXT");
    }

    private static void createPerformance(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS performance ("
                    + "currency TEXT, SellOrderId TEXT UNIQUE, Profit REAL, TotalBuffer REAL, "
                    + "SellDate TEXT, SellTime TEXT, count_Position INTEGER, SellAmount REAL)");
        }
    }

    private static void createStrategyState(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS strategyState ("
                    + "currency TEXT PRIMARY KEY, buyBlockedUntil INTEGER NOT NULL DEFAULT 0, "
                    + "reason TEXT, updatedAt INTEGER NOT NULL)");
        }
    }

    private static void requireTradeSettingsForActiveCurrencies(Connection con) throws SQLException {
        String sql = "SELECT c.currency FROM currency c "
                + "LEFT JOIN tradeSettings t ON t.currency = c.currency "
                + "WHERE (c.buyStatus = 'true' OR c.buyStatus = '1') AND t.currency IS NULL "
                + "ORDER BY c.currency";
        StringBuilder missing = new StringBuilder();
        try (Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(rs.getString("currency"));
            }
        }
        if (missing.length() > 0) {
            throw new SQLException("tradeSettings fehlen für aktive Währungen: " + missing);
        }
    }

    /** Überführt den bisherigen Grid-Divisor verlustfrei in einen Prozentabstand. */
    private static void initializeGridSettings(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("UPDATE currency SET gridMode = 'GEOMETRIC' "
                    + "WHERE gridMode IS NULL OR gridMode NOT IN ('ARITHMETIC', 'GEOMETRIC')");
            statement.executeUpdate("UPDATE currency SET gridSpacing = "
                    + "CASE WHEN grid > 0 THEN 1.0 / grid ELSE 1.0 END "
                    + "WHERE gridSpacing IS NULL OR gridSpacing <= 0");
            statement.executeUpdate("UPDATE currency SET archived = 0 WHERE archived IS NULL");
        }
    }

    private static void addColumnIfMissing(Connection con, String table, String column,
            String definition) throws SQLException {
        if (columnExists(con, table, column)) return;
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + quote(table) + " ADD COLUMN "
                    + quote(column) + " " + definition);
        }
    }

    private static boolean columnExists(Connection con, String table, String column)
            throws SQLException {
        try (Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
            return false;
        }
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static void rollback(Connection con, Exception original) {
        try {
            con.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
