package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Indicator.MACD;

public class CalcSplit {

    public static double calcSplitValue(String currency, double GewinnAfterTax) {

        double BalanceExchange = SETSQL.getBalance_SQL();
        double BalancePosition = POSSQL.getSumBuyAmount();
        double split = 0;
        int countPosition = POSSQL.getCountPOS(currency);

        if (BalanceExchange == 0 || BalancePosition == 0) {
            return 0;
        } else {

            // hier soll jetzt 1 Position für 1% stehen das heißt 1% entspricht 0.01
            if (countPosition > 0) {
                split = GewinnAfterTax * (0.01 * countPosition);
                if (split > GewinnAfterTax) {
                    split = (GewinnAfterTax * 0.99);
                }
            }          
            return split;
        }    
    }  
}
