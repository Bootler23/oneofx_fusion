package com.binance.api.examples.HelperFunctions;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;

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
}