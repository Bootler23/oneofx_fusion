package com.oneofx.fusion.tradingbot.strategy;

import java.util.List;

public record StrategyEvaluation(long strategyId, String strategyName, boolean buy,
        boolean sell, boolean blocked, int confirmations, int requiredConfirmations,
        List<String> explanations) {
    public boolean buyAllowed() { return buy && !blocked && confirmations >= requiredConfirmations; }
}
