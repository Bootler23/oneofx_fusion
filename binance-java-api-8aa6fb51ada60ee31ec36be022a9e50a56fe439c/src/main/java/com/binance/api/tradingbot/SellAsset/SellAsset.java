package com.binance.api.tradingbot.SellAsset;

import java.time.LocalDateTime;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.bnb;

import com.binance.api.client.domain.account.NewOrderResponse;

import static com.binance.api.client.domain.account.NewOrder.marketSell;

public class SellAsset {

    private static int ProcessedHour = -1;
    private static double GlobalBuyAmount = 5.5;

    public static void Three_TimesPerDay(String currency, BinanceApiRestClient client) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // Handel nur um 0:00, 6:00 und 14:00 Uhr
        if (currentMinute == 0 && (currentHour == 22 || currentHour == 6 || currentHour == 14)
                && currentHour != ProcessedHour) {

            System.out.println("Handel wird durchgeführt. Aktuelle Zeit: " + now);     
            
            //SellOrderProcess.handleSellProcess(currency, client);

            Sell_Asset_with_Qty_0_0_Double_Amount(bnb.getClient(), "XRPEUR");

            ProcessedHour = currentHour;
            System.out.println("Handel abgeschlossen um: " + now);
        }
    }

    private static void Sell_Asset_with_Integer_Amount(BinanceApiRestClient client, String CurrencyPair) {
        try {
            int qty = (int) Math.round(GlobalBuyAmount / Ticker.getAssetPrice(CurrencyPair, client));
            String qtyStr = String.valueOf(qty);

            client.newOrder(marketSell(CurrencyPair, qtyStr));
            System.out.println("Verkauf von " + qtyStr + " " + CurrencyPair + " zu " + GlobalBuyAmount + " EUR");

        } catch (BinanceApiException ex) {
            String FehlerMessage = CurrencyPair + " - Fehler beim Verkauf: BuyAmount wurde angepasst!";
            GlobalBuyAmount = GlobalBuyAmount + 0.1;
            Sell_Asset_with_Integer_Amount(client, CurrencyPair);
            System.out.println(FehlerMessage);
        }
    }

    public static void Sell_Asset_with_Qty_0_0_Double_Amount(BinanceApiRestClient client, String CurrencyPair) {
        try {
            double qty = round.one(GlobalBuyAmount / Ticker.getAssetPrice(CurrencyPair, client));
            String qtyStr = String.valueOf(qty);

            NewOrderResponse orderResponse = client.newOrder(marketSell(CurrencyPair, qtyStr));         

            SETSQL.setReserve(SETSQL.getReserve() + Double.valueOf(orderResponse.getCummulativeQuoteQty()));

            System.out.println("Verkauf von " + orderResponse.getCummulativeQuoteQty() + " EUR " + CurrencyPair);

        } catch (BinanceApiException ex) {
            String FehlerMessage = CurrencyPair + " - Fehler beim Verkauf: BuyAmount wurde angepasst!";
            GlobalBuyAmount = GlobalBuyAmount + 0.1;
            Sell_Asset_with_Qty_0_0_Double_Amount(client, CurrencyPair);
            System.out.println(FehlerMessage);
        }
    }
}
