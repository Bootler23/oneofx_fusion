package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Indicator.MACD;

public class CalcSplit {

    public static double calcSplitValue(double GewinnAfterTax) {

        double BalanceExchange = SETSQL.getBalance_SQL();
        double BalancePosition = POSSQL.getSumBuyAmount();
        double split = 0;

        if (BalanceExchange == 0 || BalancePosition == 0) {
            return 0;
        } else {
            split = ((BalancePosition / BalanceExchange) * GewinnAfterTax);
            if (split > GewinnAfterTax) {
                split = (GewinnAfterTax * 0.99);
            } else {
                split = (GewinnAfterTax * 0.80);
            }
            return split;
        }    
    }

    
}
