package com.binance.api.tradingbot.Strategie;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

/**
 * Service für Trading-Strategien basierend auf technischen Indikatoren.
 */
public class StrategieService {

    /**
     * Führt die RSI-basierte Strategie aus.
     */
    public static void executeRsiStrategy(double rsi) {

        if (rsi < 45) {
            SETSQL.setRSI(true);
        }
    }
}
