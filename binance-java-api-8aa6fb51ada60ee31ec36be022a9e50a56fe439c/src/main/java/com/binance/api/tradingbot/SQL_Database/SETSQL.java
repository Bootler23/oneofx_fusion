package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.empty;
import com.binance.api.tradingbot.HelperFunctions.round;

public class SETSQL {

    public static void CompareBalanceInSQLWithBinanceBalance(final String SET, String currency,
            BinanceApiRestClient client) {
        double BNB_Balance = Asset.getFreeCalced_Balance(currency, client);
        System.out.print(":");
        if ((getBalance_SQL(SET) != (BNB_Balance) && (BNB_Balance > 1.0))) {
            setBalance(SET, BNB_Balance);
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

    public static double getBalance_SQL(final String SET) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT Balance FROM SETTING")) {
            return round.two(rs.getDouble("Balance"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double get_newBuyAmount(final String SET) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT newBuyAmount FROM SETTING")) {
            return round.three(rs.getDouble("newBuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static void checkForNewBuyAmount(String currencyPair, final String SET, double newBuyAmount,
            double LivePrice) {
        double currentBuyAmount = get_newBuyAmount(SET);
        if (newBuyAmount > currentBuyAmount) {
            EXPOSQL.insertnewBuyAmountEntry(currencyPair, newBuyAmount, LivePrice);
            try (Connection con = DriverManager.getConnection(SET);
                    Statement query = con.createStatement()) {
                String SQL = "UPDATE SETTING SET newBuyAmount = " + round.three(newBuyAmount);
                query.executeUpdate(SQL);
            } catch (SQLException err) {
                System.out.println(err.getMessage());
            }
        }
    }

    public static int getcountPart(final String SET) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT countPart FROM SETTING")) {
            return rs.getInt("countPart");
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0;
        }
    }

    public static int setcountPart(final String SET, int newValue) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET countPart = " + newValue;
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0;
        }
    }

    public static double setBalance(final String SET, double newValue) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET Balance = " + newValue;
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    // public static int getSwitch(final String SET) {
    // try (Connection con = DriverManager.getConnection(SET);
    // Statement query = con.createStatement();
    // ResultSet rs = query.executeQuery("SELECT Switch FROM SETTING")) {
    // return rs.getInt("Switch");
    // } catch (SQLException err) {
    // System.out.println(err.getMessage());
    // return 0;
    // }
    // }

    // public static int setSwitch(final String SET, int newValue) {
    // try (Connection con = DriverManager.getConnection(SET);
    // Statement query = con.createStatement()) {
    // String SQL = "UPDATE SETTING SET Switch = " + newValue;
    // query.executeUpdate(SQL);
    // return newValue;
    // } catch (SQLException err) {
    // System.out.println(err.getMessage());
    // return 0;
    // }
    // }

    public static double getBuyAmount(final String SET) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT BuyAmount FROM SETTING")) {
            return round.two(rs.getDouble("BuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static double setBuyAmount(final String SET, double newValue) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement()) {
            String SQL = "UPDATE SETTING SET BuyAmount = " + round.two(newValue);
            query.executeUpdate(SQL);
            return newValue;
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static int getCount(final String SET) {
        try (Connection con = DriverManager.getConnection(SET);
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT Count FROM SETTING")) {
            return rs.getInt("Count");
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0;
        }
    }

    public static void updateCount(final String SET, int newCountValue) {
        try (Connection con = DriverManager.getConnection(SET);
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
}