package com.binance.api.tradingbot.Indicator;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class Update {

    public static void NewCounterPosition() {
        int count = SETSQL.getCount();
        count++;
        SETSQL.updateCount(count);       
    }

    public static void addminBuyAmount() {
        double minBuyAmount = SETSQL.getminBuyAmount();
        SETSQL.setminBuyAmount(minBuyAmount + (minBuyAmount * (0.007/100)));
    }
}