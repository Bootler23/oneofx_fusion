package com.oneofx.fusion.tradingbot.SQL_Database;

import com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CurrencySQL {

    private static final Logger logger = LoggerFactory.getLogger(CurrencySQL.class);

    private static final int STALE_THRESHOLD_SECONDS = 60;

    public static void saveStochRSI(String currency,
            double k4h,  double d4h,
            double k2h,  double d2h,
            double k12h, double d12h,
            double k1d,  double d1d,
            double k3d,  double d3d,
            double k1w,  double d1w,
            double k1m,  double d1m,
            double k5m,  double d5m,
            double cci4h,
            double atr4h,
            double rsi4h,
            double volume24h,
            double emaFast,
            double emaSlow) {
        String sql = "UPDATE currency SET " +
                "k4h = ?, d4h = ?, k2h = ?, d2h = ?, " +
                "k12h = ?, d12h = ?, k1d = ?, d1d = ?, " +
                "k3d = ?, d3d = ?, k1w = ?, d1w = ?, " +
                "k1m = ?, d1m = ?, k5m = ?, d5m = ?, " +
                "CCI = ?, ATR5m = ?, RSI4h = ?, " +
                "volume24h = ?, emafast = ?, emaslow = ?, " +
                "updateTime = datetime('now', 'localtime') " +
                "WHERE currency = ?";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1,  k4h);
            ps.setDouble(2,  d4h);
            ps.setDouble(3,  k2h);
            ps.setDouble(4,  d2h);
            ps.setDouble(5,  k12h);
            ps.setDouble(6,  d12h);
            ps.setDouble(7,  k1d);
            ps.setDouble(8,  d1d);
            ps.setDouble(9,  k3d);
            ps.setDouble(10, d3d);
            ps.setDouble(11, k1w);
            ps.setDouble(12, d1w);
            ps.setDouble(13, k1m);
            ps.setDouble(14, d1m);
            ps.setDouble(15, k5m);
            ps.setDouble(16, d5m);
            ps.setDouble(17, cci4h);
            ps.setDouble(18, atr4h);
            ps.setDouble(19, rsi4h);
            ps.setDouble(20, volume24h);
            ps.setDouble(21, emaFast);
            ps.setDouble(22, emaSlow);
            ps.setString(23, currency);
            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("Fehler beim Speichern der StochRSI-Werte für " + currency + ": " + e.getMessage());
        }
    }

    public static boolean isStale(String currency) {
        String sql = "SELECT CASE WHEN updateTime IS NULL OR (strftime('%s', datetime('now', 'localtime')) - strftime('%s', updateTime)) > ? THEN 1 ELSE 0 END AS stale FROM currency WHERE currency = ?";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
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

    /**
     * Liest alle Indikator-Werte aus der Datenbank.
     *
     * Rückgabe-Array (Index → Spalte):
     *  [0]=k4h  [1]=d4h
     *  [2]=k2h  [3]=d2h
     *  [4]=k12h [5]=d12h
     *  [6]=k1d  [7]=d1d
     *  [8]=k3d  [9]=d3d
     * [10]=k1w [11]=d1w
     * [12]=k1m [13]=d1m
     * [14]=k5m [15]=d5m
     * [16]=CCI (4h)
     * [17]=ATR5m (5m)
     * [18]=RSI4h
     * [19]=emafast (EMA 20)
     * [20]=emaslow (EMA 50)
     */
    public static double[] getStochRSI(String currency) {
        String sql = "SELECT k4h, d4h, k2h, d2h, k12h, d12h, k1d, d1d, k3d, d3d, k1w, d1w, k1m, d1m, k5m, d5m, CCI, ATR5m, RSI4h, emafast, emaslow " +
                     "FROM currency WHERE currency = ?";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{
                        rs.getDouble("k4h"),
                        rs.getDouble("d4h"),
                        rs.getDouble("k2h"),
                        rs.getDouble("d2h"),
                        rs.getDouble("k12h"),
                        rs.getDouble("d12h"),
                        rs.getDouble("k1d"),
                        rs.getDouble("d1d"),
                        rs.getDouble("k3d"),
                        rs.getDouble("d3d"),
                        rs.getDouble("k1w"),
                        rs.getDouble("d1w"),
                        rs.getDouble("k1m"),
                        rs.getDouble("d1m"),
                        rs.getDouble("k5m"),
                        rs.getDouble("d5m"),
                        rs.getDouble("CCI"),
                        rs.getDouble("ATR5m"),
                        rs.getDouble("RSI4h"),
                        rs.getDouble("emafast"),
                        rs.getDouble("emaslow")
                    };
                }
            }
        } catch (Exception e) {
            System.out.println("Fehler beim Lesen der StochRSI-Werte für " + currency + ": " + e.getMessage());
        }

        return new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    }

    // ---- Trailing Stop Loss (TSL) --------------------------------------------

    public static boolean getTSL(String currency) {
        String sql = "SELECT TSL FROM tradeSettings WHERE currency = ?";
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("TSL");
                    return "true".equalsIgnoreCase(val) || "1".equals(val);
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von TSL für {}: {}", currency, e.getMessage());
        }
        return false;
    }

    public static double getTSLActivate(String currency) {
        String sql = "SELECT TSL_activate FROM tradeSettings WHERE currency = ?";
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                return rs.getDouble("TSL_activate");
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von TSLactivate für {}: {}", currency, e.getMessage());
        }
        return 2.0;
    }

    public static double getTSLDecline(String currency) {
        String sql = "SELECT TSL_decline FROM tradeSettings WHERE currency = ?";
        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                return rs.getDouble("TSL_decline");
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von TSLdecline für {}: {}", currency, e.getMessage());
        }
        return 1.0;
    }

    public static boolean updateBalanceInfo(String currency, double exchange, double database, double differenz) {
        String sql = "UPDATE currency SET exchange = ?, database = ?, differenz = ? WHERE currency = ?";

        try (Connection con = SQLiteConnectionFactory.open(dbUrl.getoneOfX());
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, round.eight(exchange));
            ps.setDouble(2, round.eight(database));
            ps.setDouble(3, round.eight(differenz));
            ps.setString(4, currency);

            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            logger.error("Fehler beim Speichern der Balance-Info fuer {}: {}", currency, e.getMessage());
            return false;
        }
    }
}
