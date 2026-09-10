package com.oneofx.fusion.tradingbot.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TradingDecisionPolicyTest {
    @Test
    public void hardStopHasPriority() {
        TradingDecisionPolicy.ExitDecision result = TradingDecisionPolicy.evaluateExit(
                100, 97, 105, true, 2, true, 2, 1);
        assertEquals(TradingDecisionPolicy.ExitReason.HARD_STOP_LOSS, result.reason());
        assertTrue(result.shouldExit());
    }

    @Test
    public void trailingStopFollowsPeak() {
        TradingDecisionPolicy.ExitDecision result = TradingDecisionPolicy.evaluateExit(
                100, 108, 110, false, 0, true, 5, 1);
        assertEquals(TradingDecisionPolicy.ExitReason.TRAILING_STOP, result.reason());
        assertEquals(108.9, result.triggerPrice(), 0.000001);
    }

    @Test
    public void inactiveTrailingStopDoesNotExit() {
        TradingDecisionPolicy.ExitDecision result = TradingDecisionPolicy.evaluateExit(
                100, 101, 101, false, 0, true, 5, 1);
        assertFalse(result.shouldExit());
    }

    @Test
    public void takeProfitExitsBeforeTrailingStop() {
        TradingDecisionPolicy.ExitDecision result = TradingDecisionPolicy.evaluateExit(
                100, 105, 105, false, 0, true, 10, 1, 5);
        assertEquals(TradingDecisionPolicy.ExitReason.TAKE_PROFIT, result.reason());
        assertTrue(result.shouldExit());
    }
}
