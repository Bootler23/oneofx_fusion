package com.binance.api.tradingbot.RiskRewardRatio;

import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;

public class RRR {
   
    private static final double MAX_STOP_LOSS = -5.0;  // Niemals weiter als -5%
    private static final double MIN_STOP_LOSS = -0.5;  // Niemals enger als -0.5%

    public static double calculateRiskRewardRatioToday(final String Url, String tableName) {
        return getRiskRewardRatio(Url, tableName);
    }

    private static double getRiskRewardRatio(final String Url, String tableName) {
        double profit = HISTSQL.getSumColumnToday(Url, "GewinnAfterTax", tableName);
        double loss = HISTSQL.getSumColumnToday(Url, "LossAfterTax", tableName);
        return calculateRiskRewardRatio(profit, loss);
    }

    public static double calculateRiskRewardRatio(double win, double loss) {
        if (loss == 0) {
            return win;
        } else
            return round.two(win / (-loss));
    }

    public static double calculateDynamicStopLoss(double totalBuffer, int openPositions, double baseStopLoss) {        
       
        if (totalBuffer < -30.0) {           
            return MIN_STOP_LOSS;
        } else if (totalBuffer < -15.0) {            
            return -1.0;
        } else if (totalBuffer < 0.0) {           
            return -1.5;
        }
        
        // POSITIVER BUFFER: Stop-Loss kann erweitert werden
        if (openPositions <= 0) {
            // Keine offenen Positionen: Basis-Stop-Loss verwenden
            return baseStopLoss;
        }
        
        // Verfügbarer Buffer pro Position berechnen
        double availablePerPosition = totalBuffer / openPositions;
        
        // 75% des verfügbaren Buffers für Stop-Loss-Bonus nutzen (konservativ)
        double bonus = availablePerPosition * 0.75;
        
        // Dynamischer Stop-Loss = Basis + Bonus
        // Beispiel: -2.0% + 1.5% = -3.5% (weiterer Stop-Loss)
        double dynamicStopLoss = baseStopLoss - bonus;
        
        // Safety-Limits anwenden: Zwischen -5% und -0.5%
        return Math.max(MAX_STOP_LOSS, Math.min(MIN_STOP_LOSS, dynamicStopLoss));
    }
   
    public static double calculateMaxLossTrades(double stopLossPercent, double totalWinAmount) {
        if (stopLossPercent == 0) {
            return Double.MAX_VALUE; // Theoretisch unendlich viele Trades
        }
        return totalWinAmount / Math.abs(stopLossPercent);
    }
   
    public static double getRRRatio(double winPercent, double lossPercent) {
        if (lossPercent == 0) {
            return winPercent;
        }
        return round.three(winPercent / Math.abs(lossPercent));
    }
   
    public static double calculateRequiredWinRate(double winPercent, double lossPercent) {
        if (winPercent + lossPercent == 0) {
            return 0.0;
        }
        return round.two((lossPercent / (winPercent + lossPercent)) * 100.0);
    }
}

