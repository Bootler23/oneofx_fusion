package com.oneofx.fusion.tradingbot.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.oneofx.fusion.tradingbot.bot.BotRuntime;

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
                createOperationalTables(con);
                createBotTables(con);
                createBaseConfigTables(con);
                createPaperTradingTables(con);

                addColumnIfMissing(con, "historyPosition", "Split", "REAL");
                addColumnIfMissing(con, "historyPosition", "Balance_atBuy", "REAL");
                addColumnIfMissing(con, "historyPosition", "Asset_atBuy", "REAL");
                addColumnIfMissing(con, "historyPosition", "BalanceToAsset_atBuy", "REAL");
                addColumnIfMissing(con, "historyPosition", "POS_count", "INTEGER");
                addColumnIfMissing(con, "historyPosition", "X", "REAL");

                // Persistenter Zustand des Trailing Stops je offener Position.
                addColumnIfMissing(con, "positions", "TSL", "TEXT DEFAULT 'inactive'");
                addColumnIfMissing(con, "positions", "peakPrice", "REAL");

                addColumnIfMissing(con, "positions", "bot_id", "INTEGER");
                addColumnIfMissing(con, "historyPosition", "bot_id", "INTEGER");
                addColumnIfMissing(con, "performance", "bot_id", "INTEGER");
                addColumnIfMissing(con, "buy_attempts", "bot_id", "INTEGER");
                addColumnIfMissing(con, "sell_attempts", "bot_id", "INTEGER");
                addColumnIfMissing(con, "activityLog", "bot_id", "INTEGER");
                addColumnIfMissing(con, "bots", "paper_fee_percent",
                        "REAL NOT NULL DEFAULT 0.25");
                addColumnIfMissing(con, "bots", "paper_slippage_percent",
                        "REAL NOT NULL DEFAULT 0.10");
                addColumnIfMissing(con, "paperPositions", "updated_at",
                        "TEXT");
                addColumnIfMissing(con, "botPairSettings", "config_pool_id", "INTEGER");

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
                initializeBotData(con);

                requireTradeSettingsForActiveCurrencies(con);
                con.commit();
                loadSelectedBot(con);
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

    private static void createOperationalTables(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS buy_attempts ("
                    + "attempt_id TEXT PRIMARY KEY, currency_pair TEXT NOT NULL, "
                    + "quantity TEXT NOT NULL, stop_price TEXT NOT NULL, "
                    + "limit_price TEXT NOT NULL, exchange_order_id TEXT, "
                    + "state TEXT NOT NULL, error_message TEXT, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS sell_attempts ("
                    + "attempt_id TEXT PRIMARY KEY, buy_order_id TEXT NOT NULL, "
                    + "currency_pair TEXT NOT NULL, quantity TEXT NOT NULL, "
                    + "previous_position_status INTEGER NOT NULL, exchange_order_id TEXT, "
                    + "state TEXT NOT NULL, error_message TEXT, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS activityLog ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "severity TEXT NOT NULL, category TEXT NOT NULL, "
                    + "currency TEXT, message TEXT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_activity_created "
                    + "ON activityLog(created_at DESC)");
        }
    }

    private static void createBotTables(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bots ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL COLLATE NOCASE UNIQUE, "
                    + "enabled INTEGER NOT NULL DEFAULT 0, "
                    + "archived INTEGER NOT NULL DEFAULT 0, "
                    + "mode TEXT NOT NULL DEFAULT 'LIVE' CHECK(mode IN ('LIVE', 'PAPER')), "
                    + "strategy TEXT NOT NULL DEFAULT 'GRID', "
                    + "budget REAL NOT NULL DEFAULT 5000, "
                    + "max_exposure REAL NOT NULL DEFAULT 5000, "
                    + "max_open_positions INTEGER NOT NULL DEFAULT 20, "
                    + "max_open_orders INTEGER NOT NULL DEFAULT 4, "
                    + "paper_fee_percent REAL NOT NULL DEFAULT 0.25, "
                    + "paper_slippage_percent REAL NOT NULL DEFAULT 0.10, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS botPairSettings ("
                    + "bot_id INTEGER NOT NULL, currency TEXT NOT NULL, "
                    + "buyStatus TEXT NOT NULL DEFAULT 'false', buyAmount REAL NOT NULL DEFAULT 10, "
                    + "maxBuyAmount REAL NOT NULL DEFAULT 500, "
                    + "gridMode TEXT NOT NULL DEFAULT 'GEOMETRIC', gridSpacing REAL NOT NULL DEFAULT 1, "
                    + "SL REAL NOT NULL DEFAULT 2, TSL TEXT NOT NULL DEFAULT 'true', "
                    + "TSL_activate REAL NOT NULL DEFAULT 2.5, TSL_decline REAL NOT NULL DEFAULT 0.8, "
                    + "archived INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (bot_id, currency), "
                    + "FOREIGN KEY (bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS runtimeState ("
                    + "id INTEGER PRIMARY KEY CHECK(id = 1), selected_bot_id INTEGER NOT NULL, "
                    + "FOREIGN KEY (selected_bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS botStrategyState ("
                    + "bot_id INTEGER NOT NULL, currency TEXT NOT NULL, "
                    + "buyBlockedUntil INTEGER NOT NULL DEFAULT 0, reason TEXT, updatedAt INTEGER NOT NULL, "
                    + "PRIMARY KEY (bot_id, currency), "
                    + "FOREIGN KEY (bot_id) REFERENCES bots(id))");
        }
    }

    private static void createPaperTradingTables(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS paperAccounts ("
                    + "bot_id INTEGER NOT NULL, asset TEXT NOT NULL, available REAL NOT NULL DEFAULT 0, "
                    + "reserved REAL NOT NULL DEFAULT 0, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (bot_id, asset), FOREIGN KEY (bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS paperOrders ("
                    + "order_id TEXT PRIMARY KEY, bot_id INTEGER NOT NULL, currency TEXT NOT NULL, "
                    + "side TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, "
                    + "limit_price REAL, quantity REAL NOT NULL, amount REAL NOT NULL, "
                    + "filled_price REAL, fee REAL NOT NULL DEFAULT 0, reason TEXT, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS paperPositions ("
                    + "position_id TEXT PRIMARY KEY, bot_id INTEGER NOT NULL, currency TEXT NOT NULL, "
                    + "entry_price REAL NOT NULL, quantity REAL NOT NULL, buy_amount REAL NOT NULL, "
                    + "peak_price REAL NOT NULL, tsl_active INTEGER NOT NULL DEFAULT 0, "
                    + "unrealized_pnl REAL NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'OPEN', "
                    + "opened_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, closed_at TEXT, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "exit_price REAL, realized_pnl REAL, fees REAL NOT NULL DEFAULT 0, exit_reason TEXT, "
                    + "FOREIGN KEY (bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_orders_bot_status "
                    + "ON paperOrders(bot_id, status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_paper_positions_bot_status "
                    + "ON paperPositions(bot_id, status)");
        }
    }

    private static void createBaseConfigTables(Connection con) throws SQLException {
        String fields = "buy_order_type TEXT NOT NULL DEFAULT 'LIMIT', "
                + "sell_order_type TEXT NOT NULL DEFAULT 'MARKET', "
                + "max_buy_order_minutes INTEGER NOT NULL DEFAULT 0, "
                + "max_sell_order_minutes INTEGER NOT NULL DEFAULT 0, "
                + "cooldown_minutes INTEGER NOT NULL DEFAULT 60, "
                + "take_profit_percent REAL NOT NULL DEFAULT 3, "
                + "trailing_stop_buy_enabled INTEGER NOT NULL DEFAULT 0, "
                + "trailing_stop_buy_activation REAL NOT NULL DEFAULT 1, "
                + "trailing_stop_buy_rebound REAL NOT NULL DEFAULT 0.3, "
                + "only_sell_with_profit INTEGER NOT NULL DEFAULT 0, "
                + "close_after_minutes INTEGER NOT NULL DEFAULT 0, "
                + "dca_enabled INTEGER NOT NULL DEFAULT 0, "
                + "dca_max_orders INTEGER NOT NULL DEFAULT 3, "
                + "dca_trigger_percent REAL NOT NULL DEFAULT 2, "
                + "dca_size_multiplier REAL NOT NULL DEFAULT 1";
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS botBaseConfig ("
                    + "bot_id INTEGER PRIMARY KEY, " + fields + ", "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY(bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS configPools ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, bot_id INTEGER NOT NULL, "
                    + "name TEXT NOT NULL COLLATE NOCASE, " + fields + ", "
                    + "archived INTEGER NOT NULL DEFAULT 0, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE(bot_id,name), FOREIGN KEY(bot_id) REFERENCES bots(id))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_config_pools_bot "
                    + "ON configPools(bot_id,archived)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS trailingBuyState ("
                    + "bot_id INTEGER NOT NULL, currency TEXT NOT NULL, high_price REAL NOT NULL, "
                    + "low_price REAL NOT NULL, active INTEGER NOT NULL DEFAULT 0, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY(bot_id,currency), FOREIGN KEY(bot_id) REFERENCES bots(id))");
            statement.executeUpdate("INSERT OR IGNORE INTO botBaseConfig (bot_id) "
                    + "SELECT id FROM bots");
        }
    }

    /** Ordnet alle Daten einer bestehenden Installation verlustfrei dem Standard-Bot zu. */
    private static void initializeBotData(Connection con) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("INSERT OR IGNORE INTO bots "
                    + "(id, name, enabled, mode, strategy, budget, max_exposure, "
                    + "max_open_positions, max_open_orders) "
                    + "SELECT 1, 'Standard-Bot', CASE WHEN EXISTS (SELECT 1 FROM currency "
                    + "WHERE buyStatus IN ('true', '1') AND COALESCE(archived, 0) = 0) "
                    + "THEN 1 ELSE 0 END, 'LIVE', 'GRID', 5000, 5000, 20, 4");
            statement.executeUpdate("INSERT OR IGNORE INTO runtimeState (id, selected_bot_id) "
                    + "VALUES (1, 1)");
            statement.executeUpdate("UPDATE runtimeState SET selected_bot_id = 1 "
                    + "WHERE NOT EXISTS (SELECT 1 FROM bots b "
                    + "WHERE b.id = runtimeState.selected_bot_id AND b.archived = 0)");

            statement.executeUpdate("INSERT OR IGNORE INTO botPairSettings "
                    + "(bot_id, currency, buyStatus, buyAmount, maxBuyAmount, gridMode, gridSpacing, "
                    + "SL, TSL, TSL_activate, TSL_decline, archived) "
                    + "SELECT 1, c.currency, COALESCE(c.buyStatus, 'false'), "
                    + "COALESCE(c.buyAmount, 10), COALESCE(c.maxBuyAmount, 500), "
                    + "COALESCE(c.gridMode, 'GEOMETRIC'), COALESCE(c.gridSpacing, 1), "
                    + "COALESCE(t.SL, 2), COALESCE(t.TSL, 'true'), "
                    + "COALESCE(t.TSL_activate, 2.5), COALESCE(t.TSL_decline, 0.8), "
                    + "COALESCE(c.archived, 0) FROM currency c "
                    + "LEFT JOIN tradeSettings t ON t.currency = c.currency");
            statement.executeUpdate("INSERT OR IGNORE INTO botStrategyState "
                    + "(bot_id, currency, buyBlockedUntil, reason, updatedAt) "
                    + "SELECT 1, currency, buyBlockedUntil, reason, updatedAt FROM strategyState");

            for (String table : new String[] {"positions", "historyPosition", "performance",
                    "buy_attempts", "sell_attempts", "activityLog"}) {
                statement.executeUpdate("UPDATE " + quote(table) + " SET bot_id = 1 "
                        + "WHERE bot_id IS NULL");
            }

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_positions_bot_status "
                    + "ON positions(bot_id, Status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_bot_status "
                    + "ON historyPosition(bot_id, Status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_buy_attempts_bot_state "
                    + "ON buy_attempts(bot_id, state)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sell_attempts_bot_state "
                    + "ON sell_attempts(bot_id, state)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_activity_bot_created "
                    + "ON activityLog(bot_id, created_at DESC)");
            statement.executeUpdate("DROP INDEX IF EXISTS uq_active_buy_attempt");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_buy_attempt_bot "
                    + "ON buy_attempts(bot_id, currency_pair, stop_price) "
                    + "WHERE state IN ('SUBMITTING', 'RECONCILIATION_REQUIRED')");
        }
        createBotAssignmentTriggers(con);
    }

    /** Fängt auch alte Schreibpfade ab, die bot_id noch nicht explizit mitsenden. */
    private static void createBotAssignmentTriggers(Connection con) throws SQLException {
        String[][] tables = {
                {"positions", "BuyOrderId"}, {"historyPosition", "BuyOrderId"},
                {"performance", "rowid"}, {"buy_attempts", "attempt_id"},
                {"sell_attempts", "attempt_id"}, {"activityLog", "id"}
        };
        try (Statement statement = con.createStatement()) {
            for (String[] entry : tables) {
                String table = entry[0];
                String key = entry[1];
                String reference = "rowid".equals(key) ? "rowid = NEW.rowid"
                        : quote(key) + " = NEW." + quote(key);
                statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS trg_" + table
                        + "_assign_bot AFTER INSERT ON " + quote(table) + " "
                        + "WHEN NEW.bot_id IS NULL BEGIN UPDATE " + quote(table)
                        + " SET bot_id = (SELECT selected_bot_id FROM runtimeState WHERE id = 1) "
                        + "WHERE " + reference + "; END");
            }
        }
    }

    private static void loadSelectedBot(Connection con) throws SQLException {
        try (Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT selected_bot_id FROM runtimeState WHERE id = 1")) {
            if (rs.next()) BotRuntime.select(rs.getLong(1));
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
