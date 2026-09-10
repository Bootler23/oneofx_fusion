package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.oneofx.fusion.tradingbot.Database.dbUrl;

public class EXPOSQL {

       public static void insertnewBuyAmountEntry(String currencyPair, double buyAmount, double LivePrice) {        
        String sql = "INSERT INTO currency(currency) VALUES (?)";
        try (Connection con = com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currencyPair);
            ps.executeUpdate();

            System.out.println("Update currency hier");

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }   
}
