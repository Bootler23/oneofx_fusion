package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;

public class OrderTypeAvailabilityTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private CurrencySettingsRepository currencies;
    private BaseConfigRepository configs;
    private OrderTypeAvailability availability;

    @Before public void setUp() throws Exception {
        database = File.createTempFile("oneofx-order-types-", ".db");
        for (int i=0; i<PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i], database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        currencies = new CurrencySettingsRepository();
        configs = new BaseConfigRepository();
        availability = new OrderTypeAvailability();
        currencies.add(CurrencySettings.defaults("BTCEUR"));
        currencies.add(CurrencySettings.defaults("ETHEUR"));
        rules("BTCEUR", "LIMIT");
        rules("ETHEUR", "LIMIT,MARKET");
    }

    @After public void tearDown() {
        for (int i=0; i<PROPERTIES.length; i++) {
            if (previous[i] == null) System.clearProperty(PROPERTIES[i]);
            else System.setProperty(PROPERTIES[i], previous[i]);
        }
        if (database != null) database.delete();
    }

    @Test public void zeigtPaarwerteUndBildetSichereSchnittmenge() throws Exception {
        assertEquals(Set.of(OrderType.LIMIT), availability.forPair("BTC-EUR").supported());
        OrderTypeAvailability.Availability common = availability.forConfiguration(1, null);
        assertEquals(Set.of(OrderType.LIMIT), common.supported());
        assertEquals(List.of("LIMIT"), OrderTypeAvailability.buyChoices(common));
        assertEquals(List.of("LIMIT"), OrderTypeAvailability.sellChoices(common));
        assertEquals("Limit", common.display());
    }

    @Test(expected = IllegalArgumentException.class)
    public void verhindertUnpassendeBaseconfigBeimSpeichern() throws Exception {
        configs.saveBase(1, BotBaseConfig.defaults(1));
    }

    @Test public void verhindertUnpassendenConfigPoolBeiDerPaarzuordnung() throws Exception {
        rules("BTCEUR", "LIMIT,MARKET");
        BotBaseConfig incompatible = new BotBaseConfig(1, "STOP_LIMIT", "MARKET",
                0, 0, 60, 3, false, 1, 0.3, false, 0, false, 3, 2, 1);
        ConfigPool pool = configs.createPool(1, "Stop", incompatible);
        boolean rejected = false;
        try {
            configs.assignPool(1, "BTCEUR", pool.id());
        } catch (IllegalArgumentException ex) {
            rejected = ex.getMessage().contains("Stop-Limit");
        }
        assertTrue(rejected);
    }

    @Test public void verstehtFusionSchreibweisenDerOrdertypen() {
        assertEquals(OrderType.STOP_LIMIT, OrderType.fromApiValue("STOP_LIMIT"));
        assertEquals(OrderType.STOP_MARKET, OrderType.fromApiValue("stop-market"));
        assertEquals(OrderType.LIMIT, OrderType.fromApiValue("Limit"));
    }

    private void rules(String currency, String types) throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO tradingRules(currency,tickSize,stepSize,minQty,supportedOrderTypes) "
                             + "VALUES(?,'0.01','0.001','0.001',?) ON CONFLICT(currency) "
                             + "DO UPDATE SET supportedOrderTypes=excluded.supportedOrderTypes")) {
            ps.setString(1, currency);
            ps.setString(2, types);
            ps.executeUpdate();
        }
    }
}
