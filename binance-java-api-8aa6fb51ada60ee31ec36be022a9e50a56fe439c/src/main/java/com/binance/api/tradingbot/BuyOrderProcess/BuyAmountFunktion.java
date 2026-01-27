package com.binance.api.tradingbot.BuyOrderProcess;

import java.util.List;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.constants.TradingConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class BuyAmountFunktion {

    static final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";

    public static double getBuyAmount(String currencyPair, BinanceApiRestClient client, List<Double> LivePrice,
            boolean wahr) {

        int grid = set.getGridforCurrency(currencyPair);

        if (wahr) {
            SETSQL.CompareBalanceInSQLWithBinanceBalance(client);
        }

        double BuyPrice = ATHSQL.getAllTimeHigh(currencyPair);
        double unten = POSSQL.getLastPrice(currencyPair, LivePrice);

        double LPP = ATHSQL.getLPP(currencyPair);
        double getBuyAmount = 0.0;
        boolean Loop1 = true;
        boolean Loop2 = true;
        int count_PositionToBottom = 0;
        int count23 = 0;
        int count_20 = 0;
        int restPosition = 0;
        int currentPositionNumber = POSSQL.getCountPOS(currencyPair) + 1;
        double minBuyAmount = SETSQL.getminBuyAmount();
        // double maxBuyAmount = SETSQL.getmaxBuyAmount();
        double Tax = HISTSQL.getTaxe();
        double freeBalance = SETSQL.getBalance_SQL();

        double dailyAccumulatedAmount = calculateDailyAccumulatedAmount("01.01.2026", 5.0);

        freeBalance = (freeBalance - Tax - 5000);

        while (Loop1) {
            while (Loop2) {

                BuyPrice = BuyPrice - ((BuyPrice / 100) / grid);
                if (((LivePrice.get(0) >= BuyPrice)) && (unten > BuyPrice)) {

                    count_PositionToBottom++;

                    if (LPP > BuyPrice) {
                        Loop2 = false;
                    }
                }
            }
            Loop1 = false;
        }

        getBuyAmount = (freeBalance - (count_PositionToBottom * minBuyAmount));

        if (getBuyAmount < minBuyAmount) {
            getBuyAmount = minBuyAmount;
        }

        if (wahr) {
            if (minBuyAmount < getBuyAmount) {
                SETSQL.setminBuyAmount(minBuyAmount + 0.01);
            }
        }

        return getBuyAmount;
    }

    /**
     * Berechnet den akkumulierten Betrag basierend auf Tagen seit Startdatum.
     * Tag 1 (Startdatum) = addAmount, Tag 2 = 2*addAmount, usw.
     * 
     * @param startDate Startdatum im Format "dd.MM.yyyy"
     * @param addAmount Betrag der pro Tag addiert wird
     * @return Akkumulierter Betrag (Tag 1 = 5, Tag 2 = 10, Tag 3 = 15, ...)
     */
    public static double calculateDailyAccumulatedAmount(String startDate, double addAmount) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate today = LocalDate.now();

        long daysSinceStart = ChronoUnit.DAYS.between(start, today);

        // Tag 1 = Startdatum selbst, daher +1
        // Wenn Startdatum in Zukunft liegt, gib 0 zurück
        if (daysSinceStart < 0) {
            return 0.0;
        }

        return (daysSinceStart + 1) * addAmount;
    }
}