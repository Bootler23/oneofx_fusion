package com.oneofx.fusion.tradingbot.backtest;

import java.time.Instant;

public record BacktestWalkForwardFold(int number,Instant trainingStart,Instant trainingEnd,
        Instant testStart,Instant testEnd,BacktestVariant selected,
        BacktestResult trainingResult,BacktestResult testResult) { }
