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
        int count_20 = 0;
        int restPosition = 0;
        int currentPositionNumber = POSSQL.getCountPOS(currencyPair) + 1;
        double minBuyAmount = SETSQL.getMinBuyAmount();

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

            // Pyramidische Berechnung
            // -----------------------------------------------------------------------------------

            // Gesamt-Positionen = bereits gekaufte + verbleibende
            // int totalPositions = (currentPositionNumber - 1) + count_PositionToBottom;

            // // Pyramiden-Berechnung
            // double minAmount = TradingConstants.MIN_BUY_AMOUNT;
            // double inkrement = 0.0;

            // if (totalPositions > 1) {
            // // Formel: inkrement = 2 * (Budget - minAmount * n) / (n * (n-1))
            // inkrement = 2.0 * (freeBalance - minAmount * totalPositions)
            // / (totalPositions * (totalPositions - 1));
            // }

            // // Falls Budget zu klein, kein Inkrement (alle Positionen = minAmount)
            // if (inkrement < 0) {
            // inkrement = 0;
            // }

            // // BuyAmount = minAmount + (positionsNummer - 1) * inkrement
            // getBuyAmount = minAmount + (currentPositionNumber - 1) * inkrement;

            // -----------------------------------------------------------------------------------

            count_20 = (int) (count_PositionToBottom * 0.2);

            restPosition = count_PositionToBottom - count_20;

            freeBalance = ((freeBalance + dailyAccumulatedAmount) - (restPosition * 5.5));

            // freeBalance = (freeBalance - ((count_PositionToBottom - 23) * 40.0));

            // getBuyAmount = freeBalance / count_20;

            // if (getBuyAmount >= (minBuyAmount / (1 - 0.30))) {
            // SETSQL.setMinBuyAmount(minBuyAmount + 0.1);
            // getBuyAmount = minBuyAmount;
            // }

            // freeBalance = freeBalance - (32 * count_PositionToBottom);

            getBuyAmount = (freeBalance / count_20);

            Loop1 = false;
        }

        if (getBuyAmount < TradingConstants.MIN_BUY_AMOUNT) {
            getBuyAmount = 5.5;
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