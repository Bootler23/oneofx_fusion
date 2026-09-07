package com.oneofx.fusion.tradingbot.HelperFunctions;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.FusionApiException;

public class Asset {

    public static double getFree_Balance(String currency, FusionApiClient client) {
        try {
            double balance = round.two(Double.valueOf(client.getAccount().getAssetBalance(currency).getFree()));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (FusionApiException e) {
            System.out.println("Fehler: Balance der Bitpanda-Fusion-API nicht verfügbar.");
            return 0.0;
        }
    }

    public static double getLocked_Balance(String currency, FusionApiClient client) {
        try {
            double balance = round.two(Double.valueOf(client.getAccount().getAssetBalance(currency).getLocked()));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (FusionApiException e) {
            System.out.println("Fehler: Balance der Bitpanda-Fusion-API nicht verfügbar.");
            return 0.0;
        }
    }

    public static double getFreeCalced_Balance(String currency, FusionApiClient client) {
        try {
            // Fusion liefert "available" und "locked" bereits getrennt. Für neue
            // Orders darf nur der verfügbare Betrag verwendet werden.
            double balance = round.two(getFree_Balance(currency, client));
            return balance;

        } catch (NullPointerException e) {
            System.out.println("Fehler: Asset " + currency + " nicht gefunden oder nicht verfügbar.");
            return 0.0;
        } catch (FusionApiException e) {
            System.out.println("Fehler: Balance der Bitpanda-Fusion-API nicht verfügbar.");
            return 0.0;
        }
    }
}
