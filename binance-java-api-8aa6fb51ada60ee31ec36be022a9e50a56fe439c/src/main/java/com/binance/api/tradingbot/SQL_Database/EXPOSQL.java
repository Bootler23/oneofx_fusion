package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.round;

public class EXPOSQL {

       public static void insertnewBuyAmountEntry(String currencyPair, double buyAmount, double LivePrice) {        
        String sql = "INSERT INTO EXPO(Währung, BuyAmount, Kurs, Date, Time) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DriverManager.getConnection(dbUrl.getExpo());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currencyPair);
            ps.setDouble(2, round.three(buyAmount));
            ps.setDouble(3, LivePrice);
            ps.setString(4, Time.getCurrentDate());
            ps.setString(5, Time.getCurrentTime_HHmmss());

            ps.executeUpdate();

            System.out.println("Update BuyAmount hier");

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }   
}
