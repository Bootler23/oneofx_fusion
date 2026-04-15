package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.util.List;

/**
 * Stochastic RSI (StochRSI) Indikator.
 * 
 * Der StochRSI wendet die Stochastic-Formel auf RSI-Werte an statt auf Preise.
 * Er ist empfindlicher als der normale RSI und oszilliert häufiger zwischen
 * überkauft und überverkauft.
 * 
 * Berechnung:
 * 1. RSI berechnen (Standard: 14 Perioden)
 * 2. StochRSI = (RSI - min(RSI, n)) / (max(RSI, n) - min(RSI, n))
 * 3. %K = SMA(StochRSI, kPeriod)
 * 4. %D = SMA(%K, dPeriod)
 * 
 * Standard-Parameter (wie bei TradingView/Binance):
 * - RSI-Periode: 14
 * - Stoch-Periode: 14
 * - K-Periode: 3
 * - D-Periode: 3
 * 
 * Signale:
 * - Überkauft: StochRSI >= 0.80 (80%)
 * - Überverkauft: StochRSI <= 0.20 (20%)
 * - Bullish Crossover: %K kreuzt %D von unten
 * - Bearish Crossover: %K kreuzt %D von oben
 */
public class StochRSI {

    // Standard Parameter (wie TradingView)
    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final int DEFAULT_STOCH_PERIOD = 14;
    private static final int DEFAULT_K_PERIOD = 3;
    private static final int DEFAULT_D_PERIOD = 3;
    
    // Overbought/Oversold Schwellenwerte (0-1 Skala)
    private static final double OVERBOUGHT_THRESHOLD = 0.80;
    private static final double OVERSOLD_THRESHOLD = 0.20;
    
    /**
     * Repräsentiert das Ergebnis einer StochRSI-Berechnung
     */
    public static class StochRSIResult {
        private final double stochRSI;
        private final double k;
        private final double d;
        private final double rsi;
        
        public StochRSIResult(double stochRSI, double k, double d, double rsi) {
            this.stochRSI = stochRSI;
            this.k = k;
            this.d = d;
            this.rsi = rsi;
        }
        
