package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.util.List;

/**
 * KDJ-Indikator Implementierung nach der Binance-Formel (SMA-basiert).
 * 
 * Der KDJ-Indikator ist eine Erweiterung des Stochastic Oscillators.
 * Er besteht aus drei Linien:
 * - K-Linie: SMA des RSV
 * - D-Linie: SMA der K-Linie
 * - J-Linie: 3*K - 2*D
 * 
 * Berechnung (Binance-Formel mit SMA):
 * - RSV = (Close - Lowest Low) / (Highest High - Lowest Low) * 100
 * - K = SMA(RSV, kPeriod)  (Standard: 3)
 * - D = SMA(K, dPeriod)    (Standard: 3)
 * - J = 3*K - 2*D
 * 
 * Standard-Parameter (wie bei Binance):
 * - Periode: 9 (für High/Low Berechnung)
 * - K-Periode: 3 (SMA-Glättung)
 * - D-Periode: 3 (SMA-Glättung)
 */
public class KDJ {

    // Standard KDJ-Parameter (wie bei Binance)
    private static final int DEFAULT_PERIOD = 9;
    private static final int K_PERIOD = 3;
    private static final int D_PERIOD = 3;
    
    // Overbought/Oversold Schwellenwerte
    private static final double OVERBOUGHT_THRESHOLD = 80.0;
    private static final double OVERSOLD_THRESHOLD = 20.0;
    private static final double EXTREME_OVERBOUGHT_J = 100.0;
    private static final double EXTREME_OVERSOLD_J = 0.0;
    
    /**
     * Repräsentiert das Ergebnis einer KDJ-Berechnung
     */
    public static class KDJResult {
        private final double k;
        private final double d;
        private final double j;
        
        public KDJResult(double k, double d, double j) {
            this.k = k;
            this.d = d;
            this.j = j;
        }
        
        public double getK() { return k; }
        public double getD() { return d; }
        public double getJ() { return j; }
        
        public boolean isKOverbought() { return k >= OVERBOUGHT_THRESHOLD; }
        public boolean isKOversold() { return k <= OVERSOLD_THRESHOLD; }
        public boolean isJExtremelyOverbought() { return j > EXTREME_OVERBOUGHT_J; }
        public boolean isJExtremelyOversold() { return j < EXTREME_OVERSOLD_J; }
        public boolean isBullish() { return k > d; }
        public boolean isBearish() { return k < d; }
        
        public String getZone() {
            if (isKOverbought()) return "OVERBOUGHT";
            if (isKOversold()) return "OVERSOLD";
            return "NEUTRAL";
        }
        
        @Override
        public String toString() {
            return String.format("KDJ [K: %.2f, D: %.2f, J: %.2f] Zone: %s", k, d, j, getZone());
        }
    }
    
    /**
     * Berechnet KDJ für ein bestimmtes Symbol und Zeitintervall
     */
    public static KDJResult getKDJ(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getKDJ(client, symbol, interval, DEFAULT_PERIOD, K_PERIOD, D_PERIOD);
    }
    
    /**
     * Berechnet KDJ mit konfigurierbaren Parametern nach der Binance-Formel (SMA-basiert).
     */
    public static KDJResult getKDJ(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                   int period, int kPeriod, int dPeriod) {
        // Hole genügend Candlestick-Daten
        int limit = Math.max(200, period + kPeriod + dPeriod + 50);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + kPeriod + dPeriod) {
            throw new IllegalArgumentException("Nicht genügend Daten für KDJ-Berechnung.");
        }

