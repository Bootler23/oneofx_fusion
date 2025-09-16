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

    public static double getBuyAmount(String currencyPair, String EURO, BinanceApiRestClient client,
            List<Double> LivePrice, boolean wahr) {

        int grid = set.getGridforCurrency(currencyPair);

        if (wahr) {
            SETSQL.CompareBalanceInSQLWithBinanceBalance(EURO, client);
        }

        double BuyPrice = ATHSQL.getAllTimeHigh(currencyPair);
        double unten = POSSQL.getLastPrice(currencyPair, LivePrice);
        // double minBuyAmount = round.two(ATHSQL.getMinBuyAmount(currencyPair));

        double LPP = ATHSQL.getLPP(currencyPair);
        double getBuyAmount = 0.0;
        boolean Loop1 = true;
        boolean Loop2 = true;
        int count_PositionToBottom = 0;     

        double Tax = HISTSQL.getTaxe();
        double freeBalance = SETSQL.getBalance_SQL();
        double Reserve = SETSQL.getReserve();

        freeBalance = (freeBalance - Tax);     

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

            freeBalance = (freeBalance - (5.5 * count_PositionToBottom) - Reserve);
            getBuyAmount = freeBalance;

            Loop1 = false;
        }

        // if (ATHSQL.GetHighestBuyAmount(currencyPair) < getBuyAmount) {
        //     ATHSQL.setHighestBuyAmount(currencyPair, getBuyAmount);
        //     ATHSQL.setMinBuyAmount(currencyPair, (ATHSQL.getMinBuyAmount(currencyPair) + 0.1));
        // }      

        if (getBuyAmount < 5.5) {
            getBuyAmount = 5.5;
        }

        return getBuyAmount;
    }
}