package com.binance.api.tradingbot.SellAsset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;

import com.binance.api.client.domain.account.NewOrderResponse;

import static com.binance.api.client.domain.account.NewOrder.marketSell;

public class SellAsset {

    private static int ProcessedHour = -1;

    private static double GlobalBuyAmount = 5.5;
    
    // Konstanten für Retry-Mechanismus
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final double BUY_AMOUNT_INCREMENT = 0.1;
    private static final long RETRY_DELAY_MS = 1000; // 1 Sekunde

    public static void Three_TimesPerDay(String currency, BinanceApiRestClient client) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // Handel nur um 0:00, 6:00 und 14:00 Uhr
        if (currentMinute == 0 && (currentHour == 22 || currentHour == 6 || currentHour == 14)
                && currentHour != ProcessedHour) {

            System.out.println("Handel wird durchgeführt. Aktuelle Zeit: " + now);

            // SellOrderProcess.handleSellProcess(currency, client);

            Sell_Asset_with_Qty_OnePoint(bnb.getClient(), "XRPEUR");

            ProcessedHour = currentHour;
            System.out.println("Handel abgeschlossen um: " + now);
        }
    }

    public static void Sell_Asset_with_Integer_Amount(BinanceApiRestClient client, String CurrencyPair) {
        Sell_Asset_with_Integer_Amount(client, CurrencyPair, GlobalBuyAmount, 0);
    }
    
    private static void Sell_Asset_with_Integer_Amount(BinanceApiRestClient client, String CurrencyPair, 
                                                       double buyAmount, int retryCount) {
        try {
            int qty = (int) Math.round(buyAmount / Ticker.getAssetPrice(CurrencyPair, client));
            String qtyStr = String.valueOf(qty);

            NewOrderResponse orderResponse = client.newOrder(marketSell(CurrencyPair, qtyStr));
            SETSQL.setReserve(SETSQL.getReserve() + Double.valueOf(orderResponse.getCummulativeQuoteQty()));
            System.out.println("Verkauf von " + orderResponse.getCummulativeQuoteQty() + " EUR " + CurrencyPair);

        } catch (BinanceApiException ex) {
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                String errorMsg = String.format(
                    "%s - KRITISCHER FEHLER: Verkauf nach %d Versuchen fehlgeschlagen! " +
                    "Letzte BuyAmount: %.2f, Fehler: %s (Code: %s)",
                    CurrencyPair, retryCount, buyAmount, 
                    ex.getError().getMsg(), ex.getError().getCode()
                );
                System.err.println(errorMsg);
                return;
            }
            
            double newBuyAmount = buyAmount + BUY_AMOUNT_INCREMENT;
            System.out.println(String.format(
                "%s - Verkauf fehlgeschlagen (Versuch %d/%d). BuyAmount angepasst: %.2f -> %.2f. Fehler: %s",
                CurrencyPair, retryCount + 1, MAX_RETRY_ATTEMPTS, buyAmount, newBuyAmount, ex.getError().getMsg()
            ));
            
            // Kurze Pause vor erneutem Versuch
            try {
                Thread.sleep(RETRY_DELAY_MS * (retryCount + 1)); // Exponential backoff
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("Retry-Prozess wurde unterbrochen");
                return;
            }
            
            Sell_Asset_with_Integer_Amount(client, CurrencyPair, newBuyAmount, retryCount + 1);
        }
    }

    public static void Sell_Asset_with_Qty_OnePoint(BinanceApiRestClient client, String CurrencyPair) {
        Sell_Asset_with_Qty_OnePoint(client, CurrencyPair, 13.0, 0);
    }
    
    private static void Sell_Asset_with_Qty_OnePoint(BinanceApiRestClient client, String CurrencyPair, 
                                                      double buyAmount, int retryCount) {
        try {
            double qty = round.one(buyAmount / Ticker.getAssetPrice(CurrencyPair, client));
            String qtyStr = String.valueOf(qty);

            NewOrderResponse orderResponse = client.newOrder(marketSell(CurrencyPair, qtyStr));
            SETSQL.setReserve(SETSQL.getReserve() + Double.valueOf(orderResponse.getCummulativeQuoteQty()));
            System.out.println("Verkauf von " + orderResponse.getCummulativeQuoteQty() + " EUR " + CurrencyPair);

        } catch (BinanceApiException ex) {
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                String errorMsg = String.format(
                    "%s - KRITISCHER FEHLER: Verkauf nach %d Versuchen fehlgeschlagen! " +
                    "Letzte BuyAmount: %.2f, Fehler: %s (Code: %s)",
                    CurrencyPair, retryCount, buyAmount, 
                    ex.getError().getMsg(), ex.getError().getCode()
                );
                System.err.println(errorMsg);
                return;
            }
            
            double newBuyAmount = buyAmount + BUY_AMOUNT_INCREMENT;
            System.out.println(String.format(
                "%s - Verkauf fehlgeschlagen (Versuch %d/%d). BuyAmount angepasst: %.2f -> %.2f. Fehler: %s",
                CurrencyPair, retryCount + 1, MAX_RETRY_ATTEMPTS, buyAmount, newBuyAmount, ex.getError().getMsg()
            ));
            
            // Kurze Pause vor erneutem Versuch
            try {
                Thread.sleep(RETRY_DELAY_MS * (retryCount + 1)); // Exponential backoff
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("Retry-Prozess wurde unterbrochen");
                return;
            }
            
            Sell_Asset_with_Qty_OnePoint(client, CurrencyPair, newBuyAmount, retryCount + 1);
        }
    }
    
     public static void Sell_Asset_BTCEUR(BinanceApiRestClient client, String CurrencyPair) {
        Sell_Asset_BTCEUR(client, CurrencyPair, GlobalBuyAmount, 0);
    }
    
    private static void Sell_Asset_BTCEUR(BinanceApiRestClient client, String CurrencyPair, 
                                          double buyAmount, int retryCount) {
        try {
            double qty = round.eight(buyAmount / Ticker.getAssetPrice(CurrencyPair, client));
            String qtyStr = BuyOrderPocess.getQtySimple(CurrencyPair, qty);
          
            NewOrderResponse orderResponse = client.newOrder(marketSell(CurrencyPair, qtyStr));
            SETSQL.setReserve(SETSQL.getReserve() + Double.valueOf(orderResponse.getCummulativeQuoteQty()));
            System.out.println("Verkauf von " + orderResponse.getCummulativeQuoteQty() + " EUR " + CurrencyPair);

        } catch (BinanceApiException ex) {
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                String errorMsg = String.format(
                    "%s - KRITISCHER FEHLER: Verkauf nach %d Versuchen fehlgeschlagen! " +
                    "Letzte BuyAmount: %.2f, Fehler: %s (Code: %s)",
                    CurrencyPair, retryCount, buyAmount, 
                    ex.getError().getMsg(), ex.getError().getCode()
                );
                System.err.println(errorMsg);
                return;
            }
            
            double newBuyAmount = buyAmount + BUY_AMOUNT_INCREMENT;
            System.out.println(String.format(
                "%s - Verkauf fehlgeschlagen (Versuch %d/%d). BuyAmount angepasst: %.2f -> %.2f. Fehler: %s",
                CurrencyPair, retryCount + 1, MAX_RETRY_ATTEMPTS, buyAmount, newBuyAmount, ex.getError().getMsg()
            ));
            
            // Kurze Pause vor erneutem Versuch
            try {
                Thread.sleep(RETRY_DELAY_MS * (retryCount + 1)); // Exponential backoff
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("Retry-Prozess wurde unterbrochen");
                return;
            }
            
            Sell_Asset_BTCEUR(client, CurrencyPair, newBuyAmount, retryCount + 1);
        }
    } 
}
