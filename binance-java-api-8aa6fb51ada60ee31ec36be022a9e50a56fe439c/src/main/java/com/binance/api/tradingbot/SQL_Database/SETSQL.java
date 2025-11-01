package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.constants.TradingConstants;

public class SETSQL {

    public static void CompareBalanceInSQLWithBinanceBalance(BinanceApiRestClient client) {
        double BNB_Balance = Asset.getFreeCalced_Balance(TradingConstants.BASE_CURRENCY, client);
        System.out.print(":");
        if ((getBalance_SQL() != (BNB_Balance) && (BNB_Balance > 1.0))) {
            setBalance(BNB_Balance);
            System.out.print("Datenbank Aktualisiert " + BNB_Balance);
            empty.Line();
        }
    }

    public static double getUSDCBalance(BinanceApiRestClient client) {
        try {
            double USDC_Balance = Asset.getFreeCalced_Balance("USDC", client);
            return round.two(USDC_Balance);
        } catch (BinanceApiException e) {
            System.out.println("Fehler beim Abrufen des USDC-Saldos: " + e.getMessage());
            return 0.0;
        }
    }

    public static double getBalance_SQL() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT Balance FROM SETTING")) {
            return round.two(rs.getDouble("Balance"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double get_newBuyAmount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT newBuyAmount FROM SETTING")) {
            return round.three(rs.getDouble("newBuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double getROIpercent() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT ROI_percent FROM SETTING")) {
            return round.three(rs.getDouble("ROI_percent"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }
    
    public static boolean getROIstatus() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT ROI_status FROM SETTING")) {

            if (rs.next()) {
                String status = rs.getString("ROI_status");
                // Unterstütze verschiedene Formate: "true", "1", "TRUE", etc.
                return "true".equalsIgnoreCase(status) || "1".equals(status);
            }
            return false;

        } catch (SQLException err) {
            System.err.println("Fehler beim Abrufen des ROI-Status: " + err.getMessage());
            return false;
        }
    }
   
    public static boolean setROIstatus(boolean enabled) {
        String statusValue = enabled ? "true" : "false";

        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement pstmt = con.prepareStatement(
                        "UPDATE SETTING SET ROI_status = ?")) {

            pstmt.setString(1, statusValue);
            int updatedRows = pstmt.executeUpdate();

            if (updatedRows > 0) {
                System.out.println("ROI-Status wurde aktualisiert: " + statusValue);
                return true;
            }
            return false;

        } catch (SQLException err) {
            System.err.println("Fehler beim Setzen des ROI-Status: " + err.getMessage());
            return false;
        }
    }

    public static void checkForNewBuyAmount(String currencyPair, double newBuyAmount, double LivePrice) {
        double currentBuyAmount = get_newBuyAmount();
        if (newBuyAmount > currentBuyAmount) {
            EXPOSQL.insertnewBuyAmountEntry(currencyPair, newBuyAmount, LivePrice);
            try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                    Statement query = con.createStatement()) {
                String SQL = "UPDATE SETTING SET newBuyAmount = " + round.three(newBuyAmount);

                query.executeUpdate(SQL);

            } catch (SQLException err) {
                System.out.println(err.getMessage());
            }
        }
    }

    public static int getcountPart() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT countPart FROM SETTING")) {
            return rs.getInt("countPart");
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0;
        }
    }

    public static int setcountPart(int newValue) {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET countPart = " + newValue;
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0;
        }
    }

    public static double setBalance(double newValue) {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET Balance = " + newValue;
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double getBuyAmount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT BuyAmount FROM SETTING")) {
            return round.two(rs.getDouble("BuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double setBuyAmount(double newValue) {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET BuyAmount = " + round.two(newValue);
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static int getCount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT Count FROM SETTING")) {
            return rs.getInt("Count");
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0;
        }
    }

    public static void updateCount(int newCountValue) {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement statement = con.createStatement()) {

            String updateSQL = "UPDATE SETTING SET Count = " + newCountValue;
            statement.executeUpdate(updateSQL);

            System.out.println("Die Count-Spalte wurde erfolgreich aktualisiert.");
        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der Count-Spalte: " + err.getMessage());
        }
    }

    public static double get_DataBase_Table_Value(final String dbUrl, String tableName, String value) {
        try (Connection con = DriverManager.getConnection(dbUrl);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT " + value + " FROM " + tableName)) {
            return round.two(rs.getDouble(value));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double getReserve() {
        String sql = "SELECT Reserve FROM SETTING";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double val = rs.getDouble("Reserve");
                    return rs.wasNull() ? 0.0 : val;
                }
            }
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
        return 0.0;
    }

    public static double setReserve(double newValue) {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET Reserve = " + round.eight(newValue);
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }
}