package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.POSSQL;

public class CalcSplit {

    public static double calcSplitValue(String currency, double GewinnAfterTax) {

        double split = 0;
        int countPosition = POSSQL.getCountPOS(currency);
        // hier soll jetzt 1 Position für 1% stehen das heißt 1% entspricht 0.01
        if (countPosition > 0) {
            split = GewinnAfterTax * (0.01 * countPosition);
            if (split > GewinnAfterTax) {
                split = (GewinnAfterTax * 0.99);
            }
        } else {
            split = GewinnAfterTax * 0.01;
        }
        return split;
    }    
}
