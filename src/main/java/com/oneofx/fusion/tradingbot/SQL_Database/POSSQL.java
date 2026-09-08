package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;

public class POSSQL {

    public static double getLastDownSidePrice(String currencyPair, List<Double> LivePrice) {
        double PriceMin = Double.MAX_VALUE;
        boolean foundPriceInDB = false;

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement(
                        "SELECT OrderPrice FROM positions WHERE Status IN (0, 1, 5) AND Währung = ?")) {

            pstmt.setString(1, currencyPair);
            ResultSet rs = pstmt.executeQuery();

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

    public static boolean positionExistsAtPrice(String currencyPair, double price) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement(
                        "SELECT COUNT(*) as count FROM positions WHERE Status IN (0, 1, 5) AND Währung = ? AND ABS(OrderPrice - ?) < 0.0001")) {

            pstmt.setString(1, currencyPair);
            pstmt.setDouble(2, price);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt("count");
                return count > 0; // true = Position existiert → kein Kauf
            }
        } catch (SQLException err) {
            System.out.println("SQL-Fehler in positionExistsAtPrice: " + err.getMessage());
            return true; // Safe Default: bei DB-Fehler Kauf blockieren
        }
        return false;
    }
    

    public static void get_BuyOrderId_WhereStatusZero(List<Long> orderIdList, String currencyPair) {
        try {
            orderIdList.clear();
            try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                    Statement query = con.createStatement()) {

                String SQL = "SELECT BuyOrderId FROM positions WHERE Status = 0 AND Währung = '" + currencyPair + "'";
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
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT BuyOrderId, Währung FROM positions WHERE Status = 5")) {

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
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement statement = con.createStatement()) {

            String SQL = "SELECT SUM(BuyAmount) AS SumBuyAmount FROM positions";
            ResultSet rs = statement.executeQuery(SQL);

            if (rs.next()) {
                return rs.getDouble("SumBuyAmount");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der LPP-Spalte: " + err.getMessage());
        }
        return 0.0;
    }

    public static double getSumQuantity() {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement statement = con.createStatement()) {

            String SQL = "SELECT SUM(quantity) AS SumQuantity FROM positions";
            ResultSet rs = statement.executeQuery(SQL);

            if (rs.next()) {
                return rs.getDouble("SumQuantity");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Abrufen der SumQuantity aus POS: " + err.getMessage());
        }
        return 0.0;
    }
    
    public static double getSumQuantityForCurrency(String currencyPair) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement pstmt = con.prepareStatement(
                        "SELECT SUM(quantity) AS SumQuantity FROM positions WHERE Status IN (0, 1, 5) AND Währung = ?")) {

            pstmt.setString(1, currencyPair);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble("SumQuantity");
            }

        } catch (SQLException err) {
            System.out.println("Fehler beim Abrufen der SumQuantity für " + currencyPair + ": " + err.getMessage());
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
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement statement = con.createStatement()) {

            String SQL = "SELECT COUNT(*) AS RecordCount FROM positions WHERE Status IN (0, 1) AND Währung = '" + currencyPair
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

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement()) {

            String SQL = "SELECT AVG(BuyAmount) AS AverageBuyAmount FROM positions";
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
            try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                    PreparedStatement pstmt = con.prepareStatement(
                            "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate, BuyTime " +
                                    "FROM positions WHERE Status IN (1, 7) AND Währung = ? AND BuyAmount > 0")) {

                // "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate,
                // BuyTime " +
                // "FROM POS WHERE Status = 11 AND Währung = ?")) {

                pstmt.setString(1, CurrencyPair);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String BuyOrderId = rs.getString("BuyOrderId"); // 0
                    String OrderPrice = rs.getString("OrderPrice"); // 1
                    String OrigPrice = rs.getString("OrigPrice"); // 2
                    String Quantity = rs.getString("quantity"); // 3
                    String BuyAmount = rs.getString("BuyAmount"); // 4
                    String BuyPrice = rs.getString("BuyPrice"); // 5
                    String BuyDate = rs.getString("BuyDate"); // 6
                    String BuyTime = rs.getString("BuyTime"); // 7

                    String dataRecord = BuyOrderId + ", " + OrderPrice + ", " + OrigPrice + ", " + Quantity + ", "
                            + BuyAmount + ", " +
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

        double LivePrice = Ticker.getAssetPrice(currency, FusionClientProvider.getClient());

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement()) {

            dataRecords.clear();

            // Zuerst versuchen, einen Datensatz mit Status = 7 und passender Währung zu
            // finden
            String SQL = "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate, BuyTime FROM positions "
                    +
                    "WHERE Status = 7 AND Währung = '" + currency + "'";

            try (ResultSet rs = query.executeQuery(SQL)) {
                boolean foundStatus7 = false;
                while (rs.next()) {
                    foundStatus7 = true;
                    addDataRecord(rs, dataRecords);
                }

                // Falls kein Datensatz mit Status = 7 gefunden wurde, dann Status = 1 verwenden
                if (!foundStatus7) {
                    SQL = "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice, BuyDate, BuyTime FROM positions "
                            +
                            "WHERE Status = 1 AND Währung = '" + currency + "' " +
                            "AND BuyAmount > 10 " +
                            "ORDER BY (BuyPrice - " + LivePrice + ") DESC " +
                            "LIMIT 1";
                    // SQL = "SELECT BuyOrderId, OrderPrice, OrigPrice, quantity, BuyAmount, BuyPrice,
                    // BuyDate, BuyTime FROM POS "
                    // +
                    // "WHERE Status = 1 AND Währung = '" + currency + "' " +
                    // "AND BuyAmount > 10 " +
                    // "ORDER BY (BuyPrice - " + LivePrice + ") ASC " +
                    // "LIMIT 1";

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
        String OrigPrice = rs.getString("OrigPrice");
        String Quantity = rs.getString("quantity");
        String BuyAmount = rs.getString("BuyAmount");
        String BuyPrice = rs.getString("BuyPrice");
        String BuyDate = rs.getString("BuyDate");
        String BuyTime = rs.getString("BuyTime");

        String dataRecord = BuyOrderId + ", " + OrderPrice + ", " + OrigPrice + ", " + Quantity + ", " + BuyAmount
                + ", " +
                BuyPrice + ", " + BuyDate + ", " + BuyTime;
        dataRecords.add(dataRecord);
    }

    // ---- Trailing Stop Loss peakPrice ----------------------------------------

    public static double getPeakPrice(String buyOrderId) {
        String sql = "SELECT peakPrice FROM positions WHERE BuyOrderId = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, buyOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("peakPrice");
                }
            }
        } catch (SQLException e) {
            System.out.println("Fehler beim Lesen von peakPrice: " + e.getMessage());
        }
        return 0.0;
    }

    public static void updatePeakPrice(String buyOrderId, double peakPrice) {
        String sql = "UPDATE positions SET peakPrice = ? WHERE BuyOrderId = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, peakPrice);
            ps.setString(2, buyOrderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Fehler beim Aktualisieren von peakPrice: " + e.getMessage());
        }
    }

    public static void getPositionWithMaxInMinus(List<String> dataRecords, String currencyPair,
            FusionApiClient client) {

        double LivePrice = Ticker.getAssetPrice(currencyPair, client);
        try {
            dataRecords.clear();
            Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
            Statement query = con.createStatement();

            String SQL = "SELECT BuyOrderId, quantity, Währung, BuyPrice, BuyAmount FROM positions "
                    +
                    "WHERE Status = 1 " +
                    "AND BuyAmount >= 10 " +
                    "ORDER BY (BuyPrice - " + LivePrice + ") DESC " +
                    "LIMIT 1";

            ResultSet rs = query.executeQuery(SQL);

            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId"); // 0
                String Quantity = rs.getString("quantity"); // 1
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

    public static void getTwoPositions(String currency, List<String> dataRecords) {
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement()) {

            dataRecords.clear();
            String SQL = "SELECT BuyOrderId, quantity, Währung, BuyPrice, BuyAmount FROM positions " +
                    "WHERE Status = 1 " +
                    "AND BuyAmount < 10 " +
                    "AND Währung = '" + currency + "'";

            ResultSet rs = query.executeQuery(SQL);

            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId");
                String Quantity = rs.getString("quantity");
                String currencyFromDB = rs.getString("Währung");
                String BuyPrice = rs.getString("BuyPrice");
                String BuyAmount = rs.getString("BuyAmount");

                String dataRecord = BuyOrderId + ", " + Quantity + ", " + currencyFromDB + ", " + BuyPrice + ", "
                        + BuyAmount;

                dataRecords.add(dataRecord);
            }
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void getPositionSmallerThen10AndMinus7Percent(String currency, List<String> dataRecords) {
        double LivePrice = Ticker.getAssetPrice(currency, FusionClientProvider.getClient());

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement()) {

            dataRecords.clear();
            String SQL = "SELECT * FROM positions WHERE Währung = '" + currency
                    + "' AND BuyAmount < 10 AND Status = 1 AND (BuyPrice - " + LivePrice + ") / BuyPrice >= 0.07";
            ResultSet rs = query.executeQuery(SQL);

            while (rs.next()) {
                String BuyOrderId = rs.getString("BuyOrderId");
                String Quantity = rs.getString("quantity");
                String currencyFromDB = rs.getString("Währung");
                String BuyPrice = rs.getString("BuyPrice");
                String BuyAmount = rs.getString("BuyAmount");

                String dataRecord = BuyOrderId + ", " + Quantity + ", " + currencyFromDB + ", " + BuyPrice + ", "
                        + BuyAmount;
                dataRecords.add(dataRecord);
            }
        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static void mergePosition(String currency, List<String> dataRecords) {

        double TotalBuyAmount = 0.0;
        double TotalBuyPrice = 0.0;
        double TotalQuantity = 0.0;

        for (String record : dataRecords) {
            String[] parts = record.split(", ");

            String BuyOrderId_POS = parts[0];
            long BuyOrderId_POS_Long = Long.parseLong(BuyOrderId_POS);
            String Quantity = parts[1];

            double BuyPrice = Double.parseDouble(parts[3]);
            double BuyAmount = Double.parseDouble(parts[4]);

            TotalBuyAmount += BuyAmount;
            TotalBuyPrice += BuyPrice;
            TotalQuantity += Double.parseDouble(Quantity);
        }

        // updateOrderPOS("POS", TotalBuyAmount, round.five(TotalBuyAmount /
        // TotalQuantity), String.valueOf(dataRecords.get(0).split(", ")[0]));

    }

}
