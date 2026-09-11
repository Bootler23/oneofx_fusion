package com.oneofx.fusion.tradingbot.strategy;

/** Einheit fuer den Mindestabstand zwischen zwei Strategie-Einstiegen. */
public enum EntrySpacingMode {
    PERCENT,
    ABSOLUTE;

    @Override public String toString() {
        return this == PERCENT ? "Prozentual (%)" : "Fester Preiswert";
    }
}
