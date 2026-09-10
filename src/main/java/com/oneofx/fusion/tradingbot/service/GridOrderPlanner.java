package com.oneofx.fusion.tradingbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

import com.oneofx.fusion.tradingbot.grid.GridCalculator;
import com.oneofx.fusion.tradingbot.grid.GridSettings;

/** Gemeinsame, reine Auswahl freier Grid-Preise fuer Live und Paper. */
public final class GridOrderPlanner {
    private static final int MAX_GRID_STEPS = 10_000;

    private GridOrderPlanner() { }

    public static List<Double> candidates(double anchorPrice, double marketPrice,
            GridSettings settings, DoubleUnaryOperator priceFormatter,
            DoublePredicate occupied, int maximum, int maximumLevelIndexBelowMarket) {
        List<Double> result = new ArrayList<>();
        if (maximum <= 0) return result;
        double anchor = Math.max(anchorPrice, marketPrice);
        long nextLevel = GridCalculator.firstLevelBelow(anchor, marketPrice, settings);
        double previous = Double.NaN;
        int levelIndex = 0;
        for (int count = 0; count < MAX_GRID_STEPS && result.size() < maximum; count++) {
            double price = priceFormatter.applyAsDouble(
                    GridCalculator.level(anchor, nextLevel++, settings));
            if (Double.compare(price, previous) == 0) continue;
            previous = price;
            if (price <= 0) break;
            if (price >= marketPrice) continue;
            if (levelIndex++ > maximumLevelIndexBelowMarket) break;
            if (!occupied.test(price)) result.add(price);
        }
        return result;
    }
}
