package com.oneofx.fusion.tradingbot.strategy;

/** Verhindert wiederholte Strategie-Kaeufe ohne ausreichenden Preisabstand. */
public final class EntrySpacingPolicy {
    private static final double EPSILON = 0.000000001;

    private EntrySpacingPolicy() { }

    public static boolean allows(EntrySpacingMode mode, double spacing,
            double referenceEntry, double candidatePrice) {
        if (mode == null || !Double.isFinite(spacing) || spacing < 0
                || !Double.isFinite(referenceEntry) || !Double.isFinite(candidatePrice)
                || referenceEntry <= 0 || candidatePrice <= 0) return false;
        double maximum = mode == EntrySpacingMode.PERCENT
                ? referenceEntry * (1.0 - spacing / 100.0)
                : referenceEntry - spacing;
        return maximum > 0 && candidatePrice <= maximum + EPSILON;
    }
}
