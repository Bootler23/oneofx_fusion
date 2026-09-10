package com.oneofx.fusion.tradingbot.backtest;

import java.util.List;

public record BacktestResult(long runId, double initialCapital, double finalCapital,
        double netProfit, double returnPercent, double maxDrawdownPercent,
        int tradeCount, double winRatePercent, double profitFactor,
        List<BacktestTrade> trades, List<BacktestOrderEvent> orderEvents,
        List<BacktestEquityPoint> equity) {

    public BacktestResult withRunId(long id) {
        return new BacktestResult(id,initialCapital,finalCapital,netProfit,returnPercent,
                maxDrawdownPercent,tradeCount,winRatePercent,profitFactor,trades,orderEvents,equity);
    }
}
