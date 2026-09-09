package com.oneofx.fusion.tradingbot.grid;

/** Berechnungsart für die Preisstufen eines Grids. */
public enum GridMode {
    ARITHMETIC("Arithmetisch · fester Preisabstand"),
    GEOMETRIC("Geometrisch · fester Prozentabstand");

    private final String displayName;

    GridMode(String displayName) {
        this.displayName = displayName;
    }

    public static GridMode fromDatabase(String value) {
        if (value == null || value.isBlank()) return GEOMETRIC;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GEOMETRIC;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
