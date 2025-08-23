package com.binance.api.tradingbot.HelperFunctions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;

public class HourlySchedulerExample {

    private static int ProcessedHour = -1;

    public static void executeHourly(String currencyPair, BinanceApiRestClient client) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (currentMinute == 0 && currentHour != ProcessedHour) {

            System.out.println("Stündliche Aufgabe wird ausgeführt. Aktuelle Zeit: " + now);

            checkBuyAmount(currencyPair, client);

            ProcessedHour = currentHour;
            System.out.println("Stündliche Aufgabe abgeschlossen um: " + now);
        }
    }

    private static void checkBuyAmount(String currencyPair, BinanceApiRestClient client) {
        List<Double> LivePrice = new ArrayList<Double>();
        LivePrice.add(Ticker.getAssetPrice(currencyPair, client));
        try {
            System.out.println("=== Führe stündliche Überprüfungen durch ===");

            double value = (BuyAmountFunktion.getBuyAmount(currencyPair, "EUR", client, LivePrice, true));
            if (value <= 6.0) {
                System.out.println("=== 10 € werden freigegeben ===");
            } else {
                System.out.println("=== 10 € werden nicht freigegeben ===");
            }

        } catch (Exception e) {
            System.err.println("Fehler bei der stündlichen Aufgabe: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
