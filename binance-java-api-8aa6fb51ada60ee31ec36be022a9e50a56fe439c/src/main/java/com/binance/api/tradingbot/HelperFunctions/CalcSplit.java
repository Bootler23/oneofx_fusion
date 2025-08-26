package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class CalcSplit {

    public static double calcSplitValue(double GewinnAfterTax) {

        double BalanceExchange = SETSQL.getBalance_SQL();
        double BalancePosition = POSSQL.getSumBuyAmount();

        if (BalanceExchange == 0 || BalancePosition == 0) {
            return 0;
        } else {
            double split = ((BalancePosition / BalanceExchange) * GewinnAfterTax);
            if (split > GewinnAfterTax) {
                split = (GewinnAfterTax * 0.8);
            }
            return split;
        }
    }
}
