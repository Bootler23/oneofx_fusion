package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.Settings.bnb;

public class POSSQL {

    public static double getLastPrice(String currencyPair, List<Double> LivePrice) {
        double PriceMin = Double.MAX_VALUE;
        boolean foundPriceInDB = false;

        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement query = con.createStatement()) {
            String SQL = "SELECT OrderPrice FROM POS WHERE Status IN (0, 1) AND Währung = '" + currencyPair + "'";

            ResultSet rs = query.executeQuery(SQL);

            while (rs.next()) {
                double currentPrice = rs.getDouble("OrderPrice");
                if (currentPrice < PriceMin) {
                    PriceMin = currentPrice;
                    foundPriceInDB = true;
                }
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }

        if (!foundPriceInDB && !LivePrice.isEmpty()) {
            PriceMin = LivePrice.get(0);
        }
        return PriceMin;
    }

    public static void get_BuyOrderId_WhereStatusZero(List<Long> orderIdList, String currencyPair) {
        try {
            orderIdList.clear();
            try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                    Statement query = con.createStatement()) {

                String SQL = "SELECT BuyOrderId FROM POS WHERE Status = 0 AND Währung = '" + currencyPair + "'";
                ResultSet rs = query.executeQuery(SQL);

                while (rs.next()) {
                    Long BuyOrderId = rs.getLong("BuyOrderId");
                    orderIdList.add(BuyOrderId);
                }
            }
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void get_BuyTrade_Records_WhereStatusFive(List<String> GetDataRecord) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT BuyOrderId, Währung FROM POS WHERE Status = 5")) {

            GetDataRecord.clear();
            while (rs.next()) {
                String sellOrderId = rs.getString("BuyOrderId");
                String Währung = rs.getString("Währung");

                String dataRecord = sellOrderId + ", " + Währung;
                GetDataRecord.add(dataRecord);
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler: " + err.getMessage());
        }
    }

    public static double getSumBuyAmount() {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement statement = con.createStatement()) {

            String SQL = "SELECT SUM(BuyAmount) AS SumBuyAmount FROM POS";
            ResultSet rs = statement.executeQuery(SQL);

            if (rs.next()) {
                return rs.getDouble("SumBuyAmount");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der LPP-Spalte: " + err.getMessage());
        }
        return 0.0;
    }

    public static double getSumColumnWith(final String Url, String columnName, String tableName) {
        try (Connection con = DriverManager.getConnection(Url);
                Statement statement = con.createStatement()) {

            // String SQL = "SELECT SUM(BuyAmount) AS SumBuyAmount FROM POS WHERE Status IN
            // (1, 7)";
            String SQL = "SELECT SUM(" + columnName + ") AS SumBuyAmount FROM " + tableName;
            ResultSet rs = statement.executeQuery(SQL);

            if (rs.next()) {
                return rs.getDouble("SumBuyAmount");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der LPP-Spalte: " + err.getMessage());
        }
        return 0.0;
    }

    public static int getCountPOS(String currencyPair) {
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement statement = con.createStatement()) {

            String SQL = "SELECT COUNT(*) AS RecordCount FROM POS WHERE Status IN (0, 1) AND Währung = '" + currencyPair
                    + "'";
            ResultSet rs = statement.executeQuery(SQL);

            if (rs.next()) {
                int count = rs.getInt("RecordCount");
                return count;
            }

        } catch (SQLException err) {
            System.err.println("Fehler beim Zählen der Datensätze: " + err.getMessage());
        }
        return 0;
    }

    public static Double getAverageBuyAmount() {
        Double averageBuyAmount = null;

        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                Statement query = con.createStatement()) {

            String SQL = "SELECT AVG(BuyAmount) AS AverageBuyAmount FROM POS";
            ResultSet rs = query.executeQuery(SQL);

            if (rs.next()) {
                averageBuyAmount = rs.getDouble("AverageBuyAmount");
                System.out.println("Durchschnittlicher Kaufbetrag: " + round.two(averageBuyAmount));
            }
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
        return averageBuyAmount;
    }

