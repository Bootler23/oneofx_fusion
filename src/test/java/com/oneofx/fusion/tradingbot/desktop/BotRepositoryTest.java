package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.bot.BotRuntime;
import com.oneofx.fusion.tradingbot.grid.GridMode;

public class BotRepositoryTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private BotRepository bots;
    private CurrencySettingsRepository currencies;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-bots-", ".db");
        for (int i = 0; i < PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i], database.getAbsolutePath());
        }
        BotRuntime.select(BotRuntime.DEFAULT_BOT_ID);
        DatabaseSchema.initialize();
        bots = new BotRepository();
        currencies = new CurrencySettingsRepository();
    }

    @After
    public void tearDown() {
        BotRuntime.select(BotRuntime.DEFAULT_BOT_ID);
        for (int i = 0; i < PROPERTIES.length; i++) {
            if (previous[i] == null) System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i], previous[i]);
        }
        if (database != null) database.delete();
    }

    @Test
    public void createsDefaultBotAndKeepsPairSettingsIsolated() throws Exception {
        BotProfile standard = bots.loadSelected();
        assertEquals("Standard-Bot", standard.name());

        currencies.add(new CurrencySettings("BTCEUR", true, 10, 100,
                GridMode.GEOMETRIC, 1, 2, true, 2.5, 0.8));
        BotProfile second = bots.create("Swing Grid");
        bots.select(second.id());
        currencies.add(new CurrencySettings("BTCEUR", false, 25, 250,
                GridMode.ARITHMETIC, 50, 3, false, 0, 0));

        assertEquals(25, currencies.load(second.id(), "BTCEUR").buyAmount(), 0.0001);
        assertEquals(10, currencies.load(standard.id(), "BTCEUR").buyAmount(), 0.0001);
        assertTrue(currencies.load(standard.id(), "BTCEUR").buyEnabled());
        assertFalse(currencies.load(second.id(), "BTCEUR").buyEnabled());
    }

    @Test
    public void assignsNewOperationalRowsToSelectedBotAndArchivesSafely() throws Exception {
        BotProfile second = bots.create("Zweiter Bot");
        bots.select(second.id());
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = con.createStatement()) {
            statement.executeUpdate("INSERT INTO positions (currency, BuyOrderId, Status, BuyAmount) "
                    + "VALUES ('BTCEUR', 'bot-2-order', 1, 25)");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT bot_id FROM positions WHERE BuyOrderId = 'bot-2-order'")) {
                assertTrue(rs.next());
                assertEquals(second.id(), rs.getLong(1));
            }
        }

        assertThrows(java.sql.SQLException.class, () -> bots.archive(second.id()));
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = con.createStatement()) {
            statement.executeUpdate("DELETE FROM positions WHERE BuyOrderId = 'bot-2-order'");
        }
        bots.archive(second.id());
        assertEquals(BotRuntime.DEFAULT_BOT_ID, bots.loadSelected().id());
        assertEquals(0, bots.loadRiskSnapshot(second.id()).exposure(), 0.0001);
    }
}
