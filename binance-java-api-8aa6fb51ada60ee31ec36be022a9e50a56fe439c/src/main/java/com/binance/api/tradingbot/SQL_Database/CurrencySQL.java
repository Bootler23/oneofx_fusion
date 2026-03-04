package com.binance.api.tradingbot.SQL_Database;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.RiskRewardRatio.CurrencyRRR;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CurrencySQL {

    private static final int STALE_THRESHOLD_SECONDS = 60;

    public static void saveStochRSI(String currency, double k4h, double d4h, double k2h, double d2h) {
        String sql = "UPDATE currency SET k4h = ?, d4h = ?, k2h = ?, d2h = ?, updateTime = datetime('now') WHERE currency = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, k4h);
            ps.setDouble(2, d4h);
            ps.setDouble(3, k2h);
            ps.setDouble(4, d2h);
            ps.setString(5, currency);
            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("Fehler beim Speichern der StochRSI-Werte für " + currency + ": " + e.getMessage());
        }
    }

    public static boolean isStale(String currency) {
        String sql = "SELECT CASE WHEN updateTime IS NULL OR (strftime('%s','now') - strftime('%s', updateTime)) > ? THEN 1 ELSE 0 END AS stale FROM currency WHERE currency = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, STALE_THRESHOLD_SECONDS);
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stale") == 1;
                }
            }
        } catch (Exception e) {
            System.out.println("Fehler beim Prüfen der StochRSI-Aktualität für " + currency + ": " + e.getMessage());
        }

        // Im Fehlerfall als veraltet behandeln → Aktualisierung auslösen
        return true;
    }

    public static double[] getStochRSI(String currency) {
        String sql = "SELECT k4h, d4h, k2h, d2h FROM currency WHERE currency = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{
                        rs.getDouble("k4h"),
                        rs.getDouble("d4h"),
                        rs.getDouble("k2h"),
                        rs.getDouble("d2h")
                    };
                }
            }
        } catch (Exception e) {
            System.out.println("Fehler beim Lesen der StochRSI-Werte für " + currency + ": " + e.getMessage());
        }

        return new double[]{0.0, 0.0, 0.0, 0.0};
    }

    public static CurrencyRRR getCurrencyRRR(String currency) {
        return PerformanceSQL.getWeightedRRRLast30Days(currency);
    }
}
