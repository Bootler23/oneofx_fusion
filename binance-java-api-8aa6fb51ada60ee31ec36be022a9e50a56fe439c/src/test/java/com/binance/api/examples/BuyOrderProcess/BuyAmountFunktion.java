package com.binance.api.examples.BuyOrderProcess;

import java.util.List;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.examples.HelperFunctions.round;
import com.binance.api.examples.SQL_Database.ATHSQL;
import com.binance.api.examples.SQL_Database.HISTSQL;
import com.binance.api.examples.SQL_Database.POSSQL;
import com.binance.api.examples.SQL_Database.SETSQL;

public class BuyAmountFunktion {

    static final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";

    public static double getBuyAmount(String currencyPair, String EURO, int Grid, BinanceApiRestClient client,
            final String ATH, final String POS, final String SET, List<Double> LivePrice, boolean wahr) {

        if (wahr) {
            SETSQL.CompareBalanceInSQLWithBinanceBalance(SET, EURO, client);
        }

        // keine setzen von BNB Positionen
        if (currencyPair == "BNBEUR") {
            return 0.0;
        }

        double BuyPrice = ATHSQL.getAllTimeHigh(currencyPair, ATH);
        double unten = POSSQL.getLastPrice(currencyPair, POS, LivePrice);

        double LPP = ATHSQL.getLPP(ATH, currencyPair);
        double getBuyAmount = 0.0;
        boolean Loop1 = true;
        boolean Loop2 = true;

        int count_PositionToBottom = 0;
        int count_PositionTo20Percent = 0;
        int rest_Postion = 0;

        double Tax = HISTSQL.getTaxe(HIST);
        double freeBalance = SETSQL.getBalance_SQL(SET);

        freeBalance = freeBalance - Tax;
        double LPPTest = (LivePrice.get(0) * 0.93);

        while (Loop1) {
            while (Loop2) {

                BuyPrice = BuyPrice - ((BuyPrice / 100) / Grid);
                if (((LivePrice.get(0) >= BuyPrice)) && (unten > BuyPrice)) {

                    count_PositionToBottom++;

                    if (LPPTest < BuyPrice) {
                        count_PositionTo20Percent++;
                    }

                    if (38.80 > BuyPrice || LPP > BuyPrice) {
                        Loop2 = false;
                    }
                }
            }

            SETSQL.checkForNewBuyAmount(currencyPair, SET, (round.three((freeBalance) / count_PositionToBottom)),
                    LivePrice.get(0));

            rest_Postion = count_PositionToBottom - count_PositionTo20Percent;

            freeBalance = (freeBalance - (rest_Postion * 5.5));

            getBuyAmount = (freeBalance / 23);

            // getBuyAmount = ((freeBalance - (count_PositionToBottom * 5.5)) / 23);

            Loop1 = false;
        }

        if (getBuyAmount > SETSQL.getBuyAmount(SET)) {
            getBuyAmount = SETSQL.getBuyAmount(SET);
        }

        if (getBuyAmount < 5.5) {
            getBuyAmount = 5.5;
        }

        return getBuyAmount;
    }
}