package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.RiskRewardRatio.RRR;
import com.oneofx.fusion.tradingbot.RiskRewardRatio.CurrencyRRR;

public class PerformanceSQL {

    private static final PositionDAO positionDAO = new PositionDAO();

    public static double calculateCurrentDynamicStopLoss(String currency, String date) {
        try {
            String sql = "SELECT " +
                    "COALESCE(SUM(Profit), 0) as totalBuffer " +
                    "FROM Performance WHERE currency = ? " +
                    "AND date(CASE " +
                    "WHEN instr(SellDate, '.') > 0 THEN substr(SellDate, 7, 4) || '-' || substr(SellDate, 4, 2) || '-' || substr(SellDate, 1, 2) " +
                    "ELSE SellDate END) >= date('now', '-30 days')";

            double totalBuffer = 0.0;

            try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                    PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, currency);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    totalBuffer = rs.getDouble("totalBuffer");
                }
            }

            int openPositions = positionDAO.getCountPOS(currency);
            double baseStopLoss = SETSQL.getBaseStopLossPercent();
            return RRR.calculateDynamicStopLoss(totalBuffer, openPositions, baseStopLoss);

        } catch (Exception err) {
            System.err.println("Fehler beim Berechnen des dynamischen Stop-Loss: " + err.getMessage());
            return -2.0; // Fallback
        }
    }

    public static CurrencyRRR getWeightedRRRLast30Days(String currency) {
        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN Profit > 0 THEN Profit ELSE 0 END), 0) AS weightedWins, " +
                "COALESCE(ABS(SUM(CASE WHEN Profit < 0 THEN Profit ELSE 0 END)), 0) AS weightedLosses, " +
                "COUNT(CASE WHEN Profit > 0 THEN 1 END) AS countPositive, " +
                "COUNT(CASE WHEN Profit < 0 THEN 1 END) AS countNegative " +
                "FROM Performance " +
                "WHERE date(CASE " +
                "WHEN instr(SellDate, '.') > 0 THEN substr(SellDate, 7, 4) || '-' || substr(SellDate, 4, 2) || '-' || substr(SellDate, 1, 2) " +
                "ELSE SellDate END) >= date('now', '-30 days')";

        try (Connection con = DriverManager.getConnection(dbUrl.getoneOfX());
                PreparedStatement ps = con.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                double weightedWins   = rs.getDouble("weightedWins");
                double weightedLosses = rs.getDouble("weightedLosses");
                int countPos          = rs.getInt("countPositive");
                int countNeg          = rs.getInt("countNegative");
                return new CurrencyRRR(currency, weightedWins, weightedLosses, countPos, countNeg);
            }
        } catch (SQLException e) {
            System.err.println("getWeightedRRRLast30Days Fehler: " + e.getMessage());
        }
        return new CurrencyRRR(currency, 0, 0, 0, 0);
    }
}
