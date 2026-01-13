package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class CalcSplit {

    public static double calcROI(String currency, double GewinnAfterTax) {

        int positionCount = SETSQL.getCount();



        return GewinnAfterTax * (SETSQL.getROIpercent() / 100); // TODO hier sollte es Postionsabhängig gemacht werden




    }
}
