package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;

public class HISTSQL {

    public static void get_SellTrade_Records_WhereStatusZero(List<String> records) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
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

    public static void getDataRecords_WhereStatusOne(String currency, List<String> GetDataRecord) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
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
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
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

    public static double getbuyamount(String sellorderID) {
        double buyamount = 0.0;
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement("SELECT BuyAmount FROM HIST WHERE SellOrderId = ?")) {

            pstmt.setString(1, sellorderID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                buyamount = rs.getDouble("BuyAmount");
            }

        } catch (SQLException err) {
            System.err.println("SQL-Fehler beim Abrufen des Kaufbetrags: " + err.getMessage());
        }
        return buyamount;
    }

    public static double getbuyfee(String sellorderID) {
        double buyfee = 0.0;
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement("SELECT BuyFee FROM HIST WHERE SellOrderId = ?")) {

            pstmt.setString(1, sellorderID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                buyfee = rs.getDouble("BuyFee");
            }

        } catch (SQLException err) {
            System.err.println("SQL-Fehler beim Abrufen der Kaufgebühr: " + err.getMessage());
        }
        return buyfee;
    }

    public static double getbuyprice(String sellorderID) {
        double buyprice = 0.0;
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement("SELECT BuyPrice FROM HIST WHERE SellOrderId = ?")) {

            pstmt.setString(1, sellorderID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                buyprice = rs.getDouble("BuyPrice");
            }

        } catch (SQLException err) {
            System.err.println("SQL-Fehler beim Abrufen des Kaufpreises: " + err.getMessage());
        }
        return buyprice;
    }

    public static int getcountHist() {
        int count = 0;
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement stmt = con.createStatement()) {

            String query = "SELECT COUNT(*) AS TotalCount FROM HIST";

            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                count = rs.getInt("TotalCount");
            }

        } catch (SQLException err) {
            System.err.println("SQL-Fehler beim Abrufen der HIST-Anzahl: " + err.getMessage());
        }
        return count;
    }

    public static void setsellfee(String sellorderID, double sellfee) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement("UPDATE HIST SET SellFee = ? WHERE SellOrderId = ?")) {

            pstmt.setDouble(1, sellfee);
            pstmt.setString(2, sellorderID);
            pstmt.executeUpdate();

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren des Kaufgebührwerts: " + err.getMessage());
        }
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

}
