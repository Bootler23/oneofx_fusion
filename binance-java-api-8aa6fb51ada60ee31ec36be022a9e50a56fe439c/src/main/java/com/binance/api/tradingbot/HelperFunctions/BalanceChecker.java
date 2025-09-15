package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BalanceChecker {

    /**
     * Formatiert eine Zahl ohne wissenschaftliche Notation
     */
    private static String formatDecimal(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.00000000", symbols);
        return df.format(value);
    }

    /**
     * Zeigt die Quantity für eine bestimmte Währung von der Börse an
     */
    public static void showCurrencyBalance(String currency, BinanceApiRestClient client) {
        try {
            Account account = client.getAccount();
            AssetBalance balance = account.getAssetBalance(currency);
            
            String free = balance.getFree();
            String locked = balance.getLocked();
            
            double freeDouble = Double.valueOf(free);
            double lockedDouble = Double.valueOf(locked);
            double totalDouble = freeDouble + lockedDouble;
            double dbQuantity = POSSQL.getSumQuantity();
            double difference = totalDouble - dbQuantity;
            
            System.out.println("=== " + currency + " Balance von Börse ===");                
            System.out.println("Börse:     " + formatDecimal(totalDouble) + " " + currency);
            System.out.println("Datenbank: " + formatDecimal(dbQuantity) + " " + currency);
            System.out.println("Differenz: " + formatDecimal(difference) + " " + currency);
            System.out.println("================================");
            
        } catch (BinanceApiException e) {
            System.err.println("Fehler beim Abrufen der " + currency + " Balance: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unbekannter Fehler: " + e.getMessage());
        }
    }
}