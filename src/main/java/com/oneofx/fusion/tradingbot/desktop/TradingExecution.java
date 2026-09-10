package com.oneofx.fusion.tradingbot.desktop;

/** Gemeinsamer Lebenszyklus fuer Live- und Paper-Ausfuehrung. */
interface TradingExecution {
    void run();
    void stop();
}