        public double getStochRSI() { return stochRSI; }
        public double getK() { return k; }
        public double getD() { return d; }
        public double getRSI() { return rsi; }
        
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
            return String.format("StochRSI [K: %.4f, D: %.4f, RSI: %.2f] Zone: %s", 
                                 k, d, rsi, getZone());
        }
    }
    
    /**
     * Berechnet StochRSI mit Standard-Parametern (14, 14, 3, 3)
     */
    public static StochRSIResult getStochRSI(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getStochRSI(client, symbol, interval, DEFAULT_RSI_PERIOD, DEFAULT_STOCH_PERIOD, DEFAULT_K_PERIOD, DEFAULT_D_PERIOD);
    }
    
    /**
     * Gibt den %K-Wert (0-100) mit Standard-Parametern zurück.
     */
    public static double getK(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getStochRSI(client, symbol, interval).getK() * 100;
    }
    
    /**
     * Gibt den %D-Wert (0-100) mit Standard-Parametern zurück.
     */
    public static double getD(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getStochRSI(client, symbol, interval).getD() * 100;
    }
    
    /**
     * Berechnet StochRSI mit konfigurierbaren Parametern.
     * 
     * @param client Binance API Client
     * @param symbol Handelssymbol (z.B. "LTCEUR")
     * @param interval Zeitintervall
     * @param rsiPeriod RSI-Periode (Standard: 14)
     * @param stochPeriod Stochastic-Lookback-Periode (Standard: 14)
     * @param kPeriod K-Glättungsperiode (Standard: 3)
     * @param dPeriod D-SMA-Periode (Standard: 3)
     */
    public static StochRSIResult getStochRSI(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                              int rsiPeriod, int stochPeriod, int kPeriod, int dPeriod) {
        int limit = Math.max(300, rsiPeriod + stochPeriod + kPeriod + dPeriod + 100);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < rsiPeriod + stochPeriod + kPeriod + dPeriod) {
            throw new IllegalArgumentException("Nicht genügend Daten für StochRSI-Berechnung.");
        }

        return calculateStochRSI(candlesticks, rsiPeriod, stochPeriod, kPeriod, dPeriod);
    }
    
    /**
     * Berechnet StochRSI nach der Standard-Formel.
     */
    private static StochRSIResult calculateStochRSI(List<Candlestick> candlesticks, 
                                                     int rsiPeriod, int stochPeriod, int kPeriod, int dPeriod) {
        int size = candlesticks.size();
        
        // Schritt 1: RSI für jede Kerze berechnen
        double[] rsiValues = calculateRSIArray(candlesticks, rsiPeriod);
        
        // Schritt 2: StochRSI berechnen (Stochastic auf RSI-Werte)
        int stochStart = rsiPeriod + stochPeriod - 1;
        double[] stochRSIValues = new double[size];
        
        for (int i = stochStart; i < size; i++) {
            double maxRSI = Double.MIN_VALUE;
            double minRSI = Double.MAX_VALUE;
            
            for (int j = i - stochPeriod + 1; j <= i; j++) {
                if (rsiValues[j] > maxRSI) maxRSI = rsiValues[j];
                if (rsiValues[j] < minRSI) minRSI = rsiValues[j];
            }
            
            if (maxRSI == minRSI) {
                stochRSIValues[i] = 0.5;
            } else {
                stochRSIValues[i] = (rsiValues[i] - minRSI) / (maxRSI - minRSI);
            }
        }
        
        // Schritt 3: %K = SMA(StochRSI, kPeriod)
        double[] kValues = new double[size];
        int kStart = stochStart + kPeriod - 1;
        
        for (int i = kStart; i < size; i++) {
            double sum = 0;
            for (int j = 0; j < kPeriod; j++) {
                sum += stochRSIValues[i - j];
            }
            kValues[i] = sum / kPeriod;
        }
        
        // Schritt 4: %D = SMA(%K, dPeriod)
        double[] dValues = new double[size];
        int dStart = kStart + dPeriod - 1;
        
        for (int i = dStart; i < size; i++) {
            double sum = 0;
            for (int j = 0; j < dPeriod; j++) {
                sum += kValues[i - j];
            }
            dValues[i] = sum / dPeriod;
        }
        
        // Hole aktuelle Werte
        int lastIndex = size - 1;
        double stochRSI = Math.round(stochRSIValues[lastIndex] * 10000.0) / 10000.0;
        double k = Math.round(kValues[lastIndex] * 10000.0) / 10000.0;
        double d = Math.round(dValues[lastIndex] * 10000.0) / 10000.0;
        double rsi = Math.round(rsiValues[lastIndex] * 100.0) / 100.0;
        
        return new StochRSIResult(stochRSI, k, d, rsi);
    }
    
    /**
     * Berechnet RSI-Werte für alle Kerzen (Wilder's Smoothing Method)
     */
    private static double[] calculateRSIArray(List<Candlestick> candlesticks, int period) {
        int size = candlesticks.size();
        double[] rsiValues = new double[size];
        double[] gains = new double[size];
        double[] losses = new double[size];
        
        // Berechne Gewinne und Verluste
        for (int i = 1; i < size; i++) {
            double currentClose = Double.parseDouble(candlesticks.get(i).getClose());
            double prevClose = Double.parseDouble(candlesticks.get(i - 1).getClose());
            double change = currentClose - prevClose;
            
            gains[i] = change > 0 ? change : 0;
            losses[i] = change < 0 ? -change : 0;
        }
        
        // Erster Durchschnitt (SMA)
        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= period;
        avgLoss /= period;
        
        // Erster RSI
        if (avgLoss == 0) {
            rsiValues[period] = 100;
        } else {
            double rs = avgGain / avgLoss;
            rsiValues[period] = 100 - (100 / (1 + rs));
        }
        
        // Wilder's Smoothing für restliche Werte
        for (int i = period + 1; i < size; i++) {
            avgGain = ((avgGain * (period - 1)) + gains[i]) / period;
            avgLoss = ((avgLoss * (period - 1)) + losses[i]) / period;
            
            if (avgLoss == 0) {
                rsiValues[i] = 100;
            } else {
                double rs = avgGain / avgLoss;
                rsiValues[i] = 100 - (100 / (1 + rs));
            }
        }
        
        return rsiValues;
    }
    
    /**
     * Überprüft auf Crossover zwischen den letzten beiden Perioden.
     * 
     * @return 1 für bullish crossover, -1 für bearish crossover, 0 für kein crossover
     */
    public static int checkCrossover(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return checkCrossover(client, symbol, interval, DEFAULT_RSI_PERIOD, DEFAULT_STOCH_PERIOD, DEFAULT_K_PERIOD, DEFAULT_D_PERIOD);
    }
    
    public static int checkCrossover(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                     int rsiPeriod, int stochPeriod, int kPeriod, int dPeriod) {
        int limit = Math.max(300, rsiPeriod + stochPeriod + kPeriod + dPeriod + 100);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < rsiPeriod + stochPeriod + kPeriod + dPeriod + 1) {
            return 0;
        }

        StochRSIResult current = calculateStochRSI(candlesticks, rsiPeriod, stochPeriod, kPeriod, dPeriod);
        List<Candlestick> previousCandles = candlesticks.subList(0, candlesticks.size() - 1);
        StochRSIResult previous = calculateStochRSI(previousCandles, rsiPeriod, stochPeriod, kPeriod, dPeriod);
        
        if (previous.getK() <= previous.getD() && current.getK() > current.getD()) {
            return 1;  // Bullish crossover
        } else if (previous.getK() >= previous.getD() && current.getK() < current.getD()) {
            return -1; // Bearish crossover
        }
        
        return 0;
    }
    
    /**
     * Generiert ein Trading-Signal basierend auf StochRSI-Analyse.
     */
    public static String getTradingSignal(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            StochRSIResult result = getStochRSI(client, symbol, interval);
            int crossover = checkCrossover(client, symbol, interval);
            
            // Starke Signale: Crossover in extremer Zone
            if (result.isOversold() && crossover == 1) return "STRONG_BUY";
            if (result.isOverbought() && crossover == -1) return "STRONG_SELL";
            
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
     * Hilfsmethode für StochRSI-Ausgabe
     */
    public static void printStochRSI(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        printStochRSI(client, symbol, interval, DEFAULT_RSI_PERIOD, DEFAULT_STOCH_PERIOD, DEFAULT_K_PERIOD, DEFAULT_D_PERIOD);
    }
    
    public static void printStochRSI(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                     int rsiPeriod, int stochPeriod, int kPeriod, int dPeriod) {
        try {
            StochRSIResult result = getStochRSI(client, symbol, interval, rsiPeriod, stochPeriod, kPeriod, dPeriod);
            int crossover = checkCrossover(client, symbol, interval, rsiPeriod, stochPeriod, kPeriod, dPeriod);
            String signal = getTradingSignal(client, symbol, interval);
            
            System.out.println("════════════════════════════════════════");
            System.out.println("StochRSI Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            System.out.printf("StochRSI: %.4f%n", result.getStochRSI());
            System.out.printf("%%K:       %.4f%n", result.getK());
            System.out.printf("%%D:       %.4f%n", result.getD());
            System.out.printf("RSI:      %.2f%n", result.getRSI());
            System.out.println("────────────────────────────────────────");
            
            if (crossover == 1) System.out.println("⭐ BULLISH CROSSOVER! %K kreuzt %D von unten");
            else if (crossover == -1) System.out.println("⚠ BEARISH CROSSOVER! %K kreuzt %D von oben");
            
            if (result.isOverbought()) System.out.println("🔴 Überkauft (K >= 0.80)");
            else if (result.isOversold()) System.out.println("🟢 Überverkauft (K <= 0.20)");
            else System.out.println("⚪ Neutral");
            
            System.out.println("────────────────────────────────────────");
            System.out.println("Signal: " + signal);
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei StochRSI-Berechnung: " + e.getMessage());
        }
    }
}
