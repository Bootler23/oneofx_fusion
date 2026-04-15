package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.binance.api.tradingbot.Database.dbUrl;

public class EXPOSQL {

       public static void insertnewBuyAmountEntry(String currencyPair, double buyAmount, double LivePrice) {        
        String sql = "INSERT INTO currency(Währung) VALUES (?)";
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currencyPair);
            ps.executeUpdate();

            System.out.println("Update currency hier");

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }   
}
