package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.POSSQL;

public class CalcPercenToSell {

    public static double PercentToSell(String currency) {

        int positionCount = POSSQL.getCountPOS(currency);

        if (positionCount >= 100) {
            return 0.42;
        } else if (positionCount <= 1) {
            return 0.73;
        } else {

            return round.two(0.73 - ((positionCount - 1) * 0.31 / 99.0));
        }
    }
}
