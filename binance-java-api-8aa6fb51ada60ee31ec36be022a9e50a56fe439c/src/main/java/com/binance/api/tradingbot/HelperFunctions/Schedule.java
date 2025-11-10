package com.binance.api.tradingbot.HelperFunctions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.DCA.dca;
import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SellAsset.SellAsset;
import com.binance.api.tradingbot.Settings.bnb;

public class Schedule {

    private static int ProcessedHour = -1;
    private static int ProcessedMinute = -1; // Neue Variable für Minuten-Tracking
    private static long lastExecutionEpoch = 0; // Für sekundenbasierte Verkäufe (Epochen-Zeit)

    public static void executeHourly() {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (currentMinute == 0 && currentHour != ProcessedHour) {

            System.out.println("Stündliche Aufgabe wird ausgeführt. Aktuelle Zeit: " + now);

            // Bestehende Trading-Logik
            SellAsset.Sell_Asset_with_Integer_Amount(bnb.getClient(), "XLMEUR");
            SellAsset.Sell_Asset_with_Integer_Amount(bnb.getClient(), "TRXEUR");
            SellAsset.Sell_Asset_with_Integer_Amount(bnb.getClient(), "ALGOUSDC");

            ProcessedHour = currentHour;
            System.out.println("Stündliche Aufgabe abgeschlossen um: " + now);
        }
    }

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

    public static void Sell_Every_X_Seconds(String currency, int intervalSeconds) {
        long currentEpoch = System.currentTimeMillis() / 1000;     

        if (currentEpoch - lastExecutionEpoch >= intervalSeconds) {
            dca.executeDCA(currency);
            lastExecutionEpoch = currentEpoch;    
        }
    }
}