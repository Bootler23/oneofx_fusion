package com.binance.api.tradingbot.Settings;

public class CurrencyConfig {

    private static final String[] BUY_CURRENCIES = {"LTCEUR", "BNBEUR"};

    /**
     * Gibt die konfigurierten Währungspaare für Kaufoperationen zurück
     * @return Array der Währungspaare
     */
    public static String[] getBuyCurrencies() {
        return BUY_CURRENCIES.clone(); // Clone um Manipulation zu verhindern
    }
    
    /**
     * Gibt die Anzahl der konfigurierten Währungspaare zurück
     * @return Anzahl der Währungspaare
     */
    public static int getBuyCurrenciesCount() {
        return BUY_CURRENCIES.length;
    }
}
