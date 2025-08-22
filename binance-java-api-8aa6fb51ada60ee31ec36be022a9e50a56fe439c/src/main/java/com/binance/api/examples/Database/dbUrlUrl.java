package com.binance.api.examples.Database;

public class dbUrlUrl {

    public static String getSettingDB() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/SETTING.db";
    }

    public static String getATHDB() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ATH_LTCEUR.db";
    }

    public static String getPOSDB() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR.db";
    }

    public static String getHISTDB() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";
    }

    public static String getWPDDB() {
        return "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/WPD.db";
    }
}
