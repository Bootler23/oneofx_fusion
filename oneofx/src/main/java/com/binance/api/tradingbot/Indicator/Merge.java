package com.binance.api.tradingbot.Indicator;

import java.util.List;

import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.SQL_Database.HistDAO;
import com.binance.api.tradingbot.SQL_Database.PositionDAO;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.domain.HistoryPosition;
import com.binance.api.tradingbot.domain.Position;

public class Merge {

    private static final PositionDAO positionDAO = new PositionDAO();
    private static final HistDAO histDAO = new HistDAO();

    public static void splitValue(String currency, List<String> SplitRecords) {
        boolean splitComplete = false;

        for (String dataRecord : SplitRecords) {
            if (splitComplete) {
                break;
            }

            String[] parts = dataRecord.split(", ");
            String BuyOrderId_Split = parts[0];
            String profitSplitValue = parts[1];
            String currency_Split = parts[2];
            double profitSplitValue_Double = Double.valueOf(profitSplitValue);

            List<String> BuyAmountRecord = positionDAO.getDataRecordsWithMaxInMinus(currency, Ticker.getAssetPrice(currency, bnb.getClient()));

            if (!BuyAmountRecord.isEmpty() && (profitSplitValue_Double > 0.01) && currency_Split.equals(currency)) { 
                                                                                  
                String record = BuyAmountRecord.get(0); 
                String[] recordParts = record.split(", ");              
                String BuyOrderId_POS = recordParts[0];               
                String Quantity = recordParts[3];
                String BuyAmount = recordParts[4];
                String BuyPrice = recordParts[5];

                double new_Buymount, new_BuyPrice;

                System.out.println("ProfitSplitValue: " + profitSplitValue);
                System.out.println("Old Position: " + BuyOrderId_POS + " - " + Quantity + " - " + BuyAmount + " - " + BuyPrice);

                new_Buymount = round.five(Double.valueOf(BuyAmount) - Double.valueOf(profitSplitValue));
                new_BuyPrice = round.five(new_Buymount / Double.valueOf(Quantity));
              
                if (new_Buymount < 6) {
                    return;
                }             

                System.out.println("New Position: " + BuyOrderId_POS + " - " + Quantity + " - " + new_Buymount + " - " + new_BuyPrice);

                positionDAO.update(new Position.Builder(currency, BuyOrderId_POS)
                        .buyPrice(new_BuyPrice)
                        .buyAmount(new_Buymount)
                        .status(7)
                        .build());

                histDAO.updateByBuyOrderId(new HistoryPosition.Builder(currency, BuyOrderId_POS)
                        .buyPrice(new_BuyPrice)
                        .buyAmount(new_Buymount)
                        .build());

                histDAO.updateByBuyOrderId(new HistoryPosition.Builder(null, BuyOrderId_Split)
                        .status(2)
                        .build());

                splitComplete = true;
            } else {
                histDAO.updateByBuyOrderId(new HistoryPosition.Builder(null, BuyOrderId_Split)
                        .status(2)
                        .build());
            }
        }
    }
}
