package com.binance.api.examples.xSaveReset;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ExtractDateTime {

    public static void getDataRecordsIDBuyTime(final String SQL, List<String> dataRecords) {
        try {
            dataRecords.clear();
            Connection con = DriverManager.getConnection(SQL);
            Statement query = con.createStatement();
            String SQL1 = "SELECT BuyOrderId, BuyTime, SellTime FROM HIST";
            ResultSet rs = query.executeQuery(SQL1);

            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId"); // 0
                String BuyTime = rs.getString("BuyTime"); // 1
                String SellTime = rs.getString("SellTime"); // 2

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

        // Iteriere durch die Datensätze in dataRecords
        for (String dataRecord : dataRecords) {
            // Trenne den Eintrag in BuyOrderId und das Datum (dataValue)
            String[] parts = dataRecord.split(", ");
            String BuyOrderId = parts[0]; // Die BuyOrderId
            //String BuyValues = parts[1]; // Das Datum (dataValue)
            String SellValues = parts[2]; // Die Uhrzeit (timeValue)

            try {
                LocalDateTime dateTime = LocalDateTime.parse(SellValues, formatter);

                // Extrahiere das Datum und die Uhrzeit
                String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                String formattedTime = dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                // Gib die getrennten Werte aus
                // System.out.println("BuyDate: " + formattedDate);
                // System.out.println("BuyTime: " + formattedTime);

                Connection con_update = DriverManager.getConnection(SQL);
                Statement update = con_update.createStatement();
                String SQL_update = "UPDATE HIST SET "
                        + "SellTime = '" + formattedTime + "', "
                        + "SellDate = '" + formattedDate + "' "
                        + "WHERE BuyOrderId = " + BuyOrderId + ";";

                update.executeUpdate(SQL_update);
                con_update.close();
                update.close();

                //System.out.println("");
                //System.out.println("DATE TIME UPDATED IN HIST");

            } catch (SQLException err) {
                System.out.println(err.getMessage());

            } catch (Exception e) {
                System.out.println("Fehler beim Parsen des Datums: " + e.getMessage());
            }
        }

    }
}
