package com.binance.api.examples.RiskRewardRatio;

import com.binance.api.examples.HelperFunctions.round;
import com.binance.api.examples.SQL_Database.HISTSQL;

public class RRR {

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
}
