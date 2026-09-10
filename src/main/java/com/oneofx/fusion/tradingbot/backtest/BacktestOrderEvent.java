package com.oneofx.fusion.tradingbot.backtest;

public record BacktestOrderEvent(int number,String orderReference,int gridLevel,
        long timestamp,String event,double limitPrice,double fillPrice,
        double fillQuantity,double remainingAmount,String reason) { }
