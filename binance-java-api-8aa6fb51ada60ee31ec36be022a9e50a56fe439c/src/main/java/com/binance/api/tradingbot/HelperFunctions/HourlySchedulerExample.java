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

    public static void executeHourly() {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (currentMinute == 0 && currentHour != ProcessedHour) {

            System.out.println("Stündliche Aufgabe wird ausgeführt. Aktuelle Zeit: " + now);
            
            // Bestehende Trading-Logik
            SellAsset.Sell_Asset_with_Qty_0_0_Double_Amount(bnb.getClient(), "XRPEUR");

            
            
            // NEUER CODE: Git-Backup zur vollen Stunde
            // System.out.println("🔄 Starte stündliches Git-Backup...");
            // GitBackup.performHourlyBackup();
            
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
