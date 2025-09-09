package com.binance.api.tradingbot.HelperFunctions;

public class RoundCurrency {

    public static double forQuantity(double value, String currency) {
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

    public static double forTickerPrice(double value, String currency) {
        switch (currency.toUpperCase()) {
            case "BNBEUR":
                return round.two(value);
            case "LTCEUR":
                return round.two(value);
            case "BTCEUR":
                return round.two(value);
            case "XLMEUR":
                return round.four(value);
            case "TRXXRP":
                return round.four(value);
            case "TRXEUR":
                return round.four(value);
            case "LTCBTC":
                return round.six(value);
            default:
                return value;
        }
    }

    
}