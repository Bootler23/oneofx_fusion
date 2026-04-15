package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import org.ta4j.core.*;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * BollingerBands Klasse, die die Ta4j-Bibliothek verwendet, um Bollinger-Bänder zu berechnen.
 * 
 * Bollinger Bänder bestehen aus drei Linien:
 * - Middle Band: Simple Moving Average (SMA) über die gewählte Periode
 * - Upper Band: Middle Band + (Standardabweichung × Multiplikator)
 * - Lower Band: Middle Band - (Standardabweichung × Multiplikator)
 * 
 * Standard-Parameter: 20 Perioden, 2.0 Standardabweichungen
 */
public class BollingerBands {
    
    // Standard Bollinger Band-Parameter
    private static final int DEFAULT_PERIOD = 20;
    private static final double DEFAULT_DEVIATION_MULTIPLIER = 2.0;
    
    /**
     * Repräsentiert das Ergebnis einer Bollinger-Band-Berechnung
     */
    public static class BollingerBandsResult {
        private final double upperBand;
        private final double middleBand;
        private final double lowerBand;
        private final double currentPrice;
        
        public BollingerBandsResult(double upperBand, double middleBand, double lowerBand, double currentPrice) {
            this.upperBand = upperBand;
            this.middleBand = middleBand;
            this.lowerBand = lowerBand;
            this.currentPrice = currentPrice;
        }
        
        public double getUpperBand() { 
            return upperBand; 
        }
        
        public double getMiddleBand() { 
            return middleBand; 
        }
        
        public double getLowerBand() { 
            return lowerBand; 
        }
        
        public double getCurrentPrice() {
            return currentPrice;
        }
        
        /**
         * Berechnet die Bandbreite (Bandwidth): (Upper - Lower) / Middle
         * Ein Maß für die Volatilität
         */
        public double getBandwidth() {
            return (upperBand - lowerBand) / middleBand;
        }
        
        /**
         * Berechnet den %B Indikator: (Preis - Lower) / (Upper - Lower)
         * Werte: 0 = am unteren Band, 0.5 = in der Mitte, 1 = am oberen Band
         */
        public double getPercentB() {
            return (currentPrice - lowerBand) / (upperBand - lowerBand);
        }
        
        /**
         * Prüft ob der Preis das obere Band berührt oder überschritten hat
         */
        public boolean isPriceAboveUpperBand() {
            return currentPrice >= upperBand;
        }
        
        /**
         * Prüft ob der Preis das untere Band berührt oder unterschritten hat
         */
        public boolean isPriceBelowLowerBand() {
            return currentPrice <= lowerBand;
        }
        
        /**
         * Prüft ob der Preis nahe am oberen Band ist (innerhalb von 5%)
         */
        public boolean isPriceNearUpperBand() {
            double threshold = upperBand * 0.95;
            return currentPrice >= threshold;
        }
        
        /**
         * Prüft ob der Preis nahe am unteren Band ist (innerhalb von 5%)
         */
        public boolean isPriceNearLowerBand() {
            double threshold = lowerBand * 1.05;
            return currentPrice <= threshold;
        }
        
        /**
         * Prüft ob sich die Bänder verengen (Squeeze) - Bandbreite unter 10%
         * Deutet auf niedrige Volatilität und möglichen Ausbruch hin
         */
        public boolean isSqueeze() {
            return getBandwidth() < 0.10;
        }
        
        @Override
        public String toString() {
            return String.format("Bollinger Bands - Upper: %.4f, Middle: %.4f, Lower: %.4f, Price: %.4f, %%B: %.2f", 
                               upperBand, middleBand, lowerBand, currentPrice, getPercentB());
        }
    }
    
    /**
     * Berechnet Bollinger-Bänder mit Standard-Parametern (20 Perioden, 2.0 Std.abw.)
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen
     * @return BollingerBandsResult mit allen drei Bändern
     */
    public static BollingerBandsResult getBollingerBands(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getBollingerBands(client, symbol, interval, DEFAULT_PERIOD, DEFAULT_DEVIATION_MULTIPLIER);
    }
    
    /**
     * Berechnet Bollinger-Bänder mit benutzerdefinierten Parametern
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen (z.B. ONE_MINUTE, FIVE_MINUTES, ONE_HOUR, ONE_DAY)
     * @param period Die Anzahl der Perioden für die Berechnung (typisch 20)
     * @param deviationMultiplier Der Multiplikator für die Standardabweichung (typisch 2.0)
     * @return BollingerBandsResult mit allen drei Bändern
     */
    public static BollingerBandsResult getBollingerBands(BinanceApiRestClient client, String symbol, 
                                                         CandlestickInterval interval, int period, 
                                                         double deviationMultiplier) {
        // Hole genügend Candlestick-Daten für eine stabile Berechnung
        int limit = Math.max(100, period * 3);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + 1) {
            throw new IllegalArgumentException("Nicht genügend Daten für Bollinger-Band-Berechnung. Benötigt mindestens " + 
                                             (period + 1) + " Kerzen.");
        }

        // Erstelle eine Ta4j Zeitserie aus den Binance-Candlesticks
        BarSeries series = convertBinanceCandlestickToBarSeries(candlesticks, symbol);
        
        // Berechne Bollinger Bänder mit Ta4j
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        // Middle Band ist der Simple Moving Average (SMA) über die Schlusskurse
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        BollingerBandsMiddleIndicator bbmIndicator = new BollingerBandsMiddleIndicator(sma);
        
        // Standardabweichung für die Berechnung der oberen und unteren Bänder
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, period);
        
        // Upper und Lower Bands
        BollingerBandsUpperIndicator bbuIndicator = new BollingerBandsUpperIndicator(bbmIndicator, standardDeviation, series.numOf(deviationMultiplier));
        BollingerBandsLowerIndicator bblIndicator = new BollingerBandsLowerIndicator(bbmIndicator, standardDeviation, series.numOf(deviationMultiplier));
        
        // Hole die aktuellen Werte (letzte Position)
        int lastIndex = series.getEndIndex();
        
        double upperBand = bbuIndicator.getValue(lastIndex).doubleValue();
        double middleBand = bbmIndicator.getValue(lastIndex).doubleValue();
        double lowerBand = bblIndicator.getValue(lastIndex).doubleValue();
        double currentPrice = closePrice.getValue(lastIndex).doubleValue();
        
        // Runde die Werte auf 4 Dezimalstellen für bessere Lesbarkeit
        upperBand = Math.round(upperBand * 10000.0) / 10000.0;
        middleBand = Math.round(middleBand * 10000.0) / 10000.0;
        lowerBand = Math.round(lowerBand * 10000.0) / 10000.0;
        currentPrice = Math.round(currentPrice * 10000.0) / 10000.0;
        
        return new BollingerBandsResult(upperBand, middleBand, lowerBand, currentPrice);
    }
    
    /**
     * Konvertiert Binance Candlesticks in Ta4j BarSeries
     * 
     * @param candlesticks Die Binance Candlesticks
     * @param seriesName Name für die Serie
     * @return Eine Ta4j BarSeries
     */
    private static BarSeries convertBinanceCandlestickToBarSeries(List<Candlestick> candlesticks, String seriesName) {
        BarSeries series = new BaseBarSeries(seriesName);
        
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
}
