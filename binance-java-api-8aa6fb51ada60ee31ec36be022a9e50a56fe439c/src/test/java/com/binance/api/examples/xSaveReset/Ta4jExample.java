package com.binance.api.examples.xSaveReset;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.examples.HelperFunctions.round;

import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Beispielklasse für die Verwendung der Ta4j-Bibliothek zur technischen Analyse
 */
public class Ta4jExample {

    /**
     * Berechnet MACD mit Ta4j und gibt die Werte zurück
     * 
     * @param client Binance API Client
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @param interval Kerzenintervall
     */
    public static void calculateMACD(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        // 1. Holen der Kerzendaten von Binance
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, 500, null, null);
        
        // 2. Umwandlung in Ta4j-kompatible Zeitreihe
        BarSeries series = convertToBarSeries(candlesticks, symbol);
        
        // 3. Erstellen des MACD-Indikators (12, 26, 9)
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator signalLine = new EMAIndicator(macd, 9);
        
        // 4. Aktuellste Werte abrufen
        int lastIndex = series.getEndIndex();
        double macdValue = macd.getValue(lastIndex).doubleValue();
        double signalValue = signalLine.getValue(lastIndex).doubleValue();
        double histogram = macdValue - signalValue;
        
        // 5. Ausgabe der Werte (in China als DIF, DEA, MACD bezeichnet)
        System.out.println("Ta4j MACD Analyse für " + symbol + ":");
        System.out.println("DIF (MACD-Linie): " + round.three(macdValue));
        System.out.println("DEA (Signal-Linie): " + round.three(signalValue));
        System.out.println("MACD (Histogramm): " + round.three(histogram));
        
        // 6. Trading-Signal basierend auf MACD-Kreuzung
        if (macdValue > signalValue) {
            System.out.println("Signal: BUY (MACD über Signal-Linie)");
        } else {
            System.out.println("Signal: SELL (MACD unter Signal-Linie)");
        }
        
        // 7. Überprüfen auf Kreuzungen
        double previousMacdValue = macd.getValue(lastIndex - 1).doubleValue();
        double previousSignalValue = signalLine.getValue(lastIndex - 1).doubleValue();
        
        if (previousMacdValue < previousSignalValue && macdValue > signalValue) {
            System.out.println("⭐ BULLISH CROSSOVER DETECTED! Starkes Kaufsignal!");
        } else if (previousMacdValue > previousSignalValue && macdValue < signalValue) {
            System.out.println("⚠ BEARISH CROSSOVER DETECTED! Starkes Verkaufssignal!");
        }
    }
    
    /**
     * Konvertiert Binance-Candlesticks in Ta4j-kompatible BarSeries
     */
    private static BarSeries convertToBarSeries(List<Candlestick> candlesticks, String name) {
        BarSeries series = new BaseBarSeries(name);
        
        for (Candlestick candle : candlesticks) {
            ZonedDateTime time = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(candle.getOpenTime()), 
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
     * Verwende diese Methode zum Aufruf der Ta4j MACD-Analyse
     */
    public static void showMACD(BinanceApiRestClient client, String symbol) {
        calculateMACD(client, symbol, CandlestickInterval.FIVE_MINUTES);
    }
}
