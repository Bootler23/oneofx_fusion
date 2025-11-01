package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Time;

public class HISTSQL {

    public static void get_SellTrade_Records_WhereStatusZero(List<String> records) {
        try (Connection con = DriverManager.getConnection(dbUrl.getHIST());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT SellOrderId, Quantity, Währung, BuyPrice FROM HIST WHERE Status = 0")) {

            records.clear();

            while (rs.next()) {
                String sellOrderId = rs.getString("SellOrderId");
                String quantity = rs.getString("Quantity");
                String buyPrice = rs.getString("BuyPrice");
                String Währung = rs.getString("Währung");

                String dataRecord = sellOrderId + ", " + quantity + ", " + buyPrice + ", " + Währung;
                records.add(dataRecord);
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }

    // public static void getTradeInformationRecords(int statusCode, List<String> records) { // TODO
    //     String sql = "SELECT * FROM HIST WHERE Status = ?";
    //     try (Connection con = DriverManager.getConnection(dbUrl.getHIST());
    //             PreparedStatement pstmt = con.prepareStatement(sql)) {

    //         pstmt.setInt(1, statusCode);
    //         ResultSet rs = pstmt.executeQuery();

    //         records.clear();

    //         while (rs.next()) {
    //             String sellOrderId = rs.getString("SellOrderId");
    //             String quantity = rs.getString("Quantity");
    //             String buyPrice = rs.getString("BuyPrice");
    //             String Währung = rs.getString("Währung");

    //             String dataRecord = sellOrderId + ", " + quantity + ", " + buyPrice + ", " + Währung;
    //             records.add(dataRecord);
    //         }
    //     } catch (SQLException err) {
    //         System.out.println("SQL-Fehler: " + err.getMessage());
    //     }

    // }  

    public static void getDataRecords_WhereStatusOne(String currency, List<String> GetDataRecord) {
        try (Connection con = DriverManager.getConnection(dbUrl.getHIST());
                PreparedStatement query = con.prepareStatement("SELECT BuyOrderId, Split, Währung FROM HIST WHERE Status = 1 AND Split IS NOT NULL AND Währung = ?")) {

            query.setString(1, currency);
            ResultSet rs = query.executeQuery();

            GetDataRecord.clear();
            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId");
                String SplitValue = rs.getString("Split");
                String currency_Split = rs.getString("Währung");

                String dataRecord = BuyOrderId + ", " + SplitValue + ", " + currency_Split;
                GetDataRecord.add(dataRecord);
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }

    public static boolean getDate(String date) {
        String query = "  SELECT 1 FROM WPD WHERE \"Date\" = ?"; // LIMIT 1 = Performance-Optimierung

        try (Connection con = DriverManager.getConnection(dbUrl.getWPD());
                PreparedStatement stmt = con.prepareStatement(query)) {

            stmt.setString(1, date);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // Wenn ein Datensatz existiert, gibt es "true" zurück
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
            return false; // Falls ein Fehler auftritt, gehen wir davon aus, dass das Datum nicht
                          // existiert
        }
    }

    public static double getTaxe() {
        double tax = 0.0;
        try (Connection con = DriverManager.getConnection(dbUrl.getHIST());
                Statement stmt = con.createStatement()) {

            String query = "SELECT ROUND(SUM(Tax), 2) AS TotalTax FROM HIST";

            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                tax = rs.getDouble("TotalTax");
            }

        } catch (SQLException err) {
            System.err.println("SQL-Fehler beim Abrufen der Steuern: " + err.getMessage());
        }
        return tax;
    }

    public static double getSumColumnToday(final String Url, String columnName, String tableName) {

        String todayString = Time.getCurrentDate();
        try (Connection con = DriverManager.getConnection(Url);
                PreparedStatement statement = con.prepareStatement(
                        "SELECT SUM(" + columnName + ") AS SumColumn FROM " + tableName +
                                " WHERE SellDate = ?")) {

            statement.setString(1, todayString);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return rs.getDouble("SumColumn");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der LPP-Spalte: " + err.getMessage());
        }
        return 0.0;
    }

    public static void transferHISTToWPD(final String SellDate) {
        try (Connection conHIST = DriverManager.getConnection(dbUrl.getHIST());
                Statement stmtHIST = conHIST.createStatement();
                Connection conWPD = DriverManager.getConnection(dbUrl.getWPD())) {

            String selectSQL = "SELECT "
                    + "ROUND(SUM(GewinnAfterTax), 4) AS TotalGewinnAfterTax, "
                    + "ROUND(SUM(Tax), 4) AS TotalTax, "
                    + "ROUND(SUM(Gewinn), 4) AS TotalGewinn, "
                    + "ROUND(SUM(Fee), 4) AS TotalFee, "
                    + "ROUND(SUM(LossAfterTax), 4) AS LossAfterTax, "
                    + "COUNT(*) AS TotalRows "
                    + "FROM HIST "
                    + "WHERE SellDate = ?";

            try (PreparedStatement selectStmt = conHIST.prepareStatement(selectSQL)) {
                selectStmt.setString(1, SellDate);
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    String insertSQL = "INSERT INTO WPD (GewinnAfterTax, TotalTax, TotalGewinn, TotalFee, LossAfterTax, TotalRows, Date, Status, SellPerDayAVG, RRR) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                    try (PreparedStatement insertStmt = conWPD.prepareStatement(insertSQL)) {
                        insertStmt.setDouble(1, rs.getDouble("TotalGewinnAfterTax"));
                        insertStmt.setDouble(2, rs.getDouble("TotalTax"));
                        insertStmt.setDouble(3, rs.getDouble("TotalGewinn"));
                        insertStmt.setDouble(4, rs.getDouble("TotalFee"));
                        insertStmt.setDouble(5, rs.getDouble("LossAfterTax"));
                        insertStmt.setInt(6, rs.getInt("TotalRows"));
                        insertStmt.setString(7, SellDate);
                        insertStmt.setInt(8, 0); // Status explizit als Integer setzen
                        insertStmt.setDouble(9, WPDSQL.SellPerDayAVG());
                        insertStmt.setDouble(10,com.binance.api.tradingbot.RiskRewardRatio.RRR.calculateRiskRewardRatio(rs.getDouble("TotalGewinnAfterTax"), rs.getDouble("LossAfterTax")));
                                

                        int rowsAffected = insertStmt.executeUpdate();
                        System.out.println("Daten erfolgreich in WPD übertragen. Zeilen eingefügt: " + rowsAffected);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL-Fehler beim Transfer: " + e.getMessage());
            e.printStackTrace();
        }
    }  
}
