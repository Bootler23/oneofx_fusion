package com.binance.api.tradingbot.Reserve;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class Reserve {

    public static void addReserve(double amount) {

        SETSQL.setReserve(SETSQL.getReserve() + amount);

        System.out.println("Reservebetrag wurde um " + amount + " EUR erhöht.");
    }
}
