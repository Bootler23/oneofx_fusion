package com.binance.api.tradingbot.HelperFunctions;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class round {

    public static double zero(double value) {
        return Math.round(value);
    }

    public static double one(double value) {
        return Math.round(10.0 * value) / 10.0;
    }

    public static double two(double value) {
        return Math.round(100.0 * value) / 100.0;
    }

    public static double three(double value) {
        return Math.round(1000.0 * value) / 1000.0;
    }

    public static double four(double value) {
        return Math.round(10000.0 * value) / 10000.0;
    }

    public static double five(double value) {
        return Math.round(100000.0 * value) / 100000.0;
    }

    public static double six(double value) {
        return Math.round(1000000.0 * value) / 1000000.0;
    }

    public static double seven(double value) {
        return Math.round(10000000.0 * value) / 10000000.0;
    }

    public static double eight(double value) {
        return Math.round(100000000.0 * value) / 100000000.0;
    }    

    public static double Quantity(double value, String Currency) {
        return RoundCurrency.forQuantity(value, Currency);
    }

    public static String withPoint(double Qty) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');

        // Festlegen des Formats mit genau 6 Nachkommastellen
        DecimalFormat df = new DecimalFormat("0.000000", symbols);
        df.setRoundingMode(RoundingMode.HALF_UP); // Normale Rundung

        // Formatieren des Ergebnisses als String
        return df.format(Qty);
    }
}