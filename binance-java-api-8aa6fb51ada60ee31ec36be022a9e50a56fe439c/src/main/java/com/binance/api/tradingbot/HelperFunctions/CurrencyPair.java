package com.binance.api.tradingbot.HelperFunctions;

public class CurrencyPair {

    public static double formatValueByCurrency(double value, String currency) {
        switch (currency.toUpperCase()) {
            case "BNBEUR":
                return round.three(value);
            case "LTCEUR":
                return round.three(value);
            case "BTCEUR":
                return round.five(value);
            case "XLMEUR":
                return round.zero(value);
            case "TRXXRP":
                return round.one(value);
            case "TRXEUR":
                return round.zero(value);
            default:
                return value;
        }
    }  
}