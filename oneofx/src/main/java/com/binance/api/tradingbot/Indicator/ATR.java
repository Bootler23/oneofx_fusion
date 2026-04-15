package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.util.List;

/**
 * Average True Range (ATR) Indikator.
 * 
 * Der ATR ist ein Volatilitätsindikator, entwickelt von J. Welles Wilder.
 * Er misst die durchschnittliche Handelsspanne über einen bestimmten Zeitraum.
 * 
 * Berechnung:
 * 1. True Range (TR) = max(
 *      High - Low,
 *      |High - Previous Close|,
 *      |Low - Previous Close|
 *    )
 * 2. ATR = Gleitender Durchschnitt der TR über n Perioden
 * 
 * Verwendung:
 * - Volatilitätsmessung (höherer ATR = höhere Volatilität)
 * - Stop-Loss-Platzierung (z.B. 2x ATR unter Einstiegspreis)
 * - Position-Sizing
 * - Breakout-Bestätigung
 * 
 * Standard-Periode: 14
 */
public class ATR {

    private static final int DEFAULT_PERIOD = 14;
    
    /**
     * Repräsentiert das Ergebnis einer ATR-Berechnung
     */
    public static class ATRResult {
        private final double atr;
        private final double atrPercent;
        private final double currentPrice;
        private final double trueRange;
        
        public ATRResult(double atr, double atrPercent, double currentPrice, double trueRange) {
            this.atr = atr;
            this.atrPercent = atrPercent;
            this.currentPrice = currentPrice;
            this.trueRange = trueRange;
        }
        
        public double getATR() { return atr; }
        public double getATRPercent() { return atrPercent; }
        public double getCurrentPrice() { return currentPrice; }
        public double getTrueRange() { return trueRange; }
        
        /**
         * Volatilitätsbewertung basierend auf ATR%
         */
        public String getVolatilityLevel() {
            if (atrPercent < 1.0) return "SEHR_NIEDRIG";
            if (atrPercent < 2.0) return "NIEDRIG";
            if (atrPercent < 4.0) return "MITTEL";
            if (atrPercent < 6.0) return "HOCH";
            return "SEHR_HOCH";
        }
        
        /**
         * Berechnet Stop-Loss basierend auf ATR-Multiplikator
         */
        public double getStopLoss(double entryPrice, double atrMultiplier, boolean isLong) {
            if (isLong) {
                return entryPrice - (atr * atrMultiplier);
            } else {
                return entryPrice + (atr * atrMultiplier);
            }
        }
        
        /**
         * Berechnet Take-Profit basierend auf ATR-Multiplikator
         */
        public double getTakeProfit(double entryPrice, double atrMultiplier, boolean isLong) {
            if (isLong) {
                return entryPrice + (atr * atrMultiplier);
            } else {
                return entryPrice - (atr * atrMultiplier);
            }
        }
        
        @Override
        public String toString() {
            return String.format("ATR [Wert: %.4f, Prozent: %.2f%%, Volatilität: %s]", 
                                 atr, atrPercent, getVolatilityLevel());
        }
    }
    
    /**
     * Berechnet ATR mit Standard-Periode (14)
     */
    public static ATRResult getATR(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getATR(client, symbol, interval, DEFAULT_PERIOD);
    }
    
    /**
     * Berechnet ATR mit konfigurierbarer Periode.
     * 
     * @param client Binance API Client
     * @param symbol Handelssymbol (z.B. "LTCEUR")
     * @param interval Zeitintervall
     * @param period ATR-Periode (Standard: 14)
     */
    public static ATRResult getATR(BinanceApiRestClient client, String symbol, CandlestickInterval interval, int period) {
        int limit = Math.max(100, period + 50);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period + 1) {
            throw new IllegalArgumentException("Nicht genügend Daten für ATR-Berechnung.");
        }

