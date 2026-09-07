package com.oneofx.fusion.tradingbot.SQL_Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence.Reservation;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;

public class SellOrderPersistenceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String jdbcUrl;
    private SellOrderPersistence persistence;

    @Before
    public void createDatabase() throws Exception {
        File database = temporaryFolder.newFile("trading.db");
        jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
        persistence = new SellOrderPersistence(jdbcUrl);

        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement()) {
            statement.executeUpdate("CREATE TABLE positions (BuyOrderId TEXT, Status INTEGER)");
            statement.executeUpdate("CREATE TABLE HIST (BuyOrderId TEXT, Status INTEGER, "
                    + "SellOrderId TEXT, SellTime TEXT, SellDate TEXT)");
        }
    }

    @Test
    public void reserveClaimsPositionAtMostOnce() throws Exception {
        insertPositionAndHistory("buy-1", 1, true);

        Reservation reservation = persistence.reserve("buy-1", "BTC-EUR", "0.01").orElseThrow();

        assertFalse(persistence.reserve("buy-1", "BTC-EUR", "0.01").isPresent());
        assertEquals(TradingConstants.POSITION_STATUS_SELL_SUBMITTING,
                selectInt("SELECT Status FROM positions WHERE BuyOrderId = 'buy-1'"));
        assertEquals("SUBMITTING", selectString(
                "SELECT state FROM sell_attempts WHERE attempt_id = '" + reservation.attemptId() + "'"));
        assertEquals(1, reservation.previousPositionStatus());
    }

    @Test
    public void recordSubmittedChangesHistAndPositionTogether() throws Exception {
        insertPositionAndHistory("buy-2", 7, true);
        Reservation reservation = persistence.reserve("buy-2", "ETH-EUR", "0.25").orElseThrow();

        persistence.recordSubmitted(reservation, "sell-2", "2026-09-07", "21:15:30");

        assertEquals(TradingConstants.POSITION_STATUS_SELL_PENDING,
                selectInt("SELECT Status FROM positions WHERE BuyOrderId = 'buy-2'"));
        assertEquals("sell-2", selectString(
                "SELECT SellOrderId FROM HIST WHERE BuyOrderId = 'buy-2'"));
        assertEquals(0, selectInt("SELECT Status FROM HIST WHERE BuyOrderId = 'buy-2'"));
        assertEquals("SUBMITTED", selectString(
                "SELECT state FROM sell_attempts WHERE attempt_id = '" + reservation.attemptId() + "'"));
    }

    @Test
    public void recordSubmittedRollsBackWhenHistoryRowIsMissing() throws Exception {
        insertPositionAndHistory("buy-3", 1, true);
        Reservation reservation = persistence.reserve("buy-3", "BTC-EUR", "0.02").orElseThrow();
        executeUpdate("DELETE FROM HIST WHERE BuyOrderId = 'buy-3'");

        assertThrows(SQLException.class, () -> persistence.recordSubmitted(
                reservation, "sell-3", "2026-09-07", "21:16:00"));

        assertEquals(TradingConstants.POSITION_STATUS_SELL_SUBMITTING,
                selectInt("SELECT Status FROM positions WHERE BuyOrderId = 'buy-3'"));
        assertEquals("SUBMITTING", selectString(
                "SELECT state FROM sell_attempts WHERE attempt_id = '" + reservation.attemptId() + "'"));
    }

    @Test
    public void definiteRejectionRestoresPreviousPositionStatus() throws Exception {
        insertPositionAndHistory("buy-4", 7, true);
        Reservation reservation = persistence.reserve("buy-4", "BTC-EUR", "0.03").orElseThrow();

        persistence.recordRejected(reservation, "HTTP 400");

        assertEquals(7, selectInt("SELECT Status FROM positions WHERE BuyOrderId = 'buy-4'"));
        assertEquals("REJECTED", selectString(
                "SELECT state FROM sell_attempts WHERE attempt_id = '" + reservation.attemptId() + "'"));
        assertTrue(persistence.reserve("buy-4", "BTC-EUR", "0.03").isPresent());
    }

    @Test
    public void ambiguousOutcomeKeepsPositionReserved() throws Exception {
        insertPositionAndHistory("buy-5", 1, true);
        Reservation reservation = persistence.reserve("buy-5", "BTC-EUR", "0.04").orElseThrow();

        persistence.requireReconciliation(reservation, "sell-5", "network timeout");

        assertEquals(TradingConstants.POSITION_STATUS_SELL_SUBMITTING,
                selectInt("SELECT Status FROM positions WHERE BuyOrderId = 'buy-5'"));
        assertEquals("RECONCILIATION_REQUIRED", selectString(
                "SELECT state FROM sell_attempts WHERE attempt_id = '" + reservation.attemptId() + "'"));
        assertFalse(persistence.reserve("buy-5", "BTC-EUR", "0.04").isPresent());
    }

    @Test
    public void existingSellOrderPreventsReservation() throws Exception {
        insertPositionAndHistory("buy-6", 1, true);
        executeUpdate("UPDATE HIST SET SellOrderId = 'existing-sell' WHERE BuyOrderId = 'buy-6'");

        assertFalse(persistence.reserve("buy-6", "BTC-EUR", "0.05").isPresent());
        assertEquals(1, selectInt("SELECT Status FROM positions WHERE BuyOrderId = 'buy-6'"));
    }

    private void insertPositionAndHistory(String buyOrderId, int status, boolean withHistory)
            throws SQLException {
        try (Connection con = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO positions (BuyOrderId, Status) VALUES (?, ?)")) {
                ps.setString(1, buyOrderId);
                ps.setInt(2, status);
                ps.executeUpdate();
            }
            if (withHistory) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO HIST (BuyOrderId) VALUES (?)")) {
                    ps.setString(1, buyOrderId);
                    ps.executeUpdate();
                }
            }
        }
    }

    private int selectInt(String sql) throws SQLException {
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private void executeUpdate(String sql) throws SQLException {
        try (Connection con = DriverManager.getConnection(jdbcUrl);
             Statement statement = con.createStatement()) {
            statement.executeUpdate(sql);
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
}
