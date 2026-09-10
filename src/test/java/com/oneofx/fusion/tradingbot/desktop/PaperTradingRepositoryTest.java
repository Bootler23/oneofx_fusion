package com.oneofx.fusion.tradingbot.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;

public class PaperTradingRepositoryTest {
    private static final String[] PROPERTIES = {
            "ONEOFX_TRADING_DB", "ONEOFX_SETTINGS_DB", "ONEOFX_WPD_DB"
    };
    private final String[] previous = new String[PROPERTIES.length];
    private File database;
    private PaperTradingRepository repository;

    @Before
    public void setUp() throws Exception {
        database = File.createTempFile("oneofx-paper-", ".db");
        for (int i = 0; i < PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
            System.setProperty(PROPERTIES[i], database.getAbsolutePath());
        }
        DatabaseSchema.initialize();
        repository = new PaperTradingRepository();
        repository.ensureAccount(1, 1_000);
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
    public void buyFillAndSellUpdateVirtualBalancesAndPnl() throws Exception {
        assertTrue(repository.placeLimitBuy(1, "BTCEUR", 100, 1, 0.25));
        assertFalse(repository.placeLimitBuy(1, "BTCEUR", 100, 1, 0.25));
        assertEquals(899.75, balance("EUR").available(), 0.000001);
        assertEquals(100.25, balance("EUR").reserved(), 0.000001);

        assertEquals(1, repository.fillTriggeredBuys(1, "BTCEUR", 99));
        assertEquals(1, repository.loadOpenPositions(1, "BTCEUR").size());
        assertEquals(1.0, balance("BTC").available(), 0.000001);

        PaperTradingRepository.PaperPosition position =
                repository.loadOpenPositions(1, "BTCEUR").get(0);
        assertTrue(repository.closePosition(1, position, 110, 0.10, 0.25, "TEST"));
        assertEquals(0, repository.loadOpenPositions(1, "BTCEUR").size());
        assertEquals(1009.365275, balance("EUR").available(), 0.000001);
        assertEquals(0.0, balance("BTC").available(), 0.000001);
    }

    @Test
    public void cancellingOrderReleasesReservedCapital() throws Exception {
        assertTrue(repository.placeLimitBuy(1, "ETHEUR", 50, 2, 0.25));
        assertEquals(1, repository.cancelOpenBuys(1, "ETHEUR", "TEST"));
        assertEquals(1_000.0, balance("EUR").available(), 0.000001);
        assertEquals(0.0, balance("EUR").reserved(), 0.000001);
    }

    private PaperTradingRepository.Balance balance(String asset) throws Exception {
        return repository.loadBalances(1).stream()
                .filter(value -> asset.equals(value.asset())).findFirst().orElseThrow();
    }
}
