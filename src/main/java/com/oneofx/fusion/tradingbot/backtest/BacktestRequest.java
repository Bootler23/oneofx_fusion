package com.oneofx.fusion.tradingbot.backtest;

import java.time.Instant;

import com.oneofx.fusion.tradingbot.desktop.CurrencySettings;

public record BacktestRequest(long botId, long strategyId, String currency,
        String baseTimeframe, Instant start, Instant end, double initialCapital,
        double orderAmount, double feePercent, double slippagePercent,
        double stopLossPercent, double takeProfitPercent, double tickSize,
        double sizeIncrement, double minOrderAmount, double maxOrderAmount,
        BacktestExecutionConfig execution) {

    public BacktestRequest {
        if (botId <= 0 || strategyId <= 0) throw new IllegalArgumentException("Bot und Strategie fehlen.");
        currency = CurrencySettings.normalizeCurrency(currency);
        if (baseTimeframe == null || !baseTimeframe.matches("1m|5m|10m|15m|30m|1h|4h|1d"))
            throw new IllegalArgumentException("Ungültiger Backtest-Timeframe.");
        if (start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("Das Enddatum muss nach dem Startdatum liegen.");
        long estimatedBars=(end.toEpochMilli()-start.toEpochMilli())/intervalMillis(baseTimeframe);
        if(estimatedBars>20_000)throw new IllegalArgumentException(
                "Der Zeitraum umfasst mehr als 20.000 Basis-Kerzen. Bitte Zeitraum verkürzen oder Timeframe vergrößern.");
        positive(initialCapital,"Startkapital");positive(orderAmount,"Orderbetrag");
        percent(feePercent,"Gebühr");percent(slippagePercent,"Slippage");
        percent(stopLossPercent,"Stop-Loss");percent(takeProfitPercent,"Take Profit");
        positive(tickSize,"Tick-Größe");positive(sizeIncrement,"Mengenschritt");
        positive(minOrderAmount,"Mindestorder");
        if (maxOrderAmount < 0 || !Double.isFinite(maxOrderAmount))
            throw new IllegalArgumentException("Ungültige maximale Ordergröße.");
        if(execution==null)throw new IllegalArgumentException("Backtest-Ausführungskonfiguration fehlt.");
    }

    private static void positive(double value,String label){if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(label+" muss größer als 0 sein.");}
    private static void percent(double value,String label){if(!Double.isFinite(value)||value<0||value>=100)throw new IllegalArgumentException(label+" muss zwischen 0 und kleiner als 100 liegen.");}
    private static long intervalMillis(String value){return switch(value){case"1m"->60_000L;case"5m"->300_000L;case"10m"->600_000L;case"15m"->900_000L;case"30m"->1_800_000L;case"1h"->3_600_000L;case"4h"->14_400_000L;case"1d"->86_400_000L;default->throw new IllegalArgumentException("Ungültiger Backtest-Timeframe.");};}
}
