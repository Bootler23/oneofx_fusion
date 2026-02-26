package com.binance.api.tradingbot.SQL_Database;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.RiskRewardRatio.RRR;

public class PerformanceSQL {

    public static double calculateCurrentDynamicStopLoss(String currency, String date) {
        try {
            String sql = "SELECT " +
                    "COALESCE(SUM(Profit * SellAmount) / NULLIF(SUM(SellAmount), 0), 0) as weightedBuffer " +
                    "FROM Performance WHERE Währung = ? AND SellDate >= date('now', '-30 days')";

            double totalBuffer = 0.0;

            try (Connection con = DriverManager.getConnection(dbUrl.getPerformance());
                    PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, currency);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    double weightedBuffer = rs.getDouble("weightedBuffer");
                    totalBuffer = weightedBuffer;
                }
            }

            int openPositions = POSSQL.getCountPOS(currency);
            double baseStopLoss = SETSQL.getBaseStopLossPercent();
            return RRR.calculateDynamicStopLoss(totalBuffer, openPositions, baseStopLoss);

        } catch (Exception err) {
            System.err.println("Fehler beim Berechnen des dynamischen Stop-Loss: " + err.getMessage());
            return -2.0; // Fallback
        }
    }

    public static double getCurrentWeightedRRR(String currency, String date) {
        String sql = "SELECT " +
                "SUM(CASE WHEN Profit > 0 THEN Profit * SellAmount ELSE 0 END) as weightedWins, " +
                "ABS(SUM(CASE WHEN Profit < 0 THEN Profit * SellAmount ELSE 0 END)) as weightedLosses " +
                "FROM Performance WHERE Währung = ? AND SellDate = ?";

        try (Connection con = DriverManager.getConnection(dbUrl.getPerformance());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, currency);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                double wins = rs.getDouble("weightedWins");
                double losses = rs.getDouble("weightedLosses");

                if (losses == 0)
                    return wins; // Nur Gewinne
                return new BigDecimal(wins / losses).setScale(2, RoundingMode.HALF_UP).doubleValue();
            }
        } catch (SQLException e) {
            System.err.println("RRR-Fehler: " + e.getMessage());
        }
        return 0.0;
    }
}
