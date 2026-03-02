package com.binance.api.tradingbot.Database;

public class dbUrl {

    public static String getSET() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/SETTING.db";
    }

    public static String getPOS() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR.db";
    }

    public static String getoneOfX() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/oneofx.db";
    }

    public static String getWPD() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/WPD.db";
    }

    public static String getPerformance() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/Performance.db";
    }

    public static String getCurrency() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/currency.db";
    }
}
