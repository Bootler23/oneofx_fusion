package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import org.ta4j.core.*;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * RSI Klasse, die die Ta4j-Bibliothek verwendet, um präzise RSI-Berechnungen durchzuführen.
 * 
 * Unterstützt Standard-RSI sowie RSI mit SMA-Smoothing Line (wie in TradingView).
 * 
 * Standard-Parameter (TradingView):
 * - Length: 14
 * - Smoothing Line: SMA
 * - Smoothing Length: 14
 */
public class RSI {
    
    // Standard Parameter
    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final int DEFAULT_SMOOTHING_LENGTH = 14;
    
    /**
     * Repräsentiert das Ergebnis einer RSI-Berechnung mit Smoothing Line
     */
    public static class RSIResult {
        private final double rsi;
        private final double smoothingLine;  // SMA der RSI-Werte
        
        public RSIResult(double rsi, double smoothingLine) {
            this.rsi = rsi;
            this.smoothingLine = smoothingLine;
        }
        
        public double getRSI() { return rsi; }
        public double getSmoothingLine() { return smoothingLine; }
        
        public boolean isOverbought() { return rsi >= 70; }
        public boolean isOversold() { return rsi <= 30; }
        
        /** RSI über der Smoothing Line = bullish */
        public boolean isBullish() { return rsi > smoothingLine; }
        /** RSI unter der Smoothing Line = bearish */
        public boolean isBearish() { return rsi < smoothingLine; }
        
        public String getZone() {
            if (isOverbought()) return "OVERBOUGHT";
            if (isOversold()) return "OVERSOLD";
            return "NEUTRAL";
        }
        
        @Override
        public String toString() {
            return String.format("RSI [Value: %.2f, SMA: %.2f] Zone: %s", rsi, smoothingLine, getZone());
        }
    }
    
    /**
     * Berechnet den RSI (Relative Strength Index) für ein bestimmtes Symbol
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen
     * @param period Die Periode für die RSI-Berechnung (typischerweise 14)
     * @return Der aktuelle RSI-Wert
     */
    public static double getRSI(BinanceApiRestClient client, String symbol, CandlestickInterval interval, int period) {
        // Hole die Candlestick-Daten (hole dreimal so viele Kerzen wie die Periode für bessere Genauigkeit)
        int limit = period * 3;
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + 1) {
            throw new IllegalArgumentException("Nicht genügend Daten für RSI-Berechnung");
        }

        // Erstelle eine Ta4j Zeitserie aus den Binance-Candlesticks
        BarSeries series = convertBinanceCandlestickToBarSeries(candlesticks);
        
        // Berechne RSI mit Ta4j
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, period);
          // Hole den aktuellen RSI-Wert (letzte Position)
        Num rsiValue = rsiIndicator.getValue(series.getEndIndex());
        
        return Math.round(rsiValue.doubleValue() * 100.0) / 100.0;
    }
    
    /**
     * Berechnet RSI mit SMA Smoothing Line (wie TradingView Standard).
     * Standard-Parameter: Length=14, Smoothing=SMA, Smoothing Length=14
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen
     * @return RSIResult mit RSI-Wert und SMA Smoothing Line
     */
    public static RSIResult getRSIWithSmoothing(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getRSIWithSmoothing(client, symbol, interval, DEFAULT_RSI_PERIOD, DEFAULT_SMOOTHING_LENGTH);
    }
    
    /**
     * Berechnet RSI mit konfigurierbarer SMA Smoothing Line.
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen
     * @param rsiPeriod Die Periode für die RSI-Berechnung (Standard: 14)
     * @param smoothingLength Die Periode für die SMA Smoothing Line (Standard: 14)
     * @return RSIResult mit RSI-Wert und SMA Smoothing Line
     */
    public static RSIResult getRSIWithSmoothing(BinanceApiRestClient client, String symbol, 
                                                 CandlestickInterval interval, int rsiPeriod, int smoothingLength) {
        // Hole genügend Daten für beide Berechnungen
        int limit = Math.max(100, (rsiPeriod + smoothingLength) * 3);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < rsiPeriod + smoothingLength) {
            throw new IllegalArgumentException("Nicht genügend Daten für RSI-Berechnung mit Smoothing");
        }

        // Erstelle eine Ta4j Zeitserie aus den Binance-Candlesticks
        BarSeries series = convertBinanceCandlestickToBarSeries(candlesticks);
        
        // Berechne RSI
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, rsiPeriod);
        
        // Berechne SMA der RSI-Werte (Smoothing Line)
        SMAIndicator smoothingLine = new SMAIndicator(rsiIndicator, smoothingLength);
        
        // Hole aktuelle Werte
        int endIndex = series.getEndIndex();
        double rsiValue = Math.round(rsiIndicator.getValue(endIndex).doubleValue() * 100.0) / 100.0;
        double smaValue = Math.round(smoothingLine.getValue(endIndex).doubleValue() * 100.0) / 100.0;
        
        return new RSIResult(rsiValue, smaValue);
    }
    
    /**
     * Konvertiert Binance Candlesticks in Ta4j BarSeries
     * @param candlesticks Die Binance Candlesticks
     * @return Eine Ta4j BarSeries
     */
    private static BarSeries convertBinanceCandlestickToBarSeries(List<Candlestick> candlesticks) {
        BarSeries series = new BaseBarSeries();
        
        for (Candlestick candle : candlesticks) {
            ZonedDateTime time = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(candle.getCloseTime()), 
                ZoneId.systemDefault()
            );
            
            double open = Double.parseDouble(candle.getOpen());
            double high = Double.parseDouble(candle.getHigh());
            double low = Double.parseDouble(candle.getLow());
            double close = Double.parseDouble(candle.getClose());
            double volume = Double.parseDouble(candle.getVolume());
            
            series.addBar(time, open, high, low, close, volume);
        }
        
        return series;
    }

    /**
     * Überprüft ob der RSI überkauft ist (über 70)
     * @param rsi Der RSI-Wert
     * @return true wenn überkauft, sonst false
     */
    public static boolean isOverbought(double rsi) {
        return rsi >= 70;
    }

    /**
     * Überprüft ob der RSI überverkauft ist (unter 30)
     * @param rsi Der RSI-Wert
     * @return true wenn überverkauft, sonst false
     */
    public static boolean isOversold(double rsi) {
        return rsi <= 30;
    }
}

