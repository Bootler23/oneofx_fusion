package com.oneofx.fusion.tradingbot.desktop;

/** Globale Konfiguration und harte Risikogrenzen eines lokalen Bots. */
public record BotProfile(long id, String name, boolean enabled, boolean archived,
        Mode mode, String strategy, double budget, double maxExposure,
        int maxOpenPositions, int maxOpenOrders, double paperFeePercent,
        double paperSlippagePercent) {

    public enum Mode {
        LIVE("Live"), PAPER("Paper");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public BotProfile {
        if (id <= 0) throw new IllegalArgumentException("Ungültige Bot-ID.");
        name = requireText(name, "Bot-Name", 80);
        strategy = requireText(strategy, "Strategie", 80);
        requirePositive(budget, "Bot-Budget");
        requirePositive(maxExposure, "Exposure-Limit");
        if (maxExposure > budget) {
            throw new IllegalArgumentException(
                    "Das Exposure-Limit darf nicht größer als das Bot-Budget sein.");
        }
        if (maxOpenPositions < 1 || maxOpenPositions > 100_000) {
            throw new IllegalArgumentException(
                    "Maximale offene Positionen müssen zwischen 1 und 100000 liegen.");
        }
        if (maxOpenOrders < 1 || maxOpenOrders > 100_000) {
            throw new IllegalArgumentException(
                    "Maximale offene Orders müssen zwischen 1 und 100000 liegen.");
        }
        requirePercent(paperFeePercent, "Paper-Gebühr");
        requirePercent(paperSlippagePercent, "Paper-Slippage");
        if (mode == null) throw new IllegalArgumentException("Der Bot-Modus fehlt.");
    }

    @Override
    public String toString() {
        return name;
    }

    private static String requireText(String value, String label, int maximum) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " darf nicht leer sein.");
        }
        String result = value.trim();
        if (result.length() > maximum) {
            throw new IllegalArgumentException(label + " darf höchstens " + maximum
                    + " Zeichen lang sein.");
        }
        return result;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(label + " muss größer als 0 sein.");
        }
    }

    private static void requirePercent(double value, String label) {
        if (!Double.isFinite(value) || value < 0 || value >= 100) {
            throw new IllegalArgumentException(label
                    + " muss zwischen 0 und kleiner als 100 Prozent liegen.");
        }
    }
}
