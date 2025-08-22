package com.binance.api.tradingbot.BuyOrderProcess;

import java.util.List;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.Settings.set;


public class BuyAmountFunktion {

    static final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";

    public static double getBuyAmount(String currencyPair, String EURO, BinanceApiRestClient client, List<Double> LivePrice, boolean wahr) {

        int grid = set.getGridforCurrency(currencyPair);

        if (wahr) {
            SETSQL.CompareBalanceInSQLWithBinanceBalance(EURO, client);
        }

        // keine setzen von BNB Positionen
        if (currencyPair == "BNBEUR") {
            return 0.0;
        }

        double BuyPrice = ATHSQL.getAllTimeHigh(currencyPair);
        double unten = POSSQL.getLastPrice(currencyPair, LivePrice);

        double LPP = ATHSQL.getLPP(dbUrl.getATH(), currencyPair);
        double getBuyAmount = 0.0;
        boolean Loop1 = true;
        boolean Loop2 = true;

        int count_PositionToBottom = 0;
        int count_PositionTo20Percent = 0;
        int rest_Postion = 0;

        double Tax = HISTSQL.getTaxe();
        double freeBalance = SETSQL.getBalance_SQL();

        freeBalance = freeBalance - Tax;
        double LPPTest = (LivePrice.get(0) * 0.93);

        while (Loop1) {
            while (Loop2) {

                BuyPrice = BuyPrice - ((BuyPrice / 100) / grid);
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

            rest_Postion = count_PositionToBottom - count_PositionTo20Percent;

            freeBalance = (freeBalance - (rest_Postion * 5.5));

            getBuyAmount = (freeBalance / 23);    
            
            SETSQL.checkForNewBuyAmount(currencyPair, getBuyAmount, LivePrice.get(0));


            Loop1 = false;
        }

        if (getBuyAmount > SETSQL.getBuyAmount()) {
            getBuyAmount = SETSQL.getBuyAmount();
        }

        if (getBuyAmount < 5.5) {
            getBuyAmount = 5.5;
        }

        return getBuyAmount;
    }
}