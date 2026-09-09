package com.oneofx.fusion.tradingbot.SQL_Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence.Attempt;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.domain.Position;

public class BuyOrderPersistenceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String jdbcUrl;
    private BuyOrderPersistence persistence;

    @Before
    public void createDatabase() throws Exception {
        File database = temporaryFolder.newFile("trading.db");
        jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
        persistence = new BuyOrderPersistence(jdbcUrl);
        executeUpdate("CREATE TABLE positions (currency TEXT, BuyOrderId TEXT PRIMARY KEY, "
                + "OrderPrice REAL, Status INTEGER, statusCode TEXT)");
    }

    @Test
    public void startIsDurableBeforeExchangeSubmission() throws Exception {
        Attempt attempt = persistence.start("BTC-EUR", "0.01", "100", "101");

        assertEquals("SUBMITTING", selectString(
                "SELECT state FROM buy_attempts WHERE attempt_id = '" + attempt.attemptId() + "'"));
        assertThrows(SQLException.class,
                () -> persistence.start("BTC-EUR", "0.01", "100", "101"));
    }

    @Test
    public void acceptedOrderAndPositionAreStoredTogether() throws Exception {
        Attempt attempt = persistence.start("BTC-EUR", "0.01", "100", "101");
        Position position = new Position.Builder("BTC-EUR", "buy-1")
                .orderPrice(100.0)
                .status(0)
                .statusCode(TradingConstants.STATUS_NEW)
                .build();

        persistence.recordSubmitted(attempt, "buy-1", position);

        assertEquals(1, selectInt("SELECT COUNT(*) FROM positions WHERE BuyOrderId = 'buy-1'"));
        assertEquals("SUBMITTED", selectString(
                "SELECT state FROM buy_attempts WHERE attempt_id = '" + attempt.attemptId() + "'"));
        assertEquals("buy-1", selectString(
                "SELECT exchange_order_id FROM buy_attempts WHERE attempt_id = '" + attempt.attemptId() + "'"));
    }

    @Test
    public void localInsertFailureLeavesAttemptForReconciliation() throws Exception {
        Attempt attempt = persistence.start("BTC-EUR", "0.01", "100", "101");
        executeUpdate("CREATE TRIGGER reject_position BEFORE INSERT ON positions "
                + "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        Position position = new Position.Builder("BTC-EUR", "buy-2")
                .orderPrice(100.0)
                .status(0)
                .statusCode(TradingConstants.STATUS_NEW)
                .build();

        assertThrows(SQLException.class,
                () -> persistence.recordSubmitted(attempt, "buy-2", position));

        assertEquals(0, selectInt("SELECT COUNT(*) FROM positions"));
        assertEquals("SUBMITTING", selectString(
                "SELECT state FROM buy_attempts WHERE attempt_id = '" + attempt.attemptId() + "'"));
    }

    @Test
    public void ambiguousOutcomeIsMarkedForReconciliation() throws Exception {
        Attempt attempt = persistence.start("BTC-EUR", "0.01", "100", "101");

        persistence.requireReconciliation(attempt, "buy-3", "timeout");

        assertEquals("RECONCILIATION_REQUIRED", selectString(
                "SELECT state FROM buy_attempts WHERE attempt_id = '" + attempt.attemptId() + "'"));
        assertEquals("buy-3", selectString(
                "SELECT exchange_order_id FROM buy_attempts WHERE attempt_id = '" + attempt.attemptId() + "'"));
    }

    private int selectInt(String sql) throws SQLException {
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private String selectString(String sql) throws SQLException {
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private void executeUpdate(String sql) throws SQLException {
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
