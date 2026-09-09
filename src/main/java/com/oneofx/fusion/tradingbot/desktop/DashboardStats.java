package com.oneofx.fusion.tradingbot.desktop;

/** Kompakte Datenbankübersicht für das Dashboard. */
public record DashboardStats(
        int currencies,
        int enabledCurrencies,
        int pendingBuyOrders,
        int openPositions,
        double committedCapital) {
}
