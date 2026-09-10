package com.oneofx.fusion.tradingbot.backtest;

public record BacktestTrade(int number, long entryTime, long exitTime,
        double entryPrice, double exitPrice, double quantity, double fees,
        double pnl, String exitReason, String explanation) { }
