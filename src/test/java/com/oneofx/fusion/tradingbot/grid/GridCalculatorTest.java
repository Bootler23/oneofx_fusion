package com.oneofx.fusion.tradingbot.grid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GridCalculatorTest {

    @Test
    public void arithmeticGridUsesConstantPriceDistance() {
        GridSettings settings = new GridSettings(GridMode.ARITHMETIC, 2.5);

        assertEquals(97.5, GridCalculator.level(100.0, 1, settings), 0.000000001);
        assertEquals(95.0, GridCalculator.level(100.0, 2, settings), 0.000000001);
        assertEquals(5, GridCalculator.firstLevelBelow(100.0, 90.0, settings));
    }

    @Test
    public void geometricGridUsesConstantPercentageDistance() {
        GridSettings settings = new GridSettings(GridMode.GEOMETRIC, 10.0);

        assertEquals(90.0, GridCalculator.level(100.0, 1, settings), 0.000000001);
        assertEquals(81.0, GridCalculator.level(100.0, 2, settings), 0.000000001);
        assertEquals(3, GridCalculator.firstLevelBelow(100.0, 81.0, settings));
    }
}