        return calculateATR(candlesticks, period);
    }
    
    /**
     * Berechnet ATR nach Wilder's Methode (Smoothed Moving Average).
     */
    private static ATRResult calculateATR(List<Candlestick> candlesticks, int period) {
        int size = candlesticks.size();
        double[] trueRanges = new double[size];
        
        // Berechne True Range für jede Kerze (ab Index 1, da Previous Close benötigt)
        for (int i = 1; i < size; i++) {
            double high = Double.parseDouble(candlesticks.get(i).getHigh());
            double low = Double.parseDouble(candlesticks.get(i).getLow());
            double prevClose = Double.parseDouble(candlesticks.get(i - 1).getClose());
            
            trueRanges[i] = calculateTrueRange(high, low, prevClose);
        }
        
        // Erste ATR = Einfacher Durchschnitt der ersten 'period' True Ranges
        double atr = 0;
        for (int i = 1; i <= period; i++) {
            atr += trueRanges[i];
        }
        atr /= period;
        
        // Wilder's Smoothing: ATR = ((ATR_prev * (period - 1)) + TR_current) / period
        for (int i = period + 1; i < size; i++) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period;
        }
        
        // Aktuelle Werte
        int lastIndex = size - 1;
        double currentPrice = Double.parseDouble(candlesticks.get(lastIndex).getClose());
        double currentTR = trueRanges[lastIndex];
        double atrPercent = (atr / currentPrice) * 100.0;
        
        // Runden
        atr = Math.round(atr * 10000.0) / 10000.0;
        atrPercent = Math.round(atrPercent * 100.0) / 100.0;
        currentTR = Math.round(currentTR * 10000.0) / 10000.0;
        
        return new ATRResult(atr, atrPercent, currentPrice, currentTR);
    }
    
    /**
     * Berechnet True Range für eine einzelne Kerze.
     * TR = max(High - Low, |High - PrevClose|, |Low - PrevClose|)
     */
    private static double calculateTrueRange(double high, double low, double prevClose) {
        double range1 = high - low;
        double range2 = Math.abs(high - prevClose);
        double range3 = Math.abs(low - prevClose);
        
        return Math.max(range1, Math.max(range2, range3));
    }
    
    /**
     * Gibt nur den ATR-Wert zurück
     */
    public static double getValue(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getATR(client, symbol, interval).getATR();
    }
    
    public static double getValue(BinanceApiRestClient client, String symbol, CandlestickInterval interval, int period) {
        return getATR(client, symbol, interval, period).getATR();
    }
    
    /**
     * Gibt ATR als Prozentsatz des aktuellen Preises zurück
     */
    public static double getPercent(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        return getATR(client, symbol, interval).getATRPercent();
    }
    
    /**
     * Berechnet Stop-Loss-Preis basierend auf ATR
     * 
     * @param entryPrice Einstiegspreis
     * @param atrMultiplier ATR-Multiplikator (typisch 1.5 - 3.0)
     * @param isLong true für Long-Position, false für Short
     */
    public static double calculateStopLoss(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                           double entryPrice, double atrMultiplier, boolean isLong) {
        ATRResult result = getATR(client, symbol, interval);
        return result.getStopLoss(entryPrice, atrMultiplier, isLong);
    }
    
    /**
     * Berechnet Take-Profit-Preis basierend auf ATR
     */
    public static double calculateTakeProfit(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                             double entryPrice, double atrMultiplier, boolean isLong) {
        ATRResult result = getATR(client, symbol, interval);
        return result.getTakeProfit(entryPrice, atrMultiplier, isLong);
    }
    
    /**
     * Berechnet Position-Size basierend auf Risiko und ATR
     * 
     * @param accountBalance Kontostand
     * @param riskPercent Risiko-Prozent pro Trade (z.B. 1.0 für 1%)
     * @param atrMultiplier ATR-Multiplikator für Stop-Loss
     * @return Empfohlene Positionsgröße
     */
    public static double calculatePositionSize(BinanceApiRestClient client, String symbol, CandlestickInterval interval,
                                               double accountBalance, double riskPercent, double atrMultiplier) {
        ATRResult result = getATR(client, symbol, interval);
        double riskAmount = accountBalance * (riskPercent / 100.0);
        double stopDistance = result.getATR() * atrMultiplier;
        
        if (stopDistance == 0) return 0;
        
        return riskAmount / stopDistance;
    }
    
    /**
     * Hilfsmethode für ATR-Ausgabe
     */
    public static void printATR(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        printATR(client, symbol, interval, DEFAULT_PERIOD);
    }
    
    public static void printATR(BinanceApiRestClient client, String symbol, CandlestickInterval interval, int period) {
        try {
            ATRResult result = getATR(client, symbol, interval, period);
            
            System.out.println("════════════════════════════════════════");
            System.out.println("ATR Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            System.out.printf("ATR (%d): %.4f%n", period, result.getATR());
            System.out.printf("ATR %%:   %.2f%%%n", result.getATRPercent());
            System.out.printf("True Range (aktuell): %.4f%n", result.getTrueRange());
            System.out.printf("Aktueller Preis: %.4f%n", result.getCurrentPrice());
            System.out.println("────────────────────────────────────────");
            System.out.println("Volatilität: " + result.getVolatilityLevel());
            System.out.println("────────────────────────────────────────");
            
            // Stop-Loss Beispiele
            double entryPrice = result.getCurrentPrice();
            System.out.println("Stop-Loss Beispiele (Long-Position):");
            System.out.printf("  1.0x ATR: %.4f%n", result.getStopLoss(entryPrice, 1.0, true));
            System.out.printf("  1.5x ATR: %.4f%n", result.getStopLoss(entryPrice, 1.5, true));
            System.out.printf("  2.0x ATR: %.4f%n", result.getStopLoss(entryPrice, 2.0, true));
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei ATR-Berechnung: " + e.getMessage());
        }
    }
}
