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
import com.oneofx.fusion.tradingbot.grid.GridCalculator;
import com.oneofx.fusion.tradingbot.grid.GridSettings;

public class BuyAmountFunktion {

    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();

    public static double getBuyAmount(String currencyPair, FusionApiClient client, List<Double> LivePrice,
            boolean wahr) {

        GridSettings grid = set.getGridSettings(currencyPair);
        double anchorPrice = currencyDAO.getAllTimeHigh(currencyPair);
        double unten = positionDAO.getLastDownSidePrice(currencyPair, LivePrice);

        double getBuyAmount = 0.0;
        int count_PositionToBottom = 0;
        double priceBottom = anchorPrice * 0.23;
        double minBuyAmount = currencyDAO.getMinBuyAmount(currencyPair);
        double Tax = histDAO.getTaxe();
        double freeBalance = Asset.getFree_Balance("USDC", client);

        for (long level = 1; level <= 100_000; level++) {
            double buyPrice = GridCalculator.level(anchorPrice, level, grid);
            if (buyPrice <= 0.0 || buyPrice < priceBottom) break;
            if (LivePrice.get(0) >= buyPrice && unten > buyPrice) {
                count_PositionToBottom++;
            }
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
