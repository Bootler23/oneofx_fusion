package com.oneofx.fusion.tradingbot.backtest;

public record BacktestEquityPoint(long timestamp, double equity,
        double drawdownPercent) { }
