package com.binance.api.tradingbot.Indicator;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import org.ta4j.core.*;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * RSI Klasse, die die Ta4j-Bibliothek verwendet, um präzise RSI-Berechnungen durchzuführen
 */
public class RSI {
    
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

