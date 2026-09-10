package com.oneofx.fusion.tradingbot.strategy;

public record StrategyDefinition(long id, long botId, String name,
        int minimumConfirmations, boolean enabled) {
    public StrategyDefinition {
        if (name == null || name.isBlank() || name.trim().length() > 80)
            throw new IllegalArgumentException("Der Strategiename muss 1 bis 80 Zeichen enthalten.");
        name = name.trim();
        if (minimumConfirmations < 1 || minimumConfirmations > 100)
            throw new IllegalArgumentException("Bestätigungen müssen zwischen 1 und 100 liegen.");
    }
    @Override public String toString() { return name; }
}
