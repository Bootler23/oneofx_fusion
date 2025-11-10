package com.binance.api.tradingbot.BuyOrderProcess;

import java.util.List;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.constants.TradingConstants;

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
        double minBuyAmount = SETSQL.getMinBuyAmount();

        double LPP = ATHSQL.getLPP(currencyPair);
        double getBuyAmount = 0.0;
        boolean Loop1 = true;
        boolean Loop2 = true;
        int count_PositionToBottom = 0;
        int count_20 = 0;
        int count_all = 0;
        int restPosition = 0;

        double Tax = HISTSQL.getTaxe();
        double freeBalance = SETSQL.getBalance_SQL();
        double freeBalance2 = 0.0;

        freeBalance = (freeBalance - Tax);
        freeBalance2 = freeBalance;

        while (Loop1) {
            while (Loop2) {

                count_all++;

                BuyPrice = BuyPrice - ((BuyPrice / 100) / grid);
                if (((LivePrice.get(0) >= BuyPrice)) && (unten > BuyPrice)) {

                    count_PositionToBottom++;

                    if (LPP > BuyPrice) {
                        Loop2 = false;
                    }
                }
            }

            count_20 = (int) (count_PositionToBottom * 0.23);

            restPosition = count_PositionToBottom - count_20;

            freeBalance = (freeBalance - (restPosition * minBuyAmount));

            getBuyAmount = freeBalance / count_20;

            if (getBuyAmount >= (minBuyAmount / (1 - 0.30))) {
                SETSQL.setMinBuyAmount(minBuyAmount + 0.1);
                getBuyAmount = minBuyAmount;
            }

            getBuyAmount = (freeBalance2 / count_PositionToBottom);

            Loop1 = false;
        }

        if (getBuyAmount < TradingConstants.MIN_BUY_AMOUNT) {
            getBuyAmount = 5.5;
        }

        return getBuyAmount;
    }
}