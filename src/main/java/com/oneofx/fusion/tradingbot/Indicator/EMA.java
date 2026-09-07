package com.oneofx.fusion.tradingbot.Indicator;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;

import java.util.List;

/**
 * Exponential Moving Average (EMA) Indikator.
 * 
 * Der EMA ist ein gleitender Durchschnitt, der neueren Preisen mehr Gewicht gibt.
 * Er reagiert schneller auf Preisänderungen als der SMA.
 * 
 * Berechnung:
 * - Multiplier = 2 / (Periode + 1)
 * - EMA = (Close - EMA_prev) * Multiplier + EMA_prev
 * - Erster EMA = SMA der ersten 'Periode' Werte
 * 
 * Verwendung:
 * - Trendbestimmung (Preis über EMA = bullish, unter EMA = bearish)
 * - EMA 200 als langfristiger Trendindikator
 * - EMA-Crossovers (z.B. EMA 50 kreuzt EMA 200)
 * - Dynamische Support/Resistance-Levels
 * 
 * Gängige Perioden:
 * - EMA 9/12: Kurzfristig
 * - EMA 26/50: Mittelfristig
 * - EMA 200: Langfristig (Golden Cross / Death Cross)
 */
public class EMA {

    private static final int DEFAULT_PERIOD = 200;
    
    /**
     * Preistyp für EMA-Berechnung
     */
    public enum PriceType {
        CLOSE,  // Schlusskurs (Standard)
        OPEN,   // Eröffnungskurs
        HIGH,   // Höchstkurs
        LOW,    // Tiefstkurs
        HL2,    // (High + Low) / 2
        HLC3,   // (High + Low + Close) / 3
        OHLC4   // (Open + High + Low + Close) / 4
    }
    
    /**
     * Repräsentiert das Ergebnis einer EMA-Berechnung
     */
    public static class EMAResult {
        private final double ema;
        private final double currentPrice;
        private final int period;
        private final double distancePercent;
        
        public EMAResult(double ema, double currentPrice, int period) {
            this.ema = ema;
            this.currentPrice = currentPrice;
            this.period = period;
            this.distancePercent = ((currentPrice - ema) / ema) * 100.0;
        }
        
        public double getEMA() { return ema; }
        public double getCurrentPrice() { return currentPrice; }
        public int getPeriod() { return period; }
        public double getDistancePercent() { return distancePercent; }
        
        /**
         * Prüft ob Preis über EMA liegt (bullish)
         */
        public boolean isPriceAboveEMA() { return currentPrice > ema; }
        
        /**
         * Prüft ob Preis unter EMA liegt (bearish)
         */
        public boolean isPriceBelowEMA() { return currentPrice < ema; }
        
        /**
         * Gibt Trend-Richtung basierend auf EMA-Position zurück
         */
        public String getTrend() {
            if (isPriceAboveEMA()) return "BULLISH";
            if (isPriceBelowEMA()) return "BEARISH";
            return "NEUTRAL";
        }
        
        /**
         * Prüft ob Preis nahe am EMA ist (innerhalb von threshold %)
         */
        public boolean isNearEMA(double thresholdPercent) {
            return Math.abs(distancePercent) <= thresholdPercent;
        }
        
        @Override
        public String toString() {
            return String.format("EMA %d [Wert: %.4f, Preis: %.4f, Abstand: %.2f%%, Trend: %s]", 
                                 period, ema, currentPrice, distancePercent, getTrend());
        }
    }
    
    /**
     * Berechnet EMA mit Standard-Periode (200)
     */
    public static EMAResult getEMA(FusionApiClient client, String symbol, CandlestickInterval interval) {
        return getEMA(client, symbol, interval, DEFAULT_PERIOD);
    }
    
    /**
     * Berechnet EMA mit konfigurierbarer Periode.
     * 
     * @param client Binance API Client
     * @param symbol Handelssymbol (z.B. "LTCEUR")
     * @param interval Zeitintervall
     * @param period EMA-Periode (Standard: 200)
     */
    public static EMAResult getEMA(FusionApiClient client, String symbol, CandlestickInterval interval, int period) {
        return getEMA(client, symbol, interval, period, PriceType.CLOSE);
    }
    
