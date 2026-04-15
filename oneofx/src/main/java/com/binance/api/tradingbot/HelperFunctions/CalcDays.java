package com.binance.api.tradingbot.HelperFunctions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class CalcDays {

    public static long fromDate(String startDatum) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate start = LocalDate.parse(startDatum, formatter);
        LocalDate heute = LocalDate.now();

        return ChronoUnit.DAYS.between(start, heute);
    }
}
