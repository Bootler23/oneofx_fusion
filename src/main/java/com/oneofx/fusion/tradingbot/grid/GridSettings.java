package com.oneofx.fusion.tradingbot.grid;

/** Persistierte Grid-Art und deren absoluter beziehungsweise prozentualer Abstand. */
public record GridSettings(GridMode mode, double spacing) {

    public GridSettings {
        if (mode == null) throw new IllegalArgumentException("Die Grid-Art fehlt.");
        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            throw new IllegalArgumentException("Der Grid-Abstand muss größer als 0 sein.");
        }
        if (mode == GridMode.GEOMETRIC && spacing >= 100.0) {
            throw new IllegalArgumentException(
                    "Der prozentuale Grid-Abstand muss kleiner als 100 sein.");
        }
    }

    public static GridSettings legacy(int grid) {
        int safeGrid = grid > 0 ? grid : 7;
        return new GridSettings(GridMode.GEOMETRIC, 1.0 / safeGrid);
    }
}
