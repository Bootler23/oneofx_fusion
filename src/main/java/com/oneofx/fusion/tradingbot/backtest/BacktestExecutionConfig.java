package com.oneofx.fusion.tradingbot.backtest;

import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridSettings;

public record BacktestExecutionConfig(GridMode gridMode,double gridSpacing,
        double maxPairExposure,double maxBotExposure,int maxOpenPositions,
        int maxOpenOrders,String buyOrderType,boolean dcaEnabled,int dcaMaxOrders,
        double dcaTriggerPercent,double dcaSizeMultiplier,boolean onlySellWithProfit,
        double spreadPercent,double partialFillPercent,double volumeParticipationPercent) {
    public BacktestExecutionConfig {
        new GridSettings(gridMode,gridSpacing);
        positive(maxPairExposure,"Paar-Exposure");positive(maxBotExposure,"Bot-Exposure");
        if(maxOpenPositions<1||maxOpenOrders<1)throw new IllegalArgumentException("Positions- und Orderlimit müssen größer als 0 sein.");
        buyOrderType=buyOrderType==null?"LIMIT":buyOrderType.trim().toUpperCase();
        if(!buyOrderType.matches("LIMIT|MARKET|STOP_LIMIT"))throw new IllegalArgumentException("Nicht unterstützter Kauf-Ordertyp.");
        if(dcaMaxOrders<0)throw new IllegalArgumentException("DCA-Nachkäufe dürfen nicht negativ sein.");
        percent(dcaTriggerPercent,"DCA-Trigger",true);positive(dcaSizeMultiplier,"DCA-Multiplikator");
        percent(spreadPercent,"Spread",true);percent(partialFillPercent,"Teilfüllung",false);percent(volumeParticipationPercent,"Volumenbeteiligung",false);
    }
    private static void positive(double value,String label){if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(label+" muss größer als 0 sein.");}
    private static void percent(double value,String label,boolean zero){if(!Double.isFinite(value)||value<(zero?0:Double.MIN_VALUE)||value>100)throw new IllegalArgumentException(label+" muss "+(zero?"zwischen 0 und 100":"größer als 0 und höchstens 100")+" liegen.");}
}
