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
        ensureCurrencyPairExists(dbUrl.getATH(), Currency);
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT ATH FROM ATH WHERE `Währung` = '" + Currency + "'")) {
            if (rs.next()) {
                return RoundCurrency.forTickerPrice(rs.getDouble("ATH"), Currency);
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
            setAllTimeHigh(currency, dbUrl.getATH(), LivePrice.get(0));
            System.out.println("New ATH small function");
        }
    }

    private static void setAllTimeHigh(String CurrencyPair, final String ATH, double newValue) {
        try (Connection con = DriverManager.getConnection(ATH);
                Statement stmt = con.createStatement()) {
            String SQL = "UPDATE ATH SET ATH = " + newValue + " WHERE `Währung` = '" + CurrencyPair + "'";

            stmt.executeUpdate(SQL);
        } catch (SQLException err) {
            System.out.println("Update failed: " + err.getMessage());
        }
    }

    public static double getLPP(String currencyPair) {
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                Statement query = con.createStatement();
                ResultSet rs = query.executeQuery("SELECT LPP FROM ATH WHERE `Währung` = '" + currencyPair + "'")) {
            return round.two(rs.getDouble("LPP"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 0.0;
        }
    }

    public static void updateLPP(String currencyPair) {
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                Statement statement = con.createStatement()) {

            String updateSQL = "UPDATE ATH SET LPP = " + CalcNewLPP(currencyPair) + " WHERE `Währung` = '"
                    + currencyPair + "'";
            statement.executeUpdate(updateSQL);

        } catch (SQLException err) {
            System.out.println("Fehler beim Aktualisieren der LPP-Spalte: " + err.getMessage());
        }
    }

    public static double CalcNewLPP(String currencyPair) {
        double LastPossiblePrice = getLPP(currencyPair);

        double AllTimeHighMinus80Percent = (ATHSQL.getAllTimeHigh(currencyPair) / 100) * 20;

        if (AllTimeHighMinus80Percent > LastPossiblePrice) {
            LastPossiblePrice += 0.01;
        } else {
            LastPossiblePrice -= 0.01;
        }
        return round.three(LastPossiblePrice);
    }

    public static void ensureCurrencyPairExists(final String SQL, String currencyPair) {
        try (Connection con = DriverManager.getConnection(SQL);
                Statement stmt = con.createStatement()) {

            // Prüfen, ob das Currency Pair existiert
            String checkQuery = "SELECT COUNT(*) AS count FROM ATH WHERE `Währung` = '" + currencyPair + "'";
            ResultSet rs = stmt.executeQuery(checkQuery);

            if (rs.next() && rs.getInt("count") == 0) {
                // Wenn das Currency Pair nicht existiert, einfügen mit Wert 0.000000001
                String insertQuery = "INSERT INTO ATH (`Währung`, ATH, LPP) VALUES ('" + currencyPair
                        + "', 0.000000001, 1)";
                stmt.executeUpdate(insertQuery);
                System.out.println("Currency Pair hinzugefügt: " + currencyPair);
            }
        } catch (SQLException err) {
            System.out.println("Fehler beim Prüfen/Hinzufügen des Currency Pair: " + err.getMessage());
        }
    }

    public static double GetHighestBuyAmount(String currency) {
        String sql = "SELECT HighestBuyAmount FROM ATH WHERE Währung = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double val = rs.getDouble("HighestBuyAmount");
                    return rs.wasNull() ? 0.0 : val;
                }
            }

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }

        return 0.0;
    }

    public static void setHighestBuyAmount(String currency, double BuyAmount) {
        String sql = "UPDATE ATH SET HighestBuyAmount = ? WHERE Währung = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.two(BuyAmount));
            ps.setString(2, currency);

            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }

    public static double getMinBuyAmount(String currencyPair) {
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                Statement query = con.createStatement();
                ResultSet rs = query
                        .executeQuery("SELECT MinBuyAmount FROM ATH WHERE `Währung` = '" + currencyPair + "'")) {
            return round.two(rs.getDouble("MinBuyAmount"));
        } catch (SQLException err) {
            System.out.println(err.getMessage());
            return 5.5;
        }
    }

    public static void setMinBuyAmount(String currency, double BuyAmount) {
        String sql = "UPDATE ATH SET MinBuyAmount = ? WHERE Währung = ?";
        try (Connection con = DriverManager.getConnection(dbUrl.getATH());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.two(BuyAmount));
            ps.setString(2, currency);

            ps.executeUpdate();

        } catch (SQLException err) {
            System.out.println(err.getMessage());
        }
    }
}
