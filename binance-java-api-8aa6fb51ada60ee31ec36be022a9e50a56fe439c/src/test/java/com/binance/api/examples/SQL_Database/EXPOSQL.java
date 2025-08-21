package com.binance.api.examples.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.binance.api.examples.HelperFunctions.Time;
import com.binance.api.examples.HelperFunctions.round;

public class EXPOSQL {

    public final static String Url = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ExpoTag.db";

       public static void insertnewBuyAmountEntry(String currencyPair, double buyAmount, double LivePrice) {        
        try (Connection con = DriverManager.getConnection(Url);
             Statement query = con.createStatement()) {
            
            String SQL = "INSERT INTO EXPO(Währung, BuyAmount, Kurs, Date, Time) VALUES ('" + currencyPair + "', " + round.three(buyAmount) + ", " + LivePrice + ", '" 
                    + Time.getCurrentDate() + "', '" 
                    + Time.getCurrentTime_HHmmss() + "')";                    
            query.executeUpdate(SQL);

            System.out.println("Update BuyAmount");
            
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }    
}
