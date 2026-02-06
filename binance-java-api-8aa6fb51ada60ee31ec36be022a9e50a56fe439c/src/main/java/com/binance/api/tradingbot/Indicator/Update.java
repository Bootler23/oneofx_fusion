package com.binance.api.tradingbot.Indicator;

import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import com.binance.api.tradingbot.HelperFunctions.CompoundInterestCalculator;

public class Update {

    double minBuyAmount = SETSQL.getminBuyAmount();
    double percentToAdd = SETSQL.getPercentToAdd();

    public static void NewCounterPosition() {
        SETSQL.updateCount(HISTSQL.getcountHist());
    }

    public static void addminBuyAmount() {
        double minBuyAmount = SETSQL.getminBuyAmount();
        double percentToAdd = SETSQL.getPercentToAdd();

        if (percentToAdd <= 0) {
            percentToAdd = 0.001;
        }
        SETSQL.setminBuyAmount(minBuyAmount + (minBuyAmount * (percentToAdd / 100)));
    }

    public static void ratioBalanceToBA() {
        SETSQL.setratioBalanceToBA();
    }

    public static void calcPercentToAddForNextBuy() {

        int countPosition = SETSQL.getCount();
        int days = ((countPosition / getDaysPassedThisYear()) * 365);
        double DesiredAmount = SETSQL.getDesiredAmount();
        double minbuyamount = SETSQL.getminBuyAmount();
       
        if (minbuyamount <= 0 || DesiredAmount <= 0 || days <= 0 || countPosition <= 0) {
            return;
        }  
        SETSQL.setPercentToAdd(CompoundInterestCalculator.calculateRequiredDailyRate(minbuyamount, DesiredAmount, days));
    }

    public static int getDaysPassedThisYear() {
        LocalDate startOfYear = LocalDate.ofYearDay(LocalDate.now().getYear(), 1);
        LocalDate today = LocalDate.now();
        return (int) ChronoUnit.DAYS.between(startOfYear, today);
    }
}