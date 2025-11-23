package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.constants.TradingConstants;

/**
 * Repository-Klasse für SETTING-Tabellen-Operationen.
 * Verwaltet globale Trading-Einstellungen wie Balance, Reserve, und
 * Status-Flags.
 */
public class SETSQL {

    private static final Logger logger = LoggerFactory.getLogger(SETSQL.class);

    public static void CompareBalanceInSQLWithBinanceBalance(BinanceApiRestClient client) {
        double BNB_Balance = Asset.getFreeCalced_Balance(TradingConstants.BASE_CURRENCY, client);
        System.out.print(":");
        if ((getBalance_SQL() != (BNB_Balance) && (BNB_Balance > 1.0))) {
            setBalance(BNB_Balance);
            System.out.print("Datenbank Aktualisiert " + BNB_Balance);
            empty.Line();
        }
    }

    // public static double getUSDCBalance(BinanceApiRestClient client) {
    //     try {
    //         double USDC_Balance = Asset.getFreeCalced_Balance("USDC", client);
    //         return round.two(USDC_Balance);
    //     } catch (BinanceApiException e) {
    //         System.out.println("Fehler beim Abrufen des USDC-Saldos: " + e.getMessage());
    //         return 0.0;
    //     }
    // }

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

    public static double getMinBuyAmount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT MinBuyAmount FROM SETTING")) {
            return round.two(rs.getDouble("MinBuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 5.5;
        }
    }

    public static void setMinBuyAmount(double BuyAmount) {
        String sql = "UPDATE SETTING SET MinBuyAmount = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.two(BuyAmount)); 
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
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

    public static double getDCA_Amount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT DCA_Amount FROM SETTING")) {
            return round.three(rs.getDouble("DCA_Amount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    @Deprecated
    public static boolean getROIstatus() {
        return getRoiStatus();
    }

    public static boolean getStatus(StatusType statusType) {
        return getStatus(statusType.getColumnName());
    }

    public static boolean getStatus(String statusColumn) {
        if (!isValidStatusColumn(statusColumn)) {
            throw new IllegalArgumentException(
                    "Ungültige Statusspalte: " + statusColumn +
                            ". Erlaubt sind: ROI, BUYING, SELLING");
        }

        String sql = "SELECT " + statusColumn + " FROM SETTING";

        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String status = rs.getString(statusColumn);
                // Unterstütze verschiedene Formate: "true", "1", "TRUE", etc.
                return "true".equalsIgnoreCase(status) || "1".equals(status);
            }
            return false;

        } catch (SQLException err) {
            logger.error("Fehler beim Abrufen des Status für {}: {}", statusColumn, err.getMessage());
            return false;
        }
    }

    private static boolean isValidStatusColumn(String columnName) {
        return "ROI".equals(columnName) ||
                "BUYING".equals(columnName) ||
                "SELLING".equals(columnName);
    }

    public static boolean getRoiStatus() {
        return getStatus(StatusType.ROI);
    }

    public static boolean getBuyingStatus() {
        return getStatus(StatusType.BUYING);
    }

    public static boolean getSellingStatus() {
        return getStatus(StatusType.SELLING);
    }

    public static boolean setStatus(StatusType statusType, boolean value) {
        return setStatus(statusType.getColumnName(), value);
    }

    public static boolean setStatus(String statusColumn, boolean value) {
        // Validierung der erlaubten Spalten
        if (!isValidStatusColumn(statusColumn)) {
            throw new IllegalArgumentException(
                    "Ungültige Statusspalte: " + statusColumn +
                            ". Erlaubt sind: ROI_status, BUYING, SELLING");
        }

        String sql = "UPDATE SETTING SET " + statusColumn + " = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, value ? "1" : "0");
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logger.info("{} wurde erfolgreich auf {} gesetzt", statusColumn, value);
                return true;
            }
            return false;

        } catch (SQLException err) {
            logger.error("Fehler beim Setzen des Status für {}: {}", statusColumn, err.getMessage());
            return false;
        }
    }

    public static boolean setRoiStatus(boolean enabled) {
        return setStatus(StatusType.ROI, enabled);
    }

    public static boolean setBuyingStatus(boolean enabled) {
        return setStatus(StatusType.BUYING, enabled);
    }

    public static boolean setSellingStatus(boolean enabled) {
        return setStatus(StatusType.SELLING, enabled);
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