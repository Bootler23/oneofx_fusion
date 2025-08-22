package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.round;

public class EXPOSQL {

       public static void insertnewBuyAmountEntry(String currencyPair, double buyAmount, double LivePrice) {        
        try (Connection con = DriverManager.getConnection(dbUrl.getExpo());
             Statement query = con.createStatement()) {
            
            String SQL = "INSERT INTO EXPO(Währung, BuyAmount, Kurs, Date, Time) VALUES ('" + currencyPair + "', " + round.three(buyAmount) + ", " + LivePrice + ", '" 
                    + Time.getCurrentDate() + "', '" 
                    + Time.getCurrentTime_HHmmss() + "')";                    
            query.executeUpdate(SQL);

            System.out.println("Update BuyAmount hier");
            
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }    
}
