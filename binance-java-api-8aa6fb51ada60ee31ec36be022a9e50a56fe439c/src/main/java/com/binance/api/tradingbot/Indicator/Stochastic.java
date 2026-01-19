package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.util.List;

/**
 * Stochastic Oscillator Implementierung (Slow Stochastic).
 * 
 * Der Stochastic Oscillator ist ein Momentum-Indikator, der den aktuellen
 * Schlusskurs im Verhältnis zum High-Low-Bereich über eine bestimmte Periode zeigt.
 * 
 * Es gibt drei Varianten:
 * 1. FAST Stochastic: %K = Raw Stochastic, %D = SMA(%K, 3)
 * 2. SLOW Stochastic: %K = SMA(Fast %K, 3), %D = SMA(Slow %K, 3)
 * 3. FULL Stochastic: Konfigurierbare Perioden für alle Glättungen
 * 
 * Berechnung (Slow Stochastic - Standard bei Binance):
 * - Fast %K = (Close - Lowest Low) / (Highest High - Lowest Low) * 100
 * - Slow %K = SMA(Fast %K, kSlowing)  [Standard: 3]
 * - Slow %D = SMA(Slow %K, dPeriod)   [Standard: 3]
 * 
 * Standard-Parameter:
 * - Periode: 14 (für High/Low Berechnung)
 * - K-Slowing: 3
 * - D-Periode: 3
 * 
 * Signale:
 * - Überkauft: %K >= 80
 * - Überverkauft: %K <= 20
 * - Bullish Crossover: %K kreuzt %D von unten nach oben
 * - Bearish Crossover: %K kreuzt %D von oben nach unten
 */
public class Stochastic {

    // Standard Parameter
    private static final int DEFAULT_PERIOD = 14;
    private static final int DEFAULT_K_SLOWING = 3;
    private static final int DEFAULT_D_PERIOD = 3;
    
    // Overbought/Oversold Schwellenwerte
    private static final double OVERBOUGHT_THRESHOLD = 80.0;
    private static final double OVERSOLD_THRESHOLD = 20.0;
    
    /**
     * Repräsentiert das Ergebnis einer Stochastic-Berechnung
     */
    public static class StochasticResult {
        private final double k;
        private final double d;
        
        public StochasticResult(double k, double d) {
            this.k = k;
            this.d = d;
        }
        
        public double getK() { return k; }
        public double getD() { return d; }
        
        public boolean isOverbought() { return k >= OVERBOUGHT_THRESHOLD; }
        public boolean isOversold() { return k <= OVERSOLD_THRESHOLD; }
        public boolean isBullish() { return k > d; }
        public boolean isBearish() { return k < d; }
        
        public String getZone() {
            if (isOverbought()) return "OVERBOUGHT";
            if (isOversold()) return "OVERSOLD";
            return "NEUTRAL";
        }
        
        @Override
        public String toString() {
            return String.format("Stochastic [%%K: %.2f, %%D: %.2f] Zone: %s", k, d, getZone());
        }
    }
    
    /**
     * Berechnet Slow Stochastic mit Standard-Parametern (14, 3, 3)
     */
    public static StochasticResult getStochastic(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getStochastic(client, symbol, interval, DEFAULT_PERIOD, DEFAULT_K_SLOWING, DEFAULT_D_PERIOD);
    }
    
    /**
     * Berechnet Slow Stochastic mit konfigurierbaren Parametern.
     * 
     * @param client Binance API Client
     * @param symbol Handelssymbol (z.B. "LTCEUR")
     * @param interval Zeitintervall
     * @param period Lookback-Periode für High/Low (Standard: 14)
     * @param kSlowing K-Glättungsperiode (Standard: 3)
     * @param dPeriod D-SMA-Periode (Standard: 3)
     */
    public static StochasticResult getStochastic(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                                  int period, int kSlowing, int dPeriod) {
        int limit = Math.max(200, period + kSlowing + dPeriod + 50);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + kSlowing + dPeriod) {
            throw new IllegalArgumentException("Nicht genügend Daten für Stochastic-Berechnung.");
        }

