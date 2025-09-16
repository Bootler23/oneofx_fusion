package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.POSSQL;

public class CalcPercenToSell {

    public static double PercentToSell(String currency) {

        int positionCount = POSSQL.getCountPOS(currency);

        if (positionCount >= 100) {
            return 0.50;
        } else if (positionCount <= 1) {
            return 1.20;
        } else {

            return round.two(1.20 - ((positionCount - 1) * 0.7 / 99.0));
        }
    }
}
