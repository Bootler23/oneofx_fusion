package com.oneofx.fusion.tradingbot.Indicator;

import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SETSQL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import com.oneofx.fusion.tradingbot.HelperFunctions.CompoundInterestCalculator;

public class Updates {

    private static final HistDAO histDAO = new HistDAO();

    public static void NewCounterPosition() {
        SETSQL.updateCount(histDAO.getCountHist());
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