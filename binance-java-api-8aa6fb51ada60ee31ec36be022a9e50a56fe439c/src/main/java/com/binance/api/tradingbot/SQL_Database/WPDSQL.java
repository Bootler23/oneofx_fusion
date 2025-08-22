package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import com.binance.api.tradingbot.HelperFunctions.CalcDays;
import com.binance.api.tradingbot.RiskRewardRatio.RRR;

public class WPDSQL {

    // Bei jedem Soll sol WPD aktualisiert werden
    // Schaue zuerst ob es das Datum als eintrag schon gibt
    // Wenn ja, dann ak
    // OrigPrice wird nicht mehr gebraucht

    public static void getGewinnAfterTax(final String HIST, final String WPD, final String SET) {
        String startDate = "01.01.2024";
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(startDate));

            Calendar today = Calendar.getInstance(); // Heutiges Datum
            today.add(Calendar.DAY_OF_YEAR, -1); // Einen Tag zurückgehen, um heute nicht einzuschließen

            // Solange das Startdatum kleiner oder gleich gestern ist, weiterzählen
            while (cal.before(today) || cal.equals(today)) {
                String currentDate = sdf.format(cal.getTime());

                if (!HISTSQL.getDate(WPD, currentDate)) {
                    HISTSQL.transferHISTToWPD(HIST, WPD, SET, currentDate);
                }
                // zähle einen Tag nach oben und gehe durch alle Datum´s
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double SellPerDayAVG(final String SET) {
        double sellPerDayAVG = (SETSQL.getCount(SET) / CalcDays.fromDate("01.01.2025"));
        return sellPerDayAVG;
    }

    public static void getDataRecords_WhereStatusZero(final String WPD, List<String> GetDataRecord) {
        try (Connection con = DriverManager.getConnection(WPD);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery(
                        "SELECT SellOrderId, GewinnAfterTax, TotalTax, TotalGewinn, TotalFee FROM WPD WHERE Status = 0")) {

            GetDataRecord.clear();
            while (rs.next()) {
                String sellOrderId = rs.getString("SellOrderId");
                String gewinnAfterTax = rs.getString("GewinnAfterTax");
                String totalTax = rs.getString("TotalTax");
                String totalGewinn = rs.getString("TotalGewinn");
                String totalFee = rs.getString("TotalFee");

                String dataRecord = sellOrderId + ", " + gewinnAfterTax + ", " + totalTax + ", " + totalGewinn + ", "
                        + totalFee;
                GetDataRecord.add(dataRecord);
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }

    public static void getDataRecords_WPD(final String WPD, List<String> dataRecords) {
        try {
            dataRecords.clear();
            Connection con = DriverManager.getConnection(WPD);
            Statement query = con.createStatement();

            String SQL = "SELECT GewinnAfterTax, Status FROM WPD WHERE Status = 0";

            ResultSet rs = query.executeQuery(SQL);

            while (rs.next()) {
                String GewinnAfterTax = rs.getString("GewinnAfterTax"); // 9
                String Status = rs.getString("Status"); // 10

                String dataRecord = GewinnAfterTax + ", " + Status;

                dataRecords.add(dataRecord);
            }
            con.close();
            query.close();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void setLossValue(final String WPD, final String lossValue) {
        try (Connection con = DriverManager.getConnection(WPD);
                Statement query = con.createStatement()) {

            String SQL = "UPDATE WPD SET LossValue = '" + lossValue + "' WHERE Status = 0";
            query.executeUpdate(SQL);
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }

    public static void getLossValue(final String HIST, List<String> resultList, String Date) {
        try (Connection con = DriverManager.getConnection(HIST);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery(
                        "SELECT * FROM HIST WHERE SellDate = '" + Date + "' " +
                                "AND GewinnAfterTax < 0 " +
                                "AND (SellTime BETWEEN '22:00:00' AND '22:00:15')")) {

            if (rs.next()) {
                String lossValue = rs.getString("LossValue");
                resultList.add(lossValue);
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }

    public static void CalcRiskRewardRation() {

    }
}
