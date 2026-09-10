package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;

public class OperationsRepositoryTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private OperationsRepository repository;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-cockpit-", ".db");
        for (int i = 0; i < PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i], database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        repository = new OperationsRepository();
    }

    @After
    public void tearDown() {
        for (int i = 0; i < PROPERTIES.length; i++) {
            if (previous[i] == null) System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i], previous[i]);
        }
        if (database != null) database.delete();
    }

    @Test
    public void loadsOrdersPositionsAttemptsWarningsAndActivity() throws Exception {
        execute("INSERT INTO currency (currency, buyStatus, buyAmount, allTimeHigh, grid, "
                + "gridMode, gridSpacing, archived) VALUES "
                + "('BTCEUR', 'true', 25, 100, 23, 'GEOMETRIC', 1, 0)");
        execute("INSERT INTO positions (currency, BuyOrderId, OrderPrice, quantity, "
                + "BuyAmount, BuyPrice, Status, statusCode) VALUES "
                + "('BTCEUR', 'pending', 99, 0.25, 25, 0, 0, 'NEW'), "
                + "('BTCEUR', 'filled', 98, 0.25, 25, 98, 1, 'FILLED')");
        execute("INSERT INTO buy_attempts (attempt_id, currency_pair, quantity, stop_price, "
                + "limit_price, state, error_message) VALUES "
                + "('attempt-1', 'BTCEUR', '0.25', '99', '99', "
                + "'RECONCILIATION_REQUIRED', 'Netzwerkfehler')");
        execute("INSERT INTO historyPosition (currency, BuyOrderId, BuyPrice, SellPrice, "
                + "buyunixtime, SellDate, SellTime, Status, bot_id) VALUES "
                + "('BTCEUR', 'closed-live', 95, 105, 1700000000, "
                + "'2023-11-15', '00:13:20', 2, 1)");
        execute("INSERT INTO paperPositions (position_id, bot_id, currency, entry_price, "
                + "quantity, buy_amount, peak_price, status, opened_at, closed_at, exit_price) "
                + "VALUES ('closed-paper', 1, 'BTCEUR', 96, 0.2, 19.2, 106, 'CLOSED', "
                + "'2023-11-14 22:13:20', '2023-11-15 01:13:20', 106)");
        repository.recordEvent("INFO", "TEST", "BTCEUR", "Cockpit-Ereignis");

        assertEquals(1, repository.loadOpenOrders().size());
        assertEquals(OperationsRepository.OrderOrigin.LIVE,
                repository.loadOpenOrders().get(0).origin());
        assertEquals("pending",repository.loadOpenOrders().get(0).referenceId());
        assertEquals(1, repository.loadPositions().size());
        assertEquals(1, repository.loadUnresolvedAttempts().size());
        assertEquals(4,repository.loadTradeMarkers(1,"BTC-EUR",20).size());
        assertFalse(repository.loadWarnings().isEmpty());
        assertEquals("Cockpit-Ereignis", repository.loadActivity(20).stream()
                .filter(row -> "TEST".equals(row.category())).findFirst().orElseThrow().message());
    }

    private void execute(String sql) throws Exception {
        try (Connection con = DriverManager.getConnection(
                "jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = con.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
