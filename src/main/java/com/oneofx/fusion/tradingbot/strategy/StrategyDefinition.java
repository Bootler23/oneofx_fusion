package com.oneofx.fusion.tradingbot.strategy;

public record StrategyDefinition(long id, long botId, String name,
        int minimumConfirmations, boolean enabled, EntrySpacingMode entrySpacingMode,
        double entrySpacing) {
    public StrategyDefinition(long id, long botId, String name,
            int minimumConfirmations, boolean enabled) {
        this(id, botId, name, minimumConfirmations, enabled, EntrySpacingMode.PERCENT, 1.0);
    }

    public StrategyDefinition {
        if (name == null || name.isBlank() || name.trim().length() > 80)
            throw new IllegalArgumentException("Der Strategiename muss 1 bis 80 Zeichen enthalten.");
        name = name.trim();
        if (minimumConfirmations < 1 || minimumConfirmations > 100)
            throw new IllegalArgumentException("Bestätigungen müssen zwischen 1 und 100 liegen.");
        if (entrySpacingMode == null)
            throw new IllegalArgumentException("Abstandstyp fehlt.");
        if (!Double.isFinite(entrySpacing) || entrySpacing <= 0
                || entrySpacingMode == EntrySpacingMode.PERCENT && entrySpacing >= 100)
            throw new IllegalArgumentException(entrySpacingMode == EntrySpacingMode.PERCENT
                    ? "Der Positionsabstand muss gr\u00f6\u00dfer als 0 und kleiner als 100 % sein."
                    : "Der feste Positionsabstand muss gr\u00f6\u00dfer als 0 sein.");
    }
    @Override public String toString() { return name; }
}
