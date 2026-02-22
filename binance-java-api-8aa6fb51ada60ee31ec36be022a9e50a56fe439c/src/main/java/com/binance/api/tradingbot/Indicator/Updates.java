package com.binance.api.tradingbot.Indicator;

import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import com.binance.api.tradingbot.HelperFunctions.CompoundInterestCalculator;

public class Updates {

    double minBuyAmount = SETSQL.getminBuyAmount();
    double percentToAdd = SETSQL.getPercentToAdd();

    public static void NewCounterPosition() {
        SETSQL.updateCount(HISTSQL.getcountHist());
    }

    public static void addminBuyAmount() {
        double minBuyAmount = SETSQL.getminBuyAmount();
        double percentToAdd = SETSQL.getPercentToAdd();
        double newMinBuyAmount = (minBuyAmount + (minBuyAmount * (percentToAdd / 100)));

        if (minBuyAmount <= 0 || percentToAdd <= 0 || newMinBuyAmount <= 0) {
            return;
        }

        SETSQL.setminBuyAmount(newMinBuyAmount);
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

    public static void setExpectationCounter() {
        int countPosition = SETSQL.getCount();
        int expectationCounter = ((countPosition / getDaysPassedThisYear()) * 365);

        if (expectationCounter <= 0 || countPosition <= 0) {
           return;
        }

        SETSQL.setExpectationCounter(expectationCounter);
    } 
}