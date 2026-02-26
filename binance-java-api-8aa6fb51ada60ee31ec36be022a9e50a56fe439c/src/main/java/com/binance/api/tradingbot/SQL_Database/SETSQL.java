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
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.constants.TradingConstants;

public class SETSQL {

    private static final Logger logger = LoggerFactory.getLogger(SETSQL.class);

    public static void CompareBalanceInSQLWithBinanceBalance(BinanceApiRestClient client) {
        double BNB_Balance = Asset.getFreeCalced_Balance(TradingConstants.BASE_CURRENCY, client);
        System.out.print(":");
        if ((getBalance_SQL() != (BNB_Balance) && (BNB_Balance > 10.0))) {
            setBalance(BNB_Balance);
            System.out.print("Datenbank Aktualisiert " + BNB_Balance);
            empty.Line();
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

    // ------- minBuyAmount -------
    public static double getminBuyAmount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT minBuyAmount FROM SETTING")) {
            return round.five(rs.getDouble("minBuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 5.5;
        }
    }

    public static void setminBuyAmount(double BuyAmount) {
        String sql = "UPDATE SETTING SET minBuyAmount = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.six(BuyAmount));
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    // ------- get percent_toAdd -------

    public static double getPercentToAdd() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT percent_toAdd FROM SETTING")) {
            return round.three(rs.getDouble("percent_toAdd"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    // ------- maxBuyAmount -------
    public static double getmaxBuyAmount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT maxBuyAmount FROM SETTING")) {
            return round.two(rs.getDouble("maxBuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 500.0;
        }
    }

    public static void setmaxBuyAmount(double BuyAmount) {
        String sql = "UPDATE SETTING SET maxBuyAmount = ?";
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

    public static double getPercentToSell() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT PercentToSell FROM SETTING")) {
            return round.three(rs.getDouble("PercentToSell"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }
    
    public static double getBaseStopLossPercent() {
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT BaseStopLoss FROM SETTING")) {
            return round.three(rs.getDouble("BaseStopLoss"));
        } catch (SQLException err) {
            logger.warn("BaseStopLoss-Spalte nicht gefunden, verwende Fallback -2.0: {}", err.getMessage());
            return -2.0;
        }
    }
   
    public static void setBaseStopLossPercent(double stopLossPercent) {
        String sql = "UPDATE SETTING SET BaseStopLoss = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.three(stopLossPercent));
            ps.executeUpdate();
            logger.info("BaseStopLoss auf {}% gesetzt", stopLossPercent);

        } catch (SQLException err) {
            logger.error("Fehler beim Setzen des BaseStopLoss: {}", err.getMessage());
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

    /**
     * Speichert Exchange-Balance-Informationen in der Datenbank.
     * 
     * @param exchangeBalance Gesamtbalance von der Börse
     * @param databaseBalance Summe der Positionen aus der Datenbank
     * @param difference      Differenz zwischen Börse und Datenbank
     * @return true wenn erfolgreich, false bei Fehler
     */
    public static boolean setBalanceExchangeInfo(double exchangeBalance, double databaseBalance, double difference) {
        String sql = "UPDATE SETTING SET Börse = ?, Datenbank = ?, Differenz = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.eight(exchangeBalance));
            ps.setDouble(2, round.eight(databaseBalance));
            ps.setDouble(3, round.eight(difference));

            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException err) {
            logger.error("Fehler beim Speichern der Balance-Info: {}", err.getMessage());
            return false;
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

    public static void set_BNB_price(double newValue) {
        String sql = "UPDATE SETTING SET BNB_Price = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, newValue);
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void setPercentToAdd(double newValue) {
        String sql = "UPDATE SETTING SET percent_toAdd = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.eight(newValue));
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    // public static double getDesiredAmount() {
    //     try (Connection con = DriverManager.getConnection(dbUrl.getSET());
    //             Statement query = con.createStatement();
    //             ResultSet rs = query.executeQuery("SELECT DesiredAmount FROM SETTING")) {
    //         return round.two(rs.getDouble("DesiredAmount"));
    //     } catch (SQLException err) {
    //         System.out.println(err.getMessage());
    //         return 0.0;
    //     }
    // }

    public static double get_BNB_price() {
        String sql = "SELECT BNB_Price FROM SETTING";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery(sql)) {

            if (rs.next()) {
                double bnbPrice = round.two(rs.getDouble("BNB_Price"));
                return bnbPrice;
            }
        } catch (SQLException err) {
            System.out.println("Fehler beim Abrufen des BNB-Preises: " + err.getMessage());
        }
        return 0.0;
    }

    public static void getAVG_BalanceToAsset_atBuy() {
        String sql = "SELECT AVG(BalanceToAsset_atBuy) AS avg_balance_to_asset FROM HIST";
        try (Connection conHist = DriverManager.getConnection(dbUrl.getHIST());
                Statement query = conHist.createStatement();
                ResultSet rs = query.executeQuery(sql)) {

            if (rs.next()) {
                double avgBalanceToAsset = round.five(rs.getDouble("avg_balance_to_asset"));

                String updateSql = "UPDATE SETTING SET y_Factor = ?";
                try (Connection conSet = DriverManager.getConnection(dbUrl.getSET());
                        PreparedStatement ps = conSet.prepareStatement(updateSql)) {
                    ps.setDouble(1, avgBalanceToAsset);
                    ps.executeUpdate();
                } catch (SQLException updateErr) {
                    System.out.println("Fehler beim Aktualisieren von y_Factor: " + updateErr.getMessage());
                }
            }
        } catch (SQLException err) {
            System.out.println("Fehler beim Abrufen des Durchschnitts: " + err.getMessage());
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

    public static void setratioBalanceToBA() {
        String sql = "UPDATE SETTING SET ratioBalanceToBA = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            double balance = getBalance_SQL();
            double buyAmount = getminBuyAmount();
            double ratio = balance / buyAmount;

            ps.setDouble(1, round.three(ratio));
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
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

    public static void setBuyPriceTest(double buyPrice) {
        String sql = "UPDATE SETTING SET TP = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.two(buyPrice));
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void setExpectationCounter(int expectationCounter) {
        String sql = "UPDATE SETTING SET ExpectationCounter = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getSET());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, expectationCounter);
            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static double getDesiredAmount() {
        return SettingsRepository.getDouble("DesiredAmount", 0.0, 2);
    }

    // public static boolean setExpectationCounter(int ExpectationCounter){
    //     return SettingsRepository.setInt("ExpectationCounter", ExpectationCounter);
    // } 

    // public static boolean setDesiredAmount(double value) {
    //     return SettingsRepository.setDouble("DesiredAmount", value, 2);
    // }

    // // Weitere Beispiele:
    // public static double getBalance_SQL() {
    //     return SettingsRepository.getDouble("Balance", 0.0, 2);
    // }

    // public static boolean setBalance(double value) {
    //     return SettingsRepository.setDouble("Balance", value, 2);
    // }

    // public static double getminBuyAmount() {
    //     return SettingsRepository.getDouble("minBuyAmount", 5.5, 5);
    // }

    // public static boolean setminBuyAmount(double value) {
    //     return SettingsRepository.setDouble("minBuyAmount", value, 6);
    // }

    // public static int getCount() {
    //     return SettingsRepository.getInt("Count", 0);
    // }

    // public static boolean updateCount(int value) {
    //     return SettingsRepository.setInt("Count", value);
    // }

    // public static boolean getRSI() {
    //     return SettingsRepository.getBoolean("RSI", false);
    // }

    // public static boolean setRSI(boolean value) {
    //     return SettingsRepository.setBoolean("RSI", value);
    // }
}