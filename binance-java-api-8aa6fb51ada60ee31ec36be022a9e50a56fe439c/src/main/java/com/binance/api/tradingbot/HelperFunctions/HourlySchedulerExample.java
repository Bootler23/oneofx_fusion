package com.binance.api.tradingbot.HelperFunctions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.SellAsset.SellAsset;
import com.binance.api.tradingbot.Settings.bnb;

public class HourlySchedulerExample {

    private static int ProcessedHour = -1;
    private static int ProcessedMinute = -1; // Neue Variable für Minuten-Tracking

    public static void executeHourly() {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (currentMinute == 0 && currentHour != ProcessedHour) {

            System.out.println("Stündliche Aufgabe wird ausgeführt. Aktuelle Zeit: " + now);

            // Bestehende Trading-Logik
            SellAsset.Sell_Asset_with_Integer_Amount(bnb.getClient(), "XLMEUR");          
            SellAsset.Sell_Asset_BTCEUR(bnb.getClient(), "BTCEUR");

            ProcessedHour = currentHour;
            System.out.println("Stündliche Aufgabe abgeschlossen um: " + now);
        }
    }

    // private static void checkBuyAmount(String currencyPair, BinanceApiRestClient client) {
    //     List<Double> LivePrice = new ArrayList<Double>();
    //     LivePrice.add(Ticker.getAssetPrice(currencyPair, client));
    //     try {
    //         System.out.println("=== Führe stündliche Überprüfungen durch ===");

    //         double value = (BuyAmountFunktion.getBuyAmount(currencyPair, client, LivePrice, true));
    //         if (value <= 6.0) {
    //             System.out.println("=== 10 € werden freigegeben ===");
    //         } else {
    //             System.out.println("=== 10 € werden nicht freigegeben ===");
    //         }

    //     } catch (Exception e) {
    //         System.err.println("Fehler bei der stündlichen Aufgabe: " + e.getMessage());
    //         e.printStackTrace();
    //     }
    // }

    public static void Sell_Every_10_Minutes() {
        LocalDateTime now = LocalDateTime.now();
        int currentMinute = now.getMinute();

        // Verkaufe nur bei Minute 0, 10, 20, 30, 40, 50
        if (currentMinute % 10 == 0 && currentMinute != ProcessedMinute) {

            System.out.println("10-Minuten-Verkauf wird durchgeführt. Aktuelle Zeit: " + now);

            SellAsset.Sell_Asset_with_Qty_OnePoint(bnb.getClient(), "XRPEUR");

            ProcessedMinute = currentMinute;
            System.out.println("10-Minuten-Verkauf abgeschlossen um: " + now);
        }
    }
}
