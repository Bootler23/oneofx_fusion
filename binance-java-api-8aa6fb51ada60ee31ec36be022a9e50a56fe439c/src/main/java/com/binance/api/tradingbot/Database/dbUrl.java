package com.binance.api.tradingbot.Database;

public class dbUrl {

    public static String getSET() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/SETTING.db";
    }

    public static String getATH() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ATH_LTCEUR.db";
    }

    public static String getPOS() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR.db";
    }

    public static String getHIST() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";
    }

    public static String getWPD() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/WPD.db";
    }

    public static String getExpo() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ExpoTag.db";
    }
}