    /**
     * Berechnet EMA mit konfigurierbarem Preistyp.
     * 
     * @param client Binance API Client
     * @param symbol Handelssymbol (z.B. "LTCEUR")
     * @param interval Zeitintervall
     * @param period EMA-Periode
     * @param priceType Preistyp (CLOSE, OPEN, HIGH, LOW, HL2, HLC3, OHLC4)
     */
    public static EMAResult getEMA(FusionApiClient client, String symbol, CandlestickInterval interval,
                                    int period, PriceType priceType) {
        int limit = Math.max(500, period + 100);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < period) {
            throw new IllegalArgumentException("Nicht genügend Daten für EMA-Berechnung. Benötigt: " + period);
        }

        return calculateEMA(candlesticks, period, priceType);
    }
    
    /**
     * Extrahiert den Preis basierend auf PriceType
     */
    private static double getPrice(Candlestick candle, PriceType priceType) {
        double open = Double.parseDouble(candle.getOpen());
        double high = Double.parseDouble(candle.getHigh());
        double low = Double.parseDouble(candle.getLow());
        double close = Double.parseDouble(candle.getClose());
        
        switch (priceType) {
            case OPEN:  return open;
            case HIGH:  return high;
            case LOW:   return low;
            case HL2:   return (high + low) / 2.0;
            case HLC3:  return (high + low + close) / 3.0;
            case OHLC4: return (open + high + low + close) / 4.0;
            case CLOSE:
            default:    return close;
        }
    }
    
    /**
     * Berechnet EMA nach der Standard-Formel.
     */
    private static EMAResult calculateEMA(List<Candlestick> candlesticks, int period) {
        return calculateEMA(candlesticks, period, PriceType.CLOSE);
    }
    
    /**
     * Berechnet EMA mit konfigurierbarem Preistyp.
     */
    private static EMAResult calculateEMA(List<Candlestick> candlesticks, int period, PriceType priceType) {
        int size = candlesticks.size();
        
        // Multiplier für EMA
        double multiplier = 2.0 / (period + 1);
        
        // Erster EMA = SMA der ersten 'period' Werte
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += getPrice(candlesticks.get(i), priceType);
        }
        double ema = sum / period;
        
        // Berechne EMA für restliche Kerzen
        for (int i = period; i < size; i++) {
            double price = getPrice(candlesticks.get(i), priceType);
            ema = (price - ema) * multiplier + ema;
        }
        
        // Aktueller Preis
        double currentPrice = getPrice(candlesticks.get(size - 1), priceType);
        
        // Runden
        ema = Math.round(ema * 10000.0) / 10000.0;
        
        return new EMAResult(ema, currentPrice, period);
    }
    
    /**
     * Gibt nur den EMA-Wert zurück
     */
    public static double getValue(FusionApiClient client, String symbol, CandlestickInterval interval) {
        return getEMA(client, symbol, interval).getEMA();
    }
    
    public static double getValue(FusionApiClient client, String symbol, CandlestickInterval interval, int period) {
        return getEMA(client, symbol, interval, period).getEMA();
    }
    
    /**
     * Gibt EMA-Wert mit konfigurierbarem Preistyp zurück.
     */
    public static double getValue(FusionApiClient client, String symbol, CandlestickInterval interval,
                                   int period, PriceType priceType) {
        return getEMA(client, symbol, interval, period, priceType).getEMA();
    }
    
    /**
     * Prüft auf EMA-Crossover zwischen zwei Perioden.
     * 
     * @param shortPeriod Kürzere EMA-Periode (z.B. 50)
     * @param longPeriod Längere EMA-Periode (z.B. 200)
     * @return 1 für Golden Cross (bullish), -1 für Death Cross (bearish), 0 für kein Crossover
     */
    public static int checkCrossover(FusionApiClient client, String symbol, CandlestickInterval interval,
                                     int shortPeriod, int longPeriod) {
        int limit = Math.max(500, longPeriod + 100);
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        
        if (candlesticks == null || candlesticks.size() < longPeriod + 1) {
            return 0;
        }
        
        // Aktuelle EMAs
        double shortEMA = calculateEMA(candlesticks, shortPeriod).getEMA();
        double longEMA = calculateEMA(candlesticks, longPeriod).getEMA();
        
        // Vorherige EMAs (ohne letzte Kerze)
        List<Candlestick> prevCandles = candlesticks.subList(0, candlesticks.size() - 1);
        double prevShortEMA = calculateEMA(prevCandles, shortPeriod).getEMA();
        double prevLongEMA = calculateEMA(prevCandles, longPeriod).getEMA();
        
        // Golden Cross: Short EMA kreuzt Long EMA von unten
        if (prevShortEMA <= prevLongEMA && shortEMA > longEMA) {
            return 1;
        }
        // Death Cross: Short EMA kreuzt Long EMA von oben
        if (prevShortEMA >= prevLongEMA && shortEMA < longEMA) {
            return -1;
        }
        
        return 0;
    }
    
    /**
     * Prüft auf Golden Cross (EMA 50 kreuzt EMA 200 von unten)
     */
    public static boolean isGoldenCross(FusionApiClient client, String symbol, CandlestickInterval interval) {
        return checkCrossover(client, symbol, interval, 50, 200) == 1;
    }
    
    /**
     * Prüft auf Death Cross (EMA 50 kreuzt EMA 200 von oben)
     */
    public static boolean isDeathCross(FusionApiClient client, String symbol, CandlestickInterval interval) {
        return checkCrossover(client, symbol, interval, 50, 200) == -1;
    }
    
    /**
     * Generiert Trading-Signal basierend auf EMA-Analyse
     */
    public static String getTradingSignal(FusionApiClient client, String symbol, CandlestickInterval interval) {
        try {
            EMAResult ema200 = getEMA(client, symbol, interval, 200);
            int crossover = checkCrossover(client, symbol, interval, 50, 200);
            
            // Golden/Death Cross Signale
            if (crossover == 1) return "STRONG_BUY";
            if (crossover == -1) return "STRONG_SELL";
            
            // Position relativ zum EMA 200
            if (ema200.isPriceAboveEMA() && ema200.getDistancePercent() > 5) return "BUY";
            if (ema200.isPriceBelowEMA() && ema200.getDistancePercent() < -5) return "SELL";
            
            return "HOLD";
        } catch (Exception e) {
            return "HOLD";
        }
    }
    
    /**
     * Hilfsmethode für EMA-Ausgabe
     */
    public static void printEMA(FusionApiClient client, String symbol, CandlestickInterval interval) {
        printEMA(client, symbol, interval, DEFAULT_PERIOD);
    }
    
    public static void printEMA(FusionApiClient client, String symbol, CandlestickInterval interval, int period) {
        try {
            EMAResult result = getEMA(client, symbol, interval, period);
            
            System.out.println("════════════════════════════════════════");
            System.out.println("EMA Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            System.out.printf("EMA %d:  %.4f%n", period, result.getEMA());
            System.out.printf("Preis:   %.4f%n", result.getCurrentPrice());
            System.out.printf("Abstand: %.2f%%%n", result.getDistancePercent());
            System.out.println("────────────────────────────────────────");
            
            if (result.isPriceAboveEMA()) {
                System.out.println("📈 BULLISH - Preis über EMA " + period);
            } else {
                System.out.println("📉 BEARISH - Preis unter EMA " + period);
            }
            
            if (result.isNearEMA(1.0)) {
                System.out.println("⚠ Preis nahe am EMA (mögliche Support/Resistance)");
            }
            
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei EMA-Berechnung: " + e.getMessage());
        }
    }
    
    /**
     * Ausgabe mit mehreren EMAs (z.B. 50, 100, 200)
     */
    public static void printMultipleEMA(FusionApiClient client, String symbol, CandlestickInterval interval, int... periods) {
        try {
            System.out.println("════════════════════════════════════════");
            System.out.println("EMA Analyse für " + symbol + " (" + interval + "):");
            System.out.println("════════════════════════════════════════");
            
            double currentPrice = 0;
            for (int period : periods) {
                EMAResult result = getEMA(client, symbol, interval, period);
                currentPrice = result.getCurrentPrice();
                String position = result.isPriceAboveEMA() ? "↑" : "↓";
                System.out.printf("EMA %3d: %.4f %s (%.2f%%)%n", 
                                  period, result.getEMA(), position, result.getDistancePercent());
            }
            
            System.out.println("────────────────────────────────────────");
            System.out.printf("Aktueller Preis: %.4f%n", currentPrice);
            
            // Crossover Check für 50/200
            if (periods.length >= 2) {
                int crossover = checkCrossover(client, symbol, interval, 50, 200);
                if (crossover == 1) System.out.println("⭐ GOLDEN CROSS! EMA 50 > EMA 200");
                else if (crossover == -1) System.out.println("☠ DEATH CROSS! EMA 50 < EMA 200");
            }
            
            System.out.println("════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("Fehler bei EMA-Berechnung: " + e.getMessage());
        }
    }
}
