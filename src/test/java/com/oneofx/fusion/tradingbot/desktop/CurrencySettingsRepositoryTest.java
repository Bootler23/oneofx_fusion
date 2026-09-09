package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.grid.GridMode;

public class CurrencySettingsRepositoryTest {

    private static final String[] DATABASE_PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };

    private final String[] previousValues = new String[DATABASE_PROPERTIES.length];
    private File database;
    private CurrencySettingsRepository repository;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-desktop-", ".db");
        for (int i = 0; i < DATABASE_PROPERTIES.length; i++) {
            previousValues[i] = System.getProperty(DATABASE_PROPERTIES[i]);
            System.setProperty(DATABASE_PROPERTIES[i], database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        repository = new CurrencySettingsRepository();
    }

    @After
    public void tearDown() {
        for (int i = 0; i < DATABASE_PROPERTIES.length; i++) {
            if (previousValues[i] == null) {
                System.clearProperty(DATABASE_PROPERTIES[i]);
            } else {
                System.setProperty(DATABASE_PROPERTIES[i], previousValues[i]);
            }
        }
        if (database != null) database.delete();
    }

    @Test
    public void erstelltUndSpeichertHandelspaarInSqlite() throws Exception {
        repository.add(CurrencySettings.defaults("BTC-EUR"));

        CurrencySettings created = repository.load("BTCEUR");
        assertEquals("BTCEUR", created.currency());
        assertFalse(created.buyEnabled());
        assertEquals(500.0, created.maxBuyAmount(), 0.000001);

        CurrencySettings changed = new CurrencySettings(
                "BTCEUR", true, 25.0, 750.0, GridMode.ARITHMETIC, 40.0,
                3.5, true, 4.0, 1.2);
        repository.save(changed);

        CurrencySettings loaded = repository.load("BTCEUR");
        assertEquals(changed, loaded);
        assertTrue(repository.loadDashboardStats().enabledCurrencies() == 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void lehntKapitalgrenzeUnterKaufbetragAb() {
        new CurrencySettings("BTCEUR", true, 100.0, 50.0,
                GridMode.GEOMETRIC, 1.0,
                2.0, true, 2.5, 0.8);
    }

    @Test
    public void loeschtUnbenutztesHandelspaarVollstaendig() throws Exception {
        repository.add(CurrencySettings.defaults("ETHEUR"));

        CurrencySettingsRepository.RemovalResult result = repository.remove("ETH-EUR");

        assertFalse(result.archived());
        assertTrue(repository.loadAll().isEmpty());
    }

    @Test
    public void archiviertHandelspaarMitPositionsdaten() throws Exception {
        repository.add(CurrencySettings.defaults("BTCEUR"));
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO positions (currency, BuyOrderId, Status) VALUES (?, ?, ?)")) {
            ps.setString(1, "BTCEUR");
            ps.setString(2, "existing-order");
            ps.setInt(3, 1);
            ps.executeUpdate();
        }

        CurrencySettingsRepository.RemovalResult result = repository.remove("BTCEUR");

        assertTrue(result.archived());
        assertEquals(1, result.positions());
        assertTrue(repository.loadAll().isEmpty());
    }
}
