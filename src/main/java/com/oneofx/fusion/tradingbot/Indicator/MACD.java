package com.oneofx.fusion.tradingbot.Indicator;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;
import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * MACD Klasse, die die Ta4j-Bibliothek verwendet, um präzise MACD-Berechnungen durchzuführen
 * 
 * MACD (Moving Average Convergence Divergence) ist ein Momentum-Indikator, der aus drei Komponenten besteht:
 * - MACD-Linie: Differenz zwischen einer schnellen EMA (12) und einer langsamen EMA (26)
 * - Signal-Linie: EMA (9) der MACD-Linie
 * - Histogramm: Differenz zwischen MACD-Linie und Signal-Linie
 */
public class MACD {

    // Standard MACD-Parameter
    private static final int FAST_PERIOD = 12;
    private static final int SLOW_PERIOD = 26;
    private static final int SIGNAL_PERIOD = 9;
    
    /**
     * Repräsentiert das Ergebnis einer MACD-Berechnung
     */
    public static class MACDResult {
        private final double macdLine;
        private final double signalLine;
        private final double histogram;
        
        public MACDResult(double macdLine, double signalLine, double histogram) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
        }
        
        public double getMacdLine() { return macdLine; }
        public double getSignalLine() { return signalLine; }
        public double getHistogram() { return histogram; }
        
        /**
         * Prüft ob ein bullisches Crossover vorliegt (MACD über Signal)
         */
        public boolean isBullishCrossover() {
            return macdLine > signalLine;
        }
        
        /**
         * Prüft ob ein bärisches Crossover vorliegt (MACD unter Signal)
         */
        public boolean isBearishCrossover() {
            return macdLine < signalLine;
        }
        
        /**
         * Prüft ob das Histogramm positiv ist (bullisches Momentum)
         */
        public boolean isHistogramPositive() {
            return histogram > 0;
        }
        
        @Override
        public String toString() {
            return String.format("MACD: %.6f, Signal: %.6f, Histogram: %.6f", 
                               macdLine, signalLine, histogram);
        }
    }
    
    /**
     * Berechnet MACD für ein bestimmtes Symbol und Zeitintervall
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen
     * @return MACDResult mit allen MACD-Werten
     */
    public static MACDResult getMACD(FusionApiClient client, String symbol, CandlestickInterval interval) {
        // Hole genügend Candlestick-Daten für eine stabile MACD-Berechnung
        // Wir brauchen mindestens SLOW_PERIOD + SIGNAL_PERIOD für stabile Werte
        int limit = Math.max(200, (SLOW_PERIOD + SIGNAL_PERIOD) * 3);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < SLOW_PERIOD + SIGNAL_PERIOD) {
            throw new IllegalArgumentException("Nicht genügend Daten für MACD-Berechnung. Benötigt mindestens " + 
                                             (SLOW_PERIOD + SIGNAL_PERIOD) + " Kerzen.");
        }

        // Erstelle eine Ta4j Zeitserie aus den Binance-Candlesticks
        BarSeries series = convertBinanceCandlestickToBarSeries(candlesticks, symbol);
        
        // Berechne MACD mit Ta4j
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macdIndicator = new MACDIndicator(closePrice, FAST_PERIOD, SLOW_PERIOD);
        EMAIndicator signalLineIndicator = new EMAIndicator(macdIndicator, SIGNAL_PERIOD);
        
        // Hole die aktuellen Werte (letzte Position)
        int lastIndex = series.getEndIndex();
        
        Num macdValue = macdIndicator.getValue(lastIndex);
        Num signalValue = signalLineIndicator.getValue(lastIndex);
        
        double macdLine = macdValue.doubleValue();
        double signalLine = signalValue.doubleValue();
        double histogram = macdLine - signalLine;
        
        // Runde die Werte auf 6 Dezimalstellen für bessere Lesbarkeit
        macdLine = Math.round(macdLine * 1000000.0) / 1000000.0;
        signalLine = Math.round(signalLine * 1000000.0) / 1000000.0;
        histogram = Math.round(histogram * 1000000.0) / 1000000.0;
        
        return new MACDResult(macdLine, signalLine, histogram);
    }
    
    /**
     * Überprüft auf MACD-Crossover zwischen den letzten beiden Perioden
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol
     * @param interval Das Zeitintervall
     * @return 1 für bullish crossover, -1 für bearish crossover, 0 für kein crossover
     */
    public static int checkCrossover(FusionApiClient client, String symbol, CandlestickInterval interval) {
        int limit = Math.max(200, (SLOW_PERIOD + SIGNAL_PERIOD) * 3);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < SLOW_PERIOD + SIGNAL_PERIOD + 1) {
            return 0; // Nicht genügend Daten
        }

        BarSeries series = convertBinanceCandlestickToBarSeries(candlesticks, symbol);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macdIndicator = new MACDIndicator(closePrice, FAST_PERIOD, SLOW_PERIOD);
        EMAIndicator signalLineIndicator = new EMAIndicator(macdIndicator, SIGNAL_PERIOD);
        
        int lastIndex = series.getEndIndex();
        int previousIndex = lastIndex - 1;
        
        // Aktuelle Werte
        double currentMacd = macdIndicator.getValue(lastIndex).doubleValue();
        double currentSignal = signalLineIndicator.getValue(lastIndex).doubleValue();
        
        // Vorherige Werte
        double previousMacd = macdIndicator.getValue(previousIndex).doubleValue();
        double previousSignal = signalLineIndicator.getValue(previousIndex).doubleValue();
        
        // Prüfe auf Crossover
        if (previousMacd <= previousSignal && currentMacd > currentSignal) {
            return 1; // Bullish crossover
        } else if (previousMacd >= previousSignal && currentMacd < currentSignal) {
            return -1; // Bearish crossover
        }
        
        return 0; // Kein crossover
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
    
    /**
     * Hilfsmethode für einfache MACD-Ausgabe
     * 
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol
     * @param interval Das Zeitintervall
     */
    public static void printMACD(FusionApiClient client, String symbol, CandlestickInterval interval) {
        try {
            MACDResult result = getMACD(client, symbol, interval);
            System.out.println("MACD Analyse für " + symbol + " (" + interval + "):");
            System.out.println(result);
            
            int crossover = checkCrossover(client, symbol, interval);
            if (crossover == 1) {
                System.out.println("⭐ BULLISH CROSSOVER DETECTED! Starkes Kaufsignal!");
            } else if (crossover == -1) {
                System.out.println("⚠ BEARISH CROSSOVER DETECTED! Starkes Verkaufssignal!");
            } else if (result.isBullishCrossover()) {
                System.out.println("Signal: BUY (MACD über Signal-Linie)");
            } else {
                System.out.println("Signal: SELL (MACD unter Signal-Linie)");
            }
            
        } catch (Exception e) {
            System.err.println("Fehler bei MACD-Berechnung: " + e.getMessage());
        }
    }
}