        return calculateKDJ(candlesticks, period, kPeriod, dPeriod);
    }
    
    /**
     * Berechnet KDJ nach der Binance-Formel mit SMA-Glättung.
     * 
     * 1. Berechne RSV für jede Kerze: RSV = (Close - LowestLow) / (HighestHigh - LowestLow) * 100
     * 2. K = SMA(RSV, kPeriod) - Simple Moving Average der letzten kPeriod RSV-Werte
     * 3. D = SMA(K, dPeriod) - Simple Moving Average der letzten dPeriod K-Werte
     * 4. J = 3*K - 2*D
     */
    private static KDJResult calculateKDJ(List<Candlestick> candlesticks, int period, int kPeriod, int dPeriod) {
        int size = candlesticks.size();
        
        // Array für RSV-Werte
        double[] rsvValues = new double[size];
        
        // Schritt 1: Berechne RSV für jede Kerze (ab Index period-1)
        for (int i = period - 1; i < size; i++) {
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;
            
            // Finde Highest High und Lowest Low der letzten 'period' Kerzen
            for (int j = i - period + 1; j <= i; j++) {
                double high = Double.parseDouble(candlesticks.get(j).getHigh());
                double low = Double.parseDouble(candlesticks.get(j).getLow());
                
                if (high > highestHigh) highestHigh = high;
                if (low < lowestLow) lowestLow = low;
            }
            
            double close = Double.parseDouble(candlesticks.get(i).getClose());
            
            // Berechne RSV
            if (highestHigh == lowestLow) {
                rsvValues[i] = 50.0;
            } else {
                rsvValues[i] = ((close - lowestLow) / (highestHigh - lowestLow)) * 100.0;
            }
        }
        
        // Array für K-Werte
        double[] kValues = new double[size];
        
        // Schritt 2: Berechne K = SMA(RSV, kPeriod)
        int kStartIndex = period - 1 + kPeriod - 1;
        for (int i = kStartIndex; i < size; i++) {
            double sum = 0;
            for (int j = 0; j < kPeriod; j++) {
                sum += rsvValues[i - j];
            }
            kValues[i] = sum / kPeriod;
        }
        
        // Array für D-Werte
        double[] dValues = new double[size];
        
        // Schritt 3: Berechne D = SMA(K, dPeriod)
        int dStartIndex = kStartIndex + dPeriod - 1;
        for (int i = dStartIndex; i < size; i++) {
            double sum = 0;
            for (int j = 0; j < dPeriod; j++) {
                sum += kValues[i - j];
            }
            dValues[i] = sum / dPeriod;
        }
        
        // Hole die aktuellen Werte (letzte Position)
        int lastIndex = size - 1;
        double k = kValues[lastIndex];
        double d = dValues[lastIndex];
        
        // Schritt 4: Berechne J = 3*K - 2*D
        double j = 3.0 * k - 2.0 * d;
        
        // Runde auf 2 Dezimalstellen
        k = Math.round(k * 100.0) / 100.0;
        d = Math.round(d * 100.0) / 100.0;
        j = Math.round(j * 100.0) / 100.0;
        
        return new KDJResult(k, d, j);
    }
    
    /**
     * Überprüft auf KDJ-Crossover zwischen den letzten beiden Perioden
     */
    public static int checkCrossover(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return checkCrossover(client, symbol, interval, DEFAULT_PERIOD, K_PERIOD, D_PERIOD);
    }
    
    public static int checkCrossover(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                     int period, int kPeriod, int dPeriod) {
        int limit = Math.max(200, period + kPeriod + dPeriod + 50);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + kPeriod + dPeriod + 1) {
            return 0;
        }

        KDJResult current = calculateKDJ(candlesticks, period, kPeriod, dPeriod);
        List<Candlestick> previousCandles = candlesticks.subList(0, candlesticks.size() - 1);
        KDJResult previous = calculateKDJ(previousCandles, period, kPeriod, dPeriod);
        
        if (previous.getK() <= previous.getD() && current.getK() > current.getD()) {
            return 1;  // Bullish crossover
        } else if (previous.getK() >= previous.getD() && current.getK() < current.getD()) {
            return -1; // Bearish crossover
        }
        
        return 0;
    }
    
    /**
     * Generiert ein Trading-Signal
     */
    public static String getTradingSignal(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            KDJResult kdj = getKDJ(client, symbol, interval);
            int crossover = checkCrossover(client, symbol, interval);
            
            if (kdj.isJExtremelyOversold() && crossover == 1) return "STRONG_BUY";
            if (kdj.isJExtremelyOverbought() && crossover == -1) return "STRONG_SELL";
            if (kdj.isKOversold() && crossover == 1) return "BUY";
            if (kdj.isKOverbought() && crossover == -1) return "SELL";
            if (crossover == 1) return "BUY";
            if (crossover == -1) return "SELL";
            
            return "HOLD";
        } catch (Exception e) {
            return "HOLD";
        }
    }
    
    public static boolean isOverbought(double k) { return k >= OVERBOUGHT_THRESHOLD; }
    public static boolean isOversold(double k) { return k <= OVERSOLD_THRESHOLD; }
    public static boolean isExtremelyOverbought(double j) { return j > EXTREME_OVERBOUGHT_J; }
    public static boolean isExtremelyOversold(double j) { return j < EXTREME_OVERSOLD_J; }
    
    /**
     * Hilfsmethode für KDJ-Ausgabe
     */
    public static void printKDJ(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            KDJResult result = getKDJ(client, symbol, interval);
            int crossover = checkCrossover(client, symbol, interval);
            String signal = getTradingSignal(client, symbol, interval);
            
            System.out.println("════════════════════════════════════════");
            System.out.println("KDJ Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            System.out.println(result);
            System.out.println("────────────────────────────────────────");
            
            if (crossover == 1) System.out.println("⭐ BULLISH CROSSOVER!");
            else if (crossover == -1) System.out.println("⚠ BEARISH CROSSOVER!");
            
            if (result.isKOverbought()) System.out.println("🔴 K überkauft (>= 80)");
            else if (result.isKOversold()) System.out.println("🟢 K überverkauft (<= 20)");
            
            if (result.isJExtremelyOverbought()) System.out.println("🚨 J EXTREM überkauft (> 100)");
            else if (result.isJExtremelyOversold()) System.out.println("💎 J EXTREM überverkauft (< 0)");
            
            System.out.println("────────────────────────────────────────");
            System.out.println("Signal: " + signal);
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei KDJ-Berechnung: " + e.getMessage());
        }
    }
}
