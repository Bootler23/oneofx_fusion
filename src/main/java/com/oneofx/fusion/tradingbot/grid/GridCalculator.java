package com.oneofx.fusion.tradingbot.grid;

/** Reine, testbare Preisberechnung für arithmetische und geometrische Grids. */
public final class GridCalculator {

    private GridCalculator() {
    }

    public static double level(double anchorPrice, long level, GridSettings settings) {
        requirePrice(anchorPrice, "Ankerpreis");
        if (level < 0) throw new IllegalArgumentException("Grid-Stufe darf nicht negativ sein.");
        if (settings.mode() == GridMode.ARITHMETIC) {
            return anchorPrice - level * settings.spacing();
        }
        double factor = 1.0 - settings.spacing() / 100.0;
        return anchorPrice * Math.pow(factor, level);
    }

    /** Liefert die erste rechnerische Grid-Stufe strikt unterhalb des Marktpreises. */
    public static long firstLevelBelow(double anchorPrice, double marketPrice,
            GridSettings settings) {
        requirePrice(anchorPrice, "Ankerpreis");
        requirePrice(marketPrice, "Marktpreis");
        double anchor = Math.max(anchorPrice, marketPrice);
        if (settings.mode() == GridMode.ARITHMETIC) {
            return Math.max(1L,
                    (long) Math.floor((anchor - marketPrice) / settings.spacing()) + 1L);
        }
        double factor = 1.0 - settings.spacing() / 100.0;
        double raw = Math.log(marketPrice / anchor) / Math.log(factor);
        if (!Double.isFinite(raw) || raw < 1.0) return 1L;
        long candidate = Math.max(1L, (long) Math.floor(raw) + 1L);
        double candidatePrice = level(anchor, candidate, settings);
        double tolerance = Math.max(Math.ulp(marketPrice) * 8.0, marketPrice * 1.0e-14);
        if (candidatePrice >= marketPrice
                || Math.abs(candidatePrice - marketPrice) <= tolerance) {
            candidate++;
        }
        return candidate;
    }

    private static void requirePrice(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " muss größer als 0 sein.");
        }
    }
}
