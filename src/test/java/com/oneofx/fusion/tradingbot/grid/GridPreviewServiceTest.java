package com.oneofx.fusion.tradingbot.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import com.oneofx.fusion.tradingbot.desktop.CurrencySettings;

public class GridPreviewServiceTest {

    @Test
    public void previewsEveryCapitalFundedArithmeticOrder() {
        CurrencySettings settings = new CurrencySettings("BTCEUR", false,
                25.0, 100.0, GridMode.ARITHMETIC, 5.0,
                2.0, true, 2.5, 0.8);
        GridPreviewService.Rules rules = new GridPreviewService.Rules(
                new BigDecimal("0.01"), new BigDecimal("0.00001"), 10.0, 100_000.0);

        GridPreviewService.Preview preview =
                new GridPreviewService().calculate(settings, 100.0, rules);

        assertEquals(4, preview.fundedOrderCount());
        assertEquals(4, preview.levels().size());
        assertEquals(new BigDecimal("95.00"), preview.levels().get(0).price());
        assertEquals(new BigDecimal("80.00"), preview.levels().get(3).price());
        assertEquals(100.0, preview.requiredCapital(), 0.000001);
        assertEquals(20.0, preview.coveragePercent(), 0.000001);
        assertTrue(preview.warnings().isEmpty());
    }

    @Test
    public void warnsWhenRoundedOrderFallsBelowExchangeMinimum() {
        CurrencySettings settings = new CurrencySettings("BTCEUR", false,
                10.0, 20.0, GridMode.GEOMETRIC, 1.0,
                2.0, true, 2.5, 0.8);
        GridPreviewService.Rules rules = new GridPreviewService.Rules(
                new BigDecimal("0.01"), new BigDecimal("0.001"), 10.0, 100_000.0);

        GridPreviewService.Preview preview =
                new GridPreviewService().calculate(settings, 100.0, rules);

        assertFalse(preview.levels().isEmpty());
        assertEquals("Nicht handelbar", preview.levels().get(0).status());
        assertFalse(preview.warnings().isEmpty());
    }
}
