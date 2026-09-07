package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import java.util.List;
import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.tradingbot.HelperFunctions.round;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SETSQL;
import com.oneofx.fusion.tradingbot.Settings.CurrencyConfig;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import com.oneofx.fusion.tradingbot.Settings.set;
import com.oneofx.fusion.tradingbot.HelperFunctions.Asset;

public class BuyAmountFunktion {

    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();

    static final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";

    public static double getBuyAmount(String currencyPair, FusionApiClient client, List<Double> LivePrice,
            boolean wahr) {

        int grid = set.getGridforCurrency(currencyPair);
        double BuyPrice = currencyDAO.getAllTimeHigh(currencyPair);
        double unten = positionDAO.getLastDownSidePrice(currencyPair, LivePrice);

        double getBuyAmount = 0.0;
        boolean Loop1 = true;
        boolean Loop2 = true;
        int count_PositionToBottom = 0;
        double priceBottom = (currencyDAO.getAllTimeHigh(currencyPair) * 0.23);
        double minBuyAmount = currencyDAO.getMinBuyAmount(currencyPair);
        double Tax = histDAO.getTaxe();
        double freeBalance = Asset.getFree_Balance("USDC", client);

        while (Loop1) {
            while (Loop2) {

                BuyPrice = BuyPrice - ((BuyPrice / 100) / grid);
                if (((LivePrice.get(0) >= BuyPrice)) && (unten > BuyPrice)) {

                    count_PositionToBottom++;

                    if (priceBottom > BuyPrice) {
                        Loop2 = false;
                    }
                }
            }
            Loop1 = false;
        }

        getBuyAmount = (freeBalance - (count_PositionToBottom * minBuyAmount));

        if (minBuyAmount < getBuyAmount) {
            currencyDAO.setMinBuyAmount(currencyPair, minBuyAmount + 0.01);            
        }

        if (getBuyAmount < minBuyAmount) {
            getBuyAmount = minBuyAmount;
        } else if (getBuyAmount > 250) {
            getBuyAmount = currencyDAO.getMinBuyAmount(currencyPair);
        }
      
        return round.five(getBuyAmount);
    }

    public static double getsimplebuyamount(String currencyPair) {
        return round.two(currencyDAO.getMinBuyAmount(currencyPair));
    }

    public static double getBuyAmountFromStochRSI(String currency) {
        double[] stoch = currencyDAO.getStoch(currency);
        double k4h = stoch[0];
        SETSQL.compareBalanceWithFusion(FusionClientProvider.getClient());
        double totalBalance = SETSQL.getBalance_SQL();
        double one_PercentOfBalance = (totalBalance / 100);
        double buyAmount = ((101 - k4h) / 100) * one_PercentOfBalance;

        if (buyAmount > 500) {
            buyAmount = 37.0;
        } else if (buyAmount < 10.0) {
            buyAmount = 10.0;
        }

        return round.two(buyAmount);
    }
}
