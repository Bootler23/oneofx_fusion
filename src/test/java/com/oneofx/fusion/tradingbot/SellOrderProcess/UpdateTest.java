package com.oneofx.fusion.tradingbot.SellOrderProcess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Test;

import com.oneofx.fusion.tradingbot.constants.TradingConstants;

public class UpdateTest {

    @After
    public void clearTaxConfiguration() {
        System.clearProperty(TradingConstants.PROFIT_TAX_RATE_PROPERTY);
    }

    @Test
    public void lossesReduceTheTaxReserve() {
        assertEquals(-8.4, Update.getTaxe(100.0, 80.0), 0.0);
        assertEquals(-12.6, Update.getGewinnAfterTaxAndFeeSimple(100.0, 80.0, 0.5, 0.5), 0.0);
    }

    @Test
    public void profitTaxRateCanBeConfigured() {
        System.setProperty(TradingConstants.PROFIT_TAX_RATE_PROPERTY, "25");

        assertEquals(2.5, Update.getTaxe(100.0, 110.0), 0.0);
    }

    @Test
    public void invalidProfitTaxRateFailsClosed() {
        System.setProperty(TradingConstants.PROFIT_TAX_RATE_PROPERTY, "101");

        assertThrows(IllegalArgumentException.class, () -> Update.getTaxe(100.0, 110.0));
    }
}
