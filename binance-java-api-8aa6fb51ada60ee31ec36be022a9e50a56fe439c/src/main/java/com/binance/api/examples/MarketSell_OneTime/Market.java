package com.binance.api.examples.MarketSell_OneTime;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.examples.SellOrderProcess.SellOrderProcess;
import java.time.LocalDateTime;

public class Market {

    private static int ProcessedDay = -1;

    public static void SellOneTimePerDay(BinanceApiRestClient client, final String POS, final String HIST) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();
        int currentDay = now.getDayOfYear();

        if (currentMinute == 0 && currentHour == 22 && currentDay != ProcessedDay) {

          SellOrderProcess.handleSellProcess(POS, HIST, client);

            ProcessedDay = currentDay;
            System.out.println("Handel abgeschlossen um: " + now);
        }
    }   
}