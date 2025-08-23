package com.binance.api.tradingbot.MarketSell_OneTime;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;

import java.time.LocalDateTime;

public class Market {

    private static int ProcessedDay = -1;

    public static void SellOneTimePerDay(BinanceApiRestClient client, String currencyPair) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();
        int currentDay = now.getDayOfYear();

        if (currentMinute == 0 && currentHour == 22 && currentDay != ProcessedDay) {

          SellOrderProcess.handleSellProcess(currencyPair, client);

            ProcessedDay = currentDay;
            System.out.println("Handel abgeschlossen um: " + now);
        }
    }   
}