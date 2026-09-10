package com.oneofx.fusion.tradingbot.backtest;

public record BacktestVariant(long strategyId,String strategyName,double gridSpacing,
        double stopLossPercent,double takeProfitPercent) {
    public String label(){return strategyName+" · Grid "+gridSpacing+" · SL "+stopLossPercent
            +" · TP "+takeProfitPercent;}
}
