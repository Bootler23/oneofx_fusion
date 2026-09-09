package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;

/**
 * DAO für die currency-Tabelle. Vereint ATHSQL + CurrencySQL
 * in einer Klasse mit PreparedStatements überall.
 */
public class CurrencyDAO {

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl.getoneOfX());
    }

    // ===================== ATH (aus ATHSQL) =====================

    public double getAllTimeHigh(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT alltimehigh FROM currency WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return TradingRulesFormatter.formatPrice(currency, rs.getDouble("alltimehigh"));
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Holen des ATH: " + e.getMessage());
        }
        return 0.0;
    }

    public void checkForNewAllTimeHigh(String currency, List<Double> livePrice) {
        if (livePrice.get(0) > getAllTimeHigh(currency)) {
            setAllTimeHigh(currency, livePrice.get(0));
            System.out.println("New ATH small function");
        }
    }

    public void setAllTimeHigh(String currency, double newValue) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE currency SET alltimehigh = ? WHERE currency = ?")) {
            ps.setDouble(1, newValue);
            ps.setString(2, currency);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim ATH-Update: " + e.getMessage());
        }
    }

    public void ensureCurrencyPairExists(String currencyPair) {
        try (Connection con = getConnection();
             PreparedStatement check = con.prepareStatement("SELECT COUNT(*) AS count FROM currency WHERE currency = ?")) {
            check.setString(1, currencyPair);
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt("count") == 0) {
                try (PreparedStatement insert = con.prepareStatement("INSERT INTO currency (currency, alltimehigh) VALUES (?, 0.000000001)")) {
                    insert.setString(1, currencyPair);
                    insert.executeUpdate();
                    System.out.println("Currency Pair hinzugefügt: " + currencyPair);
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Prüfen/Hinzufügen: " + e.getMessage());
        }
    }

    // ===================== StochRSI (aus CurrencySQL) =====================

    private static final int STALE_THRESHOLD_SECONDS = 60;

    public void saveStochRSI(String currency,
            double k4h,  double d4h,
            double k2h,  double d2h,
            double k12h, double d12h,
            double k1d,  double d1d,
            double k3d,  double d3d,
            double k1w,  double d1w,
            double k1m,  double d1m,
            double k5m,  double d5m,
            double cci4h, double atr4h, double rsi4h,
            double volume24h, double emaFast, double emaSlow) {

        String sql = "UPDATE currency SET " +
                "k4h = ?, d4h = ?, k2h = ?, d2h = ?, " +
                "k12h = ?, d12h = ?, k1d = ?, d1d = ?, " +
                "k3d = ?, d3d = ?, k1w = ?, d1w = ?, " +
                "k1m = ?, d1m = ?, k5m = ?, d5m = ?, " +
                "CCI = ?, ATR5m = ?, RSI4h = ?, " +
                "volume24h = ?, emafast = ?, emaslow = ?, " +
                "updateTime = datetime('now', 'localtime') " +
                "WHERE currency = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1,  k4h);  ps.setDouble(2,  d4h);
            ps.setDouble(3,  k2h);  ps.setDouble(4,  d2h);
            ps.setDouble(5,  k12h); ps.setDouble(6,  d12h);
            ps.setDouble(7,  k1d);  ps.setDouble(8,  d1d);
            ps.setDouble(9,  k3d);  ps.setDouble(10, d3d);
            ps.setDouble(11, k1w);  ps.setDouble(12, d1w);
            ps.setDouble(13, k1m);  ps.setDouble(14, d1m);
            ps.setDouble(15, k5m);  ps.setDouble(16, d5m);
            ps.setDouble(17, cci4h);
            ps.setDouble(18, atr4h);
            ps.setDouble(19, rsi4h);
            ps.setDouble(20, volume24h);
            ps.setDouble(21, emaFast);
            ps.setDouble(22, emaSlow);
            ps.setString(23, currency);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Fehler beim Speichern der StochRSI-Werte für " + currency + ": " + e.getMessage());
        }
    }

    public boolean isStale(String currency) {
        String sql = "SELECT CASE WHEN updateTime IS NULL OR (strftime('%s', datetime('now', 'localtime')) - strftime('%s', updateTime)) > ? THEN 1 ELSE 0 END AS stale FROM currency WHERE currency = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, STALE_THRESHOLD_SECONDS);
            ps.setString(2, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("stale") == 1;
        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
        }
        return true;
    }

    public double[] getStoch(String currency) {
        String sql = "SELECT k4h, d4h, k1d, d1d FROM currency WHERE currency = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new double[]{
                    rs.getDouble("k4h"),  rs.getDouble("d4h"),
                    rs.getDouble("k1d"),  rs.getDouble("d1d")
                };
            }
        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
        }
        return new double[4];
    }

    /**
     * Speichert k4h, d4h, k1d, d1d und setzt updateTime auf jetzt.
     * Wird jede Minute aus der Main-Loop aufgerufen.
     */
    public void saveStochValues(String currency, double k4h, double d4h, double k1d, double d1d, double k5m, double d5m) {
        String sql = "UPDATE currency SET k4h = ?, d4h = ?, k1d = ?, d1d = ?, k5m = ?, d5m = ?, " +
                     "updateTime = datetime('now', 'localtime') WHERE currency = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, k4h);
            ps.setDouble(2, d4h);
            ps.setDouble(3, k1d);
            ps.setDouble(4, d1d);
            ps.setDouble(5, k5m);
            ps.setDouble(6, d5m);
            ps.setString(7, currency);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[FEHLER] saveStochValues (" + currency + "): " + e.getMessage());
        }
    }

    // ---- Trailing Stop Loss ----

    public boolean getTSL(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT TSL FROM tradeSettings WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString("TSL");
                return "true".equalsIgnoreCase(val) || "1".equals(val);
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Lesen von TSL: " + e.getMessage());
        }
        return false;
    }

    public double getTSLActivate(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT TSL_activate FROM tradeSettings WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("TSL_activate");
        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
        }
        return 2.0;
    }

    public double getTSLDecline(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT TSL_decline FROM tradeSettings WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("TSL_decline");
        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
        }
        return 1.0;
    }

    /**
     * Liefert den konfigurierten harten Stop-Abstand in Prozent. Null deaktiviert
     * den Stop. Ältere Datenbanken speichern zwei Prozent als 2 oder -2, deshalb
     * wird immer der Betrag des gespeicherten Werts verwendet.
     */
    public double getStopLossPercent(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT SL FROM tradeSettings WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double value = Math.abs(rs.getDouble("SL"));
                return Double.isFinite(value) && value < 100.0 ? value : 0.0;
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Lesen des Stop-Loss fuer " + currency + ": " + e.getMessage());
        }
        return 0.0;
    }

    public boolean updateBalanceInfo(String currency, double exchange, double database, double differenz) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE currency SET exchange = ?, database = ?, differenz = ? WHERE currency = ?")) {
            ps.setDouble(1, round.eight(exchange));
            ps.setDouble(2, round.eight(database));
            ps.setDouble(3, round.eight(differenz));
            ps.setString(4, currency);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Fehler: " + e.getMessage());
            return false;
        }
    }

    // ---- minBuyAmount (pro Währung) ----

    public double getMinBuyAmount(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT buyAmount FROM currency WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return round.five(rs.getDouble("buyAmount"));
        } catch (SQLException e) {
            System.err.println("Fehler beim Lesen von minBuyAmount für " + currency + ": " + e.getMessage());
        }
        return 5.5;
    }

    public void setMinBuyAmount(String currency, double amount) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE currency SET buyAmount = ? WHERE currency = ?")) {
            ps.setDouble(1, round.six(amount));
            ps.setString(2, currency);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Setzen von minBuyAmount für " + currency + ": " + e.getMessage());
        }
    }

    // ---- maxBuyAmount (pro Währung) ----

    public double getMaxBuyAmount(String currency) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT maxBuyAmount FROM currency WHERE currency = ?")) {
            ps.setString(1, currency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return round.two(rs.getDouble("maxBuyAmount"));
        } catch (SQLException e) {
            System.err.println("Fehler beim Lesen von maxBuyAmount für " + currency + ": " + e.getMessage());
        }
        return 500.0;
    }

    public void setMaxBuyAmount(String currency, double amount) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE currency SET maxBuyAmount = ? WHERE currency = ?")) {
            ps.setDouble(1, round.two(amount));
            ps.setString(2, currency);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Setzen von maxBuyAmount für " + currency + ": " + e.getMessage());
        }
    }
}
