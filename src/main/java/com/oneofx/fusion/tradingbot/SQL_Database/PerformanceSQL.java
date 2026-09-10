package com.oneofx.fusion.tradingbot.SQL_Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.RiskRewardRatio.RRR;

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

            try (Connection con = com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
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

}
