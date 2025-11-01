package com.binance.api.tradingbot.TradeInformation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class getTrade {

      public static void RecordsByStatus(String dbUrl, String tableName, int status, String[] columns, List<String> records) {

        records.clear();      
        String columnList = String.join(", ", columns);
        String sql = String.format("SELECT %s FROM %s WHERE Status = %d", columnList, tableName, status);

        try (Connection con = DriverManager.getConnection(dbUrl);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery(sql)) {

            while (rs.next()) {
                StringBuilder dataRecord = new StringBuilder();
               
                for (int i = 0; i < columns.length; i++) {
                    if (i > 0)
                        dataRecord.append(", ");
                    dataRecord.append(rs.getString(columns[i]));
                }

                records.add(dataRecord.toString());
            }

        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }    
}
