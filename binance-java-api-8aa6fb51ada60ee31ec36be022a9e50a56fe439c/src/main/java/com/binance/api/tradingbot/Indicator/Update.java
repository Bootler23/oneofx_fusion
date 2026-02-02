package com.binance.api.tradingbot.Indicator;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class Update {

    double minBuyAmount = SETSQL.getminBuyAmount();
    double percentToAdd = SETSQL.getPercentToAdd();

    public static void NewCounterPosition() {
        int count = SETSQL.getCount();
        count++;
        SETSQL.updateCount(count);
    }

    public static void addminBuyAmount() {
        double minBuyAmount = SETSQL.getminBuyAmount();
        double percentToAdd = SETSQL.getPercentToAdd();

        if (percentToAdd <= 0) {
            percentToAdd = 0.01;
        }

        SETSQL.setminBuyAmount(minBuyAmount + (minBuyAmount * (percentToAdd / 100)));
    }
}