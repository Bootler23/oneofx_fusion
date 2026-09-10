package com.oneofx.fusion.tradingbot.backtest;

import java.time.Instant;
import java.util.List;

public record BacktestOptimizationResult(Instant inSampleStart,Instant inSampleEnd,
        Instant outOfSampleStart,Instant outOfSampleEnd,
        List<BacktestOptimizationRow> variants,List<BacktestWalkForwardFold> folds) {

    public BacktestOptimizationResult {
        variants=List.copyOf(variants);folds=List.copyOf(folds);
    }
}
