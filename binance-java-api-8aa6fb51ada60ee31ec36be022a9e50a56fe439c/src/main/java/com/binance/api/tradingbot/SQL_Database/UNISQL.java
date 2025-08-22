package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
public class UNISQL {

    public static double getValueFromTable(String value, String tableName, final String dbUrl) {
        try (Connection con = DriverManager.getConnection(dbUrl);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT " + value + " FROM " + tableName)) {           
            return rs.getDouble(value);
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }    
}
