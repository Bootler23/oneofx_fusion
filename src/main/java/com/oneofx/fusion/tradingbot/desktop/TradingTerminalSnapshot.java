package com.oneofx.fusion.tradingbot.desktop;

import java.util.List;

public record TradingTerminalSnapshot(String currency,String timeframe,
        List<TradingCandle> candles,List<PriceLine> priceLines,List<TradeMarker> markers,
        double lastPrice,long loadedAt) {

    public TradingTerminalSnapshot {
        candles=List.copyOf(candles);priceLines=List.copyOf(priceLines);
        markers=List.copyOf(markers);
    }

    public record TradingCandle(long timestamp,double open,double high,double low,double close,
            double volume) { }

    public record PriceLine(LineType type,String label,double price,double quantity) { }

    public record TradeMarker(long timestamp,double price,String side,String label) { }

    public enum LineType { GRID, BUY_ORDER, SELL_ORDER, POSITION }
}
