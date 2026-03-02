package com.binance.api.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.RoundCurrency;
import com.binance.api.tradingbot.HelperFunctions.round;

public class ATHSQL {

    public static double getAllTimeHigh(String Currency) {
        ensureCurrencyPairExists(dbUrl.getCurrency(), Currency);
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT alltimehigh FROM currency WHERE `currency` = '" + Currency + "'")) {
            if (rs.next()) {
                return RoundCurrency.forTickerPrice(rs.getDouble("alltimehigh"), Currency);
            } else {
                return 0.0;
            }
        } catch (SQLException err) {
            System.out.println("Fehler beim holen der ATH Preis " + err.getMessage());
            return 0.0;
        }
    }

    public static double getAllTimeHighoneOfX(String Currency) {
        ensureCurrencyPairExists(dbUrl.getoneOfX(), Currency);
        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT alltimehigh FROM currency WHERE `currency` = '" + Currency + "'")) {
            if (rs.next()) {
                return RoundCurrency.forTickerPrice(rs.getDouble("alltimehigh"), Currency);
            } else {
                return 0.0;
            }
        } catch (SQLException err) {
            System.out.println("Fehler beim holen der ATH Preis " + err.getMessage());
            return 0.0;
        }
    }

    public static void CheckForNewAllTimeHigh(String currency, List<Double> LivePrice) {
        if (LivePrice.get(0) > getAllTimeHigh(currency)) {
            setAllTimeHigh(currency, dbUrl.getoneOfX(), LivePrice.get(0));
            System.out.println("New ATH small function");
        }
    }

    private static void setAllTimeHigh(String CurrencyPair, final String ATH, double newValue) {
        try (Connection con = DriverManager.getConnection(ATH);
                Statement stmt = con.createStatement()) {
            String SQL = "UPDATE currency SET alltimehigh = " + newValue + " WHERE `currency` = '" + CurrencyPair + "'";

            stmt.executeUpdate(SQL);
        } catch (SQLException err) {
            System.out.println("Update failed: " + err.getMessage());
        }
    }

    public static void ensureCurrencyPairExists(final String SQL, String currencyPair) {
        try (Connection con = DriverManager.getConnection(SQL);
                Statement stmt = con.createStatement()) {

            String checkQuery = "SELECT COUNT(*) AS count FROM currency WHERE `currency` = '" + currencyPair + "'";
            ResultSet rs = stmt.executeQuery(checkQuery);

            if (rs.next() && rs.getInt("count") == 0) {
                String insertQuery = "INSERT INTO currency (`currency`, alltimehigh) VALUES ('" + currencyPair
                        + "', 0.000000001)";
                stmt.executeUpdate(insertQuery);
                System.out.println("Currency Pair hinzugefügt: " + currencyPair);
            }
        } catch (SQLException err) {
            System.out.println("Fehler beim Prüfen/Hinzufügen des Currency Pair: " + err.getMessage());
        }
    }
}
