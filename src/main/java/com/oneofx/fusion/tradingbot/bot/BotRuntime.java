package com.oneofx.fusion.tradingbot.bot;

import java.util.concurrent.atomic.AtomicLong;

/** Prozessweiter Kontext des Bots, den die einzelne lokale Engine gerade bedient. */
public final class BotRuntime {
    public static final long DEFAULT_BOT_ID = 1L;

    private static final AtomicLong ACTIVE_BOT_ID = new AtomicLong(DEFAULT_BOT_ID);

    private BotRuntime() {
    }

    public static long activeBotId() {
        return ACTIVE_BOT_ID.get();
    }

    public static void select(long botId) {
        if (botId <= 0) throw new IllegalArgumentException("Ungültige Bot-ID.");
        ACTIVE_BOT_ID.set(botId);
    }
}
