package com.binance.api.tradingbot.BuyOrderProcess;

import java.util.List;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.set;

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
        // int currentPositionNumber = POSSQL.getCountPOS(currencyPair) + 1;
        double minBuyAmount = SETSQL.getminBuyAmount();
        // double maxBuyAmount = SETSQL.getmaxBuyAmount();
        double Tax = HISTSQL.getTaxe();
        double freeBalance = SETSQL.getBalance_SQL();     

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
        return round.five(getBuyAmount);
    }

    public static double getsimplebuyamount() {
        double minBuyAmount = SETSQL.getminBuyAmount();
        double AVGminBuyAmount = POSSQL.getAverageBuyAmount();

        if (minBuyAmount <= 0 || minBuyAmount > (AVGminBuyAmount * 3)) {
            minBuyAmount = round.two(POSSQL.getAverageBuyAmount());
        }
        return round.two(minBuyAmount);
    }
}