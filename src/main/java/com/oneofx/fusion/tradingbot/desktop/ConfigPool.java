package com.oneofx.fusion.tradingbot.desktop;

/** Benannte Baseconfig-Ueberschreibung fuer ein oder mehrere Handelspaare. */
public record ConfigPool(long id, long botId, String name, BotBaseConfig configuration) {
    @Override public String toString() { return name; }
}