        return calculateSlowStochastic(candlesticks, period, kSlowing, dPeriod);
    }
    
    /**
     * Berechnet Slow Stochastic nach der Standard-Formel.
     * 
     * 1. Fast %K = (Close - LowestLow) / (HighestHigh - LowestLow) * 100
     * 2. Slow %K = SMA(Fast %K, kSlowing)
     * 3. Slow %D = SMA(Slow %K, dPeriod)
     */
    private static StochasticResult calculateSlowStochastic(List<Candlestick> candlesticks, int period, int kSlowing, int dPeriod) {
        int size = candlesticks.size();
        
        // Schritt 1: Berechne Fast %K für jede Kerze
        double[] fastK = new double[size];
        
        for (int i = period - 1; i < size; i++) {
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;
            
            for (int j = i - period + 1; j <= i; j++) {
                double high = Double.parseDouble(candlesticks.get(j).getHigh());
                double low = Double.parseDouble(candlesticks.get(j).getLow());
                
                if (high > highestHigh) highestHigh = high;
                if (low < lowestLow) lowestLow = low;
            }
            
            double close = Double.parseDouble(candlesticks.get(i).getClose());
            
            if (highestHigh == lowestLow) {
                fastK[i] = 50.0;
            } else {
                fastK[i] = ((close - lowestLow) / (highestHigh - lowestLow)) * 100.0;
            }
        }
        
        // Schritt 2: Berechne Slow %K = SMA(Fast %K, kSlowing)
        double[] slowK = new double[size];
        int slowKStart = period - 1 + kSlowing - 1;
        
        for (int i = slowKStart; i < size; i++) {
            double sum = 0;
            for (int j = 0; j < kSlowing; j++) {
                sum += fastK[i - j];
            }
            slowK[i] = sum / kSlowing;
        }
        
        // Schritt 3: Berechne Slow %D = SMA(Slow %K, dPeriod)
        double[] slowD = new double[size];
        int slowDStart = slowKStart + dPeriod - 1;
        
        for (int i = slowDStart; i < size; i++) {
            double sum = 0;
            for (int j = 0; j < dPeriod; j++) {
                sum += slowK[i - j];
            }
            slowD[i] = sum / dPeriod;
        }
        
        // Hole aktuelle Werte
        int lastIndex = size - 1;
        double k = Math.round(slowK[lastIndex] * 100.0) / 100.0;
        double d = Math.round(slowD[lastIndex] * 100.0) / 100.0;
        
        return new StochasticResult(k, d);
    }
    
    /**
     * Überprüft auf Crossover zwischen den letzten beiden Perioden.
     * 
     * @return 1 für bullish crossover, -1 für bearish crossover, 0 für kein crossover
     */
    public static int checkCrossover(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return checkCrossover(client, symbol, interval, DEFAULT_PERIOD, DEFAULT_K_SLOWING, DEFAULT_D_PERIOD);
    }
    
    public static int checkCrossover(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                     int period, int kSlowing, int dPeriod) {
        int limit = Math.max(200, period + kSlowing + dPeriod + 50);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + kSlowing + dPeriod + 1) {
            return 0;
        }

        StochasticResult current = calculateSlowStochastic(candlesticks, period, kSlowing, dPeriod);
        List<Candlestick> previousCandles = candlesticks.subList(0, candlesticks.size() - 1);
        StochasticResult previous = calculateSlowStochastic(previousCandles, period, kSlowing, dPeriod);
        
        if (previous.getK() <= previous.getD() && current.getK() > current.getD()) {
            return 1;  // Bullish crossover
        } else if (previous.getK() >= previous.getD() && current.getK() < current.getD()) {
            return -1; // Bearish crossover
        }
        
        return 0;
    }
    
    /**
     * Generiert ein Trading-Signal basierend auf Stochastic-Analyse.
     */
    public static String getTradingSignal(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            StochasticResult stoch = getStochastic(client, symbol, interval);
            int crossover = checkCrossover(client, symbol, interval);
            
            // Starke Signale: Crossover in extremer Zone
            if (stoch.isOversold() && crossover == 1) return "STRONG_BUY";
            if (stoch.isOverbought() && crossover == -1) return "STRONG_SELL";
            
            // Normale Signale: Crossover
            if (crossover == 1) return "BUY";
            if (crossover == -1) return "SELL";
            
            return "HOLD";
        } catch (Exception e) {
            return "HOLD";
        }
    }
    
    public static boolean isOverbought(double k) { return k >= OVERBOUGHT_THRESHOLD; }
    public static boolean isOversold(double k) { return k <= OVERSOLD_THRESHOLD; }
    
    /**
     * Hilfsmethode für Stochastic-Ausgabe
     */
    public static void printStochastic(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            StochasticResult result = getStochastic(client, symbol, interval);
            int crossover = checkCrossover(client, symbol, interval);
            String signal = getTradingSignal(client, symbol, interval);
            
            System.out.println("════════════════════════════════════════");
            System.out.println("Stochastic Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            System.out.println(result);
            System.out.println("────────────────────────────────────────");
            
            if (crossover == 1) System.out.println("⭐ BULLISH CROSSOVER! %K kreuzt %D von unten");
            else if (crossover == -1) System.out.println("⚠ BEARISH CROSSOVER! %K kreuzt %D von oben");
            
            if (result.isOverbought()) System.out.println("🔴 Überkauft (%K >= 80)");
            else if (result.isOversold()) System.out.println("🟢 Überverkauft (%K <= 20)");
            
            System.out.println("────────────────────────────────────────");
            System.out.println("Signal: " + signal);
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei Stochastic-Berechnung: " + e.getMessage());
        }
    }
    
    /**
     * Hilfsmethode mit KDJ-kompatibler Ausgabe (berechnet zusätzlich J = 3K - 2D)
     */
    public static void printKDJ(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        printKDJ(client, symbol, interval, 9, 3, 3);
    }
    
    public static void printKDJ(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                int period, int kSlowing, int dPeriod) {
        try {
            StochasticResult result = getStochastic(client, symbol, interval, period, kSlowing, dPeriod);
            double j = 3 * result.getK() - 2 * result.getD();
            j = Math.round(j * 100.0) / 100.0;
            
            int crossover = checkCrossover(client, symbol, interval, period, kSlowing, dPeriod);
            
            System.out.println("════════════════════════════════════════");
            System.out.println("KDJ Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            System.out.printf("KDJ [K: %.2f, D: %.2f, J: %.2f]%n", result.getK(), result.getD(), j);
            System.out.println("────────────────────────────────────────");
            
            if (crossover == 1) System.out.println("⭐ BULLISH CROSSOVER!");
            else if (crossover == -1) System.out.println("⚠ BEARISH CROSSOVER!");
            
            if (result.isOverbought()) System.out.println("🔴 K überkauft (>= 80)");
            else if (result.isOversold()) System.out.println("🟢 K überverkauft (<= 20)");
            
            if (j > 100) System.out.println("🚨 J EXTREM überkauft (> 100)");
            else if (j < 0) System.out.println("💎 J EXTREM überverkauft (< 0)");
            
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei KDJ-Berechnung: " + e.getMessage());
        }
    }
}
