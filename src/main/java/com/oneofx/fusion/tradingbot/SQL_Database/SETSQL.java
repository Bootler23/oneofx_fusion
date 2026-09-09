package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.HelperFunctions.Asset;
import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;

public class SETSQL {

    private static final Logger logger = LoggerFactory.getLogger(SETSQL.class);

    public static void compareBalanceWithFusion(FusionApiClient client) {
        double fusionBalance = Asset.getFreeCalced_Balance(TradingConstants.BASE_CURRENCY, client);
        System.out.print(":");
        if ((getBalance_SQL() != fusionBalance && fusionBalance > 10.0)) {
            setBalance(fusionBalance);
            System.out.print("Datenbank Aktualisiert " + fusionBalance);
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

    public static boolean getStatus(String tableString, String statusColumn, String currency) {
        String sql = "SELECT " + statusColumn + " FROM " + tableString + " WHERE currency = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency); // ✅ WHERE-Filter
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String status = rs.getString(statusColumn);
                return "true".equalsIgnoreCase(status) || "1".equals(status);
            }
            return false;

        } catch (SQLException err) {
            logger.error("Fehler beim Abrufen des Status für {}.{}: {}", tableString, statusColumn, err.getMessage());
            return false;
        }
    }

    public static boolean setStatus(StatusType statusType, boolean value) {
        return setStatus("currency", statusType.getColumnName(), value);
    }

    public static boolean setStatus(String tableString, String statusColumn, boolean value) {

        String sql = "UPDATE " + tableString + " SET " + statusColumn + " = ?";

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

    public static boolean setbuyStatus(boolean enabled) {
        return setStatus(StatusType.buyStatus, enabled);
    }

    public static boolean setsellStatus(boolean enabled) {
        return setStatus(StatusType.sellStatus, enabled);
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

    public static void getAVG_BalanceToAsset_atBuy() {
        String sql = "SELECT AVG(BalanceToAsset_atBuy) AS avg_balance_to_asset FROM historyPosition";
        try (Connection conHist = DriverManager.getConnection(dbUrl.getoneOfX());
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

            // System.out.println("Die Count-Spalte wurde erfolgreich aktualisiert.");
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

    public static double getDesiredAmount() {
        return SettingsRepository.getDouble("DesiredAmount");
    }

    public static void setPnL_Reverense(double newValue) {
        SettingsRepository.setDouble("PnL_reverense", newValue, 2);
    }

    public static double getPnL_Reverense() {
        return SettingsRepository.getDouble("PnL_reverense");
    }

    public static void setBuyingfalse() {
        SettingsRepository.setString("BUYING", "false");
    }
}
