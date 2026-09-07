package com.oneofx.fusion.tradingbot.Indicator;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;
import org.ta4j.core.*;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * CCI Klasse zur Berechnung des Commodity Channel Index (CCI)
 */
public class CCI {
    /**
     * Berechnet den CCI (Commodity Channel Index) für ein bestimmtes Symbol
     * @param client Der Binance API Client
     * @param symbol Das Handelssymbol (z.B. "LTCEUR")
     * @param interval Das Zeitintervall für die Kerzen
     * @param cciPeriod Die Periode für die CCI-Berechnung (typisch 20)
     * @return Der aktuelle CCI-Wert
     */
    public static double getCCI(FusionApiClient client, String symbol, CandlestickInterval interval, int cciPeriod) {
        int limit = cciPeriod * 3;
        List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, limit, null, null);
        if (candlesticks == null || candlesticks.size() < cciPeriod + 1) {
            throw new IllegalArgumentException("Nicht genügend Daten für CCI-Berechnung");
        }
        BarSeries series = convertBinanceCandlestickToBarSeries(candlesticks);
        CCIIndicator cci = new CCIIndicator(series, cciPeriod);
        double value = cci.getValue(series.getEndIndex()).doubleValue();
        return Math.round(value * 100.0) / 100.0;
    }

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
     * Überprüft ob der CCI überkauft ist (z.B. > +100)
     */
    public static boolean isOverbought(double cci) {
        return cci > 100;
    }

    /**
     * Überprüft ob der CCI überverkauft ist (z.B. < -100)
     */
    public static boolean isOversold(double cci) {
        return cci < -100;
    }

    
}