    public static void getDataRecords_WhereStatusOneOrSeven(String CurrencyPair, List<String> dataRecords) {
        try {
            dataRecords.clear();
            try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
                    PreparedStatement pstmt = con.prepareStatement(
                            "SELECT BuyOrderId, OrderPrice, Qty, BuyAmount, BuyPrice, BuyDate, BuyTime " +
                                    "FROM POS WHERE Status IN (1, 7) AND Währung = ?")) {

                pstmt.setString(1, CurrencyPair);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String BuyOrderId = rs.getString("BuyOrderId"); // 0
                    String OrderPrice = rs.getString("OrderPrice"); // 1
                    String Quantity = rs.getString("Qty"); // 2
                    String BuyAmount = rs.getString("BuyAmount"); // 3
                    String BuyPrice = rs.getString("BuyPrice"); // 4
                    String BuyDate = rs.getString("BuyDate"); // 7
                    String BuyTime = rs.getString("BuyTime"); // 8

                    String dataRecord = BuyOrderId + ", " + OrderPrice + ", " + Quantity + ", " + BuyAmount + ", " +
                            BuyPrice + ", " + BuyDate + ", " + BuyTime;

                    dataRecords.add(dataRecord);
                }
            }
        } catch (SQLException err) {
            System.err.println("Fehler beim Abrufen der Verkaufs-Records: " + err.getMessage());
            err.printStackTrace();
        }
    }

    public static void getDataRecordsPOS_WithMaxInMinus(String currency, List<String> dataRecords) {
        double LivePrice = Ticker.getAssetPrice(currency, bnb.getClient());
        
        try (Connection con = DriverManager.getConnection(dbUrl.getPOS());
             Statement query = con.createStatement()) {
            
            dataRecords.clear();

            // Zuerst versuchen, einen Datensatz mit Status = 7 und passender Währung zu finden
            String SQL = "SELECT BuyOrderId, OrderPrice, Qty, BuyAmount, BuyPrice, BuyDate, BuyTime FROM POS " +
                        "WHERE Status = 7 AND Währung = '" + currency + "'";
            
            try (ResultSet rs = query.executeQuery(SQL)) {
                boolean foundStatus7 = false;
                while (rs.next()) {
                    foundStatus7 = true;
                    addDataRecord(rs, dataRecords);
                }

                // Falls kein Datensatz mit Status = 7 gefunden wurde, dann Status = 1 verwenden
                if (!foundStatus7) {
                    SQL = "SELECT BuyOrderId, OrderPrice, Qty, BuyAmount, BuyPrice, BuyDate, BuyTime FROM POS " +
                          "WHERE Status = 1 AND Währung = '" + currency + "' " +
                          "AND BuyAmount > 10 " +
                          "ORDER BY (BuyPrice - " + LivePrice + ") DESC " +
                          "LIMIT 1";
                    
                    try (ResultSet rs2 = query.executeQuery(SQL)) {
                        while (rs2.next()) {
                            addDataRecord(rs2, dataRecords);
                        }
                    }
                }
            }
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    private static void addDataRecord(ResultSet rs, List<String> dataRecords) throws SQLException {
        String BuyOrderId = rs.getString("BuyOrderId");
        String OrderPrice = rs.getString("OrderPrice");
        String Quantity = rs.getString("Qty");
        String BuyAmount = rs.getString("BuyAmount");
        String BuyPrice = rs.getString("BuyPrice");
        String BuyDate = rs.getString("BuyDate");
        String BuyTime = rs.getString("BuyTime");

        String dataRecord = BuyOrderId + ", " + OrderPrice + ", " + Quantity + ", " + BuyAmount + ", " +
                BuyPrice + ", " + BuyDate + ", " + BuyTime;
        dataRecords.add(dataRecord);
    }

    public static void getPositionWithMaxInMinus(List<String> dataRecords, String currencyPair,
            BinanceApiRestClient client) {

        double LivePrice = Ticker.getAssetPrice(currencyPair, client);
        try {
            dataRecords.clear();
            Connection con = DriverManager.getConnection(dbUrl.getPOS());
            Statement query = con.createStatement();

            String SQL = "SELECT BuyOrderId, Qty, Währung, BuyPrice, BuyAmount FROM POS "
                    +
                    "WHERE Status = 1 " +
                    "AND BuyAmount >= 10 " +
                    "ORDER BY (BuyPrice - " + LivePrice + ") DESC " +
                    "LIMIT 1";

            ResultSet rs = query.executeQuery(SQL);

            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId"); // 0
                String Quantity = rs.getString("Qty"); // 1
                String currency = rs.getString("Währung"); // 2

                String dataRecord = BuyOrderId + ", " + Quantity + ", " + currency;

                dataRecords.add(dataRecord);
            }
            con.close();
            query.close();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }
}
