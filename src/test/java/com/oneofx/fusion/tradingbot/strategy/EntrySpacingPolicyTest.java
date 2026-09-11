package com.oneofx.fusion.tradingbot.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EntrySpacingPolicyTest {
    @Test public void percentageRequiresNextEntryBelowConfiguredDistance() {
        assertFalse(EntrySpacingPolicy.allows(EntrySpacingMode.PERCENT, 2.0, 100.0, 98.01));
        assertTrue(EntrySpacingPolicy.allows(EntrySpacingMode.PERCENT, 2.0, 100.0, 98.0));
    }

    @Test public void absoluteRequiresFixedPriceDistance() {
        assertFalse(EntrySpacingPolicy.allows(EntrySpacingMode.ABSOLUTE, 250.0, 50_000.0, 49_751.0));
        assertTrue(EntrySpacingPolicy.allows(EntrySpacingMode.ABSOLUTE, 250.0, 50_000.0, 49_750.0));
    }
}
