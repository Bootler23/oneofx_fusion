package com.oneofx.fusion.tradingbot.SellOrderProcess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oneofx.fusion.tradingbot.SellOrderProcess.SellOrderProcess.TrailingStopDecision;

public class SellOrderProcessTest {

    @Test
    public void reachingActivationOnlyActivatesTrailingStop() {
        TrailingStopDecision decision = SellOrderProcess.evaluateTrailingStop(
                100.0, 101.2, 100.0, false, 1.2, 0.4);

        assertTrue(decision.active());
        assertFalse(decision.sell());
        assertEquals(101.2, decision.peakPrice(), 0.000001);
        assertEquals(100.7952, decision.triggerPrice(), 0.000001);
    }

    @Test
    public void activeTrailingStopFollowsPeakWithFixedDistance() {
        TrailingStopDecision newPeak = SellOrderProcess.evaluateTrailingStop(
                100.0, 103.0, 101.2, true, 1.2, 0.4);

        assertTrue(newPeak.active());
        assertFalse(newPeak.sell());
        assertEquals(103.0, newPeak.peakPrice(), 0.000001);
        assertEquals(102.588, newPeak.triggerPrice(), 0.000001);

        TrailingStopDecision pullback = SellOrderProcess.evaluateTrailingStop(
                100.0, 102.58, newPeak.peakPrice(), true, 1.2, 0.4);

        assertTrue(pullback.sell());
        assertEquals(103.0, pullback.peakPrice(), 0.000001);
        assertEquals(102.588, pullback.triggerPrice(), 0.000001);
    }

    @Test
    public void pullbackBeforeActivationDoesNotSell() {
        TrailingStopDecision decision = SellOrderProcess.evaluateTrailingStop(
                100.0, 100.3, 101.0, false, 1.2, 0.4);

        assertFalse(decision.active());
        assertFalse(decision.sell());
        assertTrue(Double.isNaN(decision.triggerPrice()));
    }

    @Test
    public void hardStopUsesConfiguredDistanceFromEachBuyPrice() {
        assertFalse(SellOrderProcess.shouldTriggerHardStop(100.0, 98.01, 2.0));
        assertTrue(SellOrderProcess.shouldTriggerHardStop(100.0, 98.0, 2.0));
        assertFalse(SellOrderProcess.shouldTriggerHardStop(100.0, 80.0, 0.0));
    }
}
