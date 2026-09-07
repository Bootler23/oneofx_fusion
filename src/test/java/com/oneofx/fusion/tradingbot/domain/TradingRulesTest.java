package com.oneofx.fusion.tradingbot.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;

public class TradingRulesTest {

    @Test
    public void missingRulesBlockOrderValidation() {
        assertFalse(TradingRulesFormatter.isQuantityValid("", new BigDecimal("1")));
        assertFalse(TradingRulesFormatter.isOrderValid("", new BigDecimal("10"), new BigDecimal("1")));
    }

    @Test
    public void quantityRequiresCompleteRulesAndCorrectIncrement() {
        TradingRules rules = new TradingRules("BTC-EUR");
        rules.setMinQty(new BigDecimal("0.001"));

        assertFalse(rules.isQuantityValid(new BigDecimal("0.010")));

        rules.setStepSize(new BigDecimal("0.001"));
        assertTrue(rules.isQuantityValid(new BigDecimal("0.010")));
        assertFalse(rules.isQuantityValid(new BigDecimal("0.0105")));
        assertFalse(rules.isQuantityValid(BigDecimal.ZERO));
    }

    @Test
    public void orderRequiresPriceRulesAndCorrectIncrements() {
        TradingRules rules = completeRules();

        assertTrue(rules.isOrderValid(new BigDecimal("10.00"), new BigDecimal("1.000")));
        assertFalse(rules.isOrderValid(new BigDecimal("10.005"), new BigDecimal("1.000")));
        assertFalse(rules.isOrderValid(new BigDecimal("10.00"), new BigDecimal("1.0005")));
    }

    @Test
    public void orderHonorsNotionalLimits() {
        TradingRules rules = completeRules();
        rules.setMinOrderAmount(new BigDecimal("10"));
        rules.setMaxOrderAmount(new BigDecimal("100"));

        assertFalse(rules.isOrderValid(new BigDecimal("9.00"), new BigDecimal("1.000")));
        assertTrue(rules.isOrderValid(new BigDecimal("10.00"), new BigDecimal("1.000")));
        assertFalse(rules.isOrderValid(new BigDecimal("100.01"), new BigDecimal("1.000")));
    }

    private TradingRules completeRules() {
        TradingRules rules = new TradingRules("BTC-EUR");
        rules.setTickSize(new BigDecimal("0.01"));
        rules.setStepSize(new BigDecimal("0.001"));
        rules.setMinQty(new BigDecimal("0.001"));
        return rules;
    }
}
