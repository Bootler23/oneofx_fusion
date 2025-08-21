package com.binance.api.examples.Settings;

public class set {

    public static int getGridforCurrency(String currency) {
        int Grid;
        switch (currency) {
            case "LTCEUR":
                Grid = 11;
                break;
            case "BNBEUR":
                Grid = 11;
                break;
            case "BTCEUR":
                Grid = 13;
                break;
            default:
                Grid = 7; // Default value if currency doesn't match any case
                break;
        }
        return Grid;
    }    

    public static int Currency(String[] currencies, int state) {
        state++;
        if (state >= currencies.length) {
            state = 0;
        }
        return state;
    }
 }
