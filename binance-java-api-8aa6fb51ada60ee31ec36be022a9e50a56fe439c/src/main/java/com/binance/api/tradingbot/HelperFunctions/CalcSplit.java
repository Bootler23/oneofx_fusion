package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class CalcSplit {

    public static double calcROI(String currency, double GewinnAfterTax) {
        return GewinnAfterTax * (SETSQL.getROIpercent() / 100);
    }
}
