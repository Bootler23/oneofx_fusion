package com.binance.api.tradingbot.HelperFunctions;

import java.time.LocalDateTime;

import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.Settings.set;

public class HourlySchedulerExample {

    private static int ProcessedHour = -1;

    public static void executeHourly(String currencyPair) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (currentMinute == 0 && currentHour != ProcessedHour) {
            
            System.out.println("Stündliche Aufgabe wird ausgeführt. Aktuelle Zeit: " + now);
            
            checkBuyAmount(currencyPair);
            
            ProcessedHour = currentHour;
            System.out.println("Stündliche Aufgabe abgeschlossen um: " + now);
        }
    }

    private static void checkBuyAmount(String currencyPair) {
        try {
            System.out.println("=== Führe stündliche Überprüfungen durch ===");

            double value = (BuyAmountFunktion.getBuyAmount(currencyPair, null, set.getGridforCurrency(currencyPair), null, "ATH", "POS", "SET", null, true));
            if (value <= 6.0) {         
                System.out.println("=== 10 € werden freigegeben ===");
            }
 
        } catch (Exception e) {
            System.err.println("Fehler bei der stündlichen Aufgabe: " + e.getMessage());
            e.printStackTrace();
        }
    }   
}
