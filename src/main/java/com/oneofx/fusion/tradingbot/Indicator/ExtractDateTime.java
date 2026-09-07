package com.oneofx.fusion.tradingbot.Indicator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.domain.HistoryPosition;

public class ExtractDateTime {

    private static final HistDAO histDAO = new HistDAO();

    public static void getDataRecordsIDBuyTime(final String SQL, List<String> dataRecords) {
        try {
            dataRecords.clear();
            Connection con = DriverManager.getConnection(SQL);
            Statement query = con.createStatement();
            String SQL1 = "SELECT BuyOrderId, BuyTime, SellTime FROM HIST";
            ResultSet rs = query.executeQuery(SQL1);

            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId");
                String BuyTime = rs.getString("BuyTime");
                String SellTime = rs.getString("SellTime");

                String dataRecord = BuyOrderId + ", " + BuyTime + ", " + SellTime;
                dataRecords.add(dataRecord);
            }
            con.close();
            query.close();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void SepareteValue(final String SQL, List<String> dataRecords) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        for (String dataRecord : dataRecords) {
            String[] parts = dataRecord.split(", ");
            String BuyOrderId = parts[0];
            String SellValues = parts[2];

            try {
                LocalDateTime dateTime = LocalDateTime.parse(SellValues, formatter);

                String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                String formattedTime = dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                histDAO.updateByBuyOrderId(new HistoryPosition.Builder(null, BuyOrderId)
                        .sellTime(formattedTime)
                        .sellDate(formattedDate)
                        .build());

            } catch (Exception e) {
                System.out.println("Fehler beim Parsen des Datums: " + e.getMessage());
            }
        }
    }
}
