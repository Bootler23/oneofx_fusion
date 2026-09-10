package com.oneofx.fusion.tradingbot.backtest;

public record BacktestOptimizationRow(int rank,BacktestVariant variant,
        BacktestResult inSample,BacktestResult outOfSample,double score) { }
