package com.oneofx.fusion.tradingbot.Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatabaseSchemaTest {

    private File database;
    private String jdbcUrl;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-schema-", ".db");
        jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE currency (currency PRIMARY KEY, buyStatus, "
                    + "sellStatus, buyAmount, allTimehigh, grid)");
            statement.executeUpdate("INSERT INTO currency VALUES "
                    + "('BTCEUR', 'true', 'true', 60, 109000, 23)");
            statement.executeUpdate("CREATE TABLE positions (currency, buyOrderId PRIMARY KEY, "
                    + "orderPrice, quantity, buyAmount, buyPrice, side, Profit, SL, buyDate, "
                    + "buyTime, status, statusCode, positionId, limitprice, Origprice, "
                    + "realisierterPNL, unrealisierterPNL)");
            statement.executeUpdate("CREATE TABLE historyPosition (currency, "
                    + "BuyOrderId PRIMARY KEY, Quantity, BuyAmount, OrigPrice, BuyPrice, side, "
                    + "Profit, strategie, tense, SellPrice, SellOrderId, BuyDate, BuyTime, "
                    + "buyunixtime, SellDate, SellAmount, SellTime, Fee, Tax, Gewinn, "
                    + "GewinnAfterTax, LossAfterTax, Status, BuyFee, SellFee, statusCode, Exittype)");
            statement.executeUpdate("CREATE TABLE tradeSettings (currency PRIMARY KEY, TP, SL, "
                    + "TSL, TSL_activate, TSL_decline)");
            statement.executeUpdate("INSERT INTO tradeSettings VALUES "
                    + "('BTCEUR', 0.53, 2, 'true', 1.2, 0.4)");
            statement.executeUpdate("CREATE TABLE tradingrules "
                    + "(currency PRIMARY KEY, tickSize, stepSize, minQty)");
        }
    }

    @After
    public void tearDown() {
        if (database != null) database.delete();
    }

    @Test
    public void upgradesExistingUnifiedSchemaWithoutLosingData() throws Exception {
        DatabaseSchema.initializeTradingDatabase(jdbcUrl);
        DatabaseSchema.initializeSettingsDatabase(jdbcUrl);
        DatabaseSchema.initializeWpdDatabase(jdbcUrl);

        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement()) {
            assertTrue(columnExists(statement, "historyPosition", "Split"));
            assertTrue(columnExists(statement, "historyPosition", "Balance_atBuy"));
            assertTrue(columnExists(statement, "positions", "TSL"));
            assertTrue(columnExists(statement, "positions", "peakPrice"));
            assertTrue(columnExists(statement, "positions", "orderOrigin"));
            assertTrue(columnExists(statement, "tradingRules", "amountIncrement"));
            assertTrue(columnExists(statement, "currency", "gridMode"));
            assertTrue(columnExists(statement, "currency", "gridSpacing"));
            assertTrue(columnExists(statement, "currency", "archived"));
            assertTrue(tableExists(statement, "performance"));
            assertTrue(tableExists(statement, "strategyState"));
            assertTrue(tableExists(statement, "buy_attempts"));
            assertTrue(tableExists(statement, "sell_attempts"));
            assertTrue(tableExists(statement, "activityLog"));
            assertTrue(tableExists(statement, "bots"));
            assertTrue(tableExists(statement, "botPairSettings"));
            assertTrue(tableExists(statement, "runtimeState"));
            assertTrue(tableExists(statement, "botStrategyState"));
            assertTrue(tableExists(statement, "botBaseConfig"));
            assertTrue(tableExists(statement, "configPools"));
            assertTrue(tableExists(statement, "strategies"));
            assertTrue(tableExists(statement, "strategyNodes"));
            assertTrue(tableExists(statement, "botStrategyAssignments"));
            assertTrue(tableExists(statement, "marketStrategyAssignments"));
            assertTrue(tableExists(statement, "strategyEvaluationLog"));
            assertTrue(tableExists(statement, "backtestRuns"));
            assertTrue(tableExists(statement, "backtestTrades"));
            assertTrue(tableExists(statement, "backtestOrderEvents"));
            assertTrue(tableExists(statement, "backtestEquity"));
            assertTrue(tableExists(statement, "paperOrders"));
            assertTrue(tableExists(statement, "paperPositions"));
            assertTrue(columnExists(statement, "paperOrders", "position_id"));
            assertTrue(columnExists(statement, "botPairSettings", "config_pool_id"));
            assertTrue(columnExists(statement, "botPairSettings", "strategy_id"));
            assertTrue(columnExists(statement, "configPools", "strategy_id"));
            assertTrue(columnExists(statement, "positions", "bot_id"));
            assertTrue(columnExists(statement, "historyPosition", "bot_id"));
            assertTrue(tableExists(statement, "SETTING"));
            assertTrue(tableExists(statement, "WPD"));
            assertEquals(1, scalarInt(statement,
                    "SELECT COUNT(*) FROM currency WHERE currency = 'BTCEUR' AND buyAmount = 60"));
            assertEquals("GEOMETRIC", scalarString(statement,
                    "SELECT gridMode FROM currency WHERE currency = 'BTCEUR'"));
            assertEquals(1.0 / 23.0, scalarDouble(statement,
                    "SELECT gridSpacing FROM currency WHERE currency = 'BTCEUR'"), 0.000000001);
            assertEquals(1, scalarInt(statement, "SELECT COUNT(*) FROM SETTING"));
            assertEquals(1, scalarInt(statement,
                    "SELECT COUNT(*) FROM botPairSettings WHERE bot_id = 1 AND currency = 'BTCEUR'"));
        }
    }

    @Test(expected = SQLException.class)
    public void rejectsActiveCurrencyWithoutTradeSettings() throws Exception {
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement()) {
            statement.executeUpdate("DELETE FROM tradeSettings WHERE currency = 'BTCEUR'");
        }

        DatabaseSchema.initializeTradingDatabase(jdbcUrl);
    }

    private static boolean tableExists(Statement statement, String table) throws Exception {
        try (ResultSet rs = statement.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            return rs.next();
        }
    }

    private static boolean columnExists(Statement statement, String table, String column)
            throws Exception {
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
            return false;
        }
    }

    private static int scalarInt(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String scalarString(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static double scalarDouble(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : Double.NaN;
        }
    }
}
