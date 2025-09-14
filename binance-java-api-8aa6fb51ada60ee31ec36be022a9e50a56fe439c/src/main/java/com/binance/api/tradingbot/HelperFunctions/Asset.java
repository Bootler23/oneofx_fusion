package com.binance.api.tradingbot.HelperFunctions;

import static com.binance.api.client.domain.account.NewOrder.marketBuy;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;

public class Asset {

    public static double getFree_Balance(String currency, BinanceApiRestClient client) {
        try {
            double balance = round.two(Double.valueOf(client.getAccount().getAssetBalance(currency).getFree()));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (BinanceApiException e) {
            System.out.println("Fehler: Live - Balance des Binance-API nicht möglich.");
            return 0.0;
        }
    }

    public static double getLocked_Balance(String currency, BinanceApiRestClient client) {
        try {
            double balance = round.two(Double.valueOf(client.getAccount().getAssetBalance(currency).getLocked()));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (BinanceApiException e) {
            System.out.println("Fehler: Live - Balance des Binance-API nicht möglich.");
            return 0.0;
        }
    }

    public static double getFreeCalced_Balance(String currency, BinanceApiRestClient client) {
        try {
            double balance = round.two(getFree_Balance(currency, client) - getLocked_Balance(currency, client));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (BinanceApiException e) {
            System.out.println("Fehler: Live - Balance des Binance-API nicht möglich.");
            return 0.0;
        }
    }

    public static double getBNB_Balance(String currencyPeer, String currency, BinanceApiRestClient client) {
        try {
            double bnbbalance = Double.valueOf(client.getAccount().getAssetBalance(currency).getFree());
            double bnbeuro = Double.valueOf(client.getPrice(currencyPeer).getPrice());
            double returnvalue = round.five(bnbeuro * bnbbalance);
            System.out.println(); // Zeilenumbruch vor der Balance-Ausgabe
            System.out.println("BNB Balance: " + returnvalue + " EUR");

            if ((bnbeuro * bnbbalance) < 5.0) {

                System.out.println("BNB unter 5 Euro -> Bitte Nachkaufen!");

                Buy_BNB(client);

            }
            return returnvalue;

        } catch (BinanceApiException e) {
            System.out.println("Fehler beim Abrufen der freien Balance: " + e.getMessage());
            return 0.0;
        } catch (Exception e) {
            System.out.println("Unbekannter Fehler: " + e.getMessage());
            return 0.0;
        }
    }

    private static void Buy_BNB(BinanceApiRestClient client) {

        double Qty = RoundCurrency.forQuantity((5.5/Ticker.getAssetPrice("BNBEUR", client)), "BNBEUR");
        String Quantity = String.valueOf(Qty);

        try {
            NewOrderResponse newOrderResponse = client.newOrder(marketBuy("BNBEUR", Quantity));

            System.out.println("BNB Nachkauf erfolgreich! " + newOrderResponse.getOrigQty());

        } catch (BinanceApiException dex) {
            System.err.println("Fehler beim Verkauf: Keine Menge für den Verkauf verfügbar!");
            sleep.for_10_seconds();
        } catch (Exception e) {
            System.err.println("Fehler beim Verkaufsprozess: " + e.getMessage());
        }
    }
}