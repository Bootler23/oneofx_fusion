package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;

public class BalanceChecker {  
   
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

            SETSQL.setBalanceExchangeInfo(totalDouble, dbQuantity, difference);   
            
        } catch (BinanceApiException e) {
            System.err.println("Fehler beim Abrufen der " + currency + " Balance: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unbekannter Fehler: " + e.getMessage());
        }
    }
}