package com.binance.api.tradingbot.DCA;

import java.util.ArrayList;
import java.util.List;

import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.HelperFunctions.round;

public class dca {

    public static void executeDCA(String currency) {

        double DCA_Amount = SETSQL.getDCA_Amount();

        List<String> BuyAmountRecord = new ArrayList<String>();
        POSSQL.getDataRecordsPOS_WithMaxInMinus(currency, BuyAmountRecord);

        if (!BuyAmountRecord.isEmpty() && (DCA_Amount >= 0.01)) {

            String record = BuyAmountRecord.get(0);
            String[] recordParts = record.split(", ");         
            String BuyOrderId_POS = recordParts[0];
            String Quantity = recordParts[3];
            String BuyAmount = recordParts[4];
            String OrigBuyPrice = recordParts[5];

            double new_Buymount, new_BuyPrice;

            System.out.println("DCA Value: " + DCA_Amount);
            System.out.println("Old Position: " + BuyOrderId_POS + " - " + Quantity + " - " + BuyAmount + " - "
                    + OrigBuyPrice);

            new_Buymount = round.five(Double.valueOf(BuyAmount) - Double.valueOf(DCA_Amount));
            new_BuyPrice = round.five(new_Buymount / Double.valueOf(Quantity));

            if (new_Buymount < 6) {
                return;
            }

            System.out.println("New Position: " + BuyOrderId_POS + " - " + Quantity + " - " + new_Buymount + " - "
                    + new_BuyPrice);

            Merge.updateOrderPOS("POS", new_Buymount, new_BuyPrice, BuyOrderId_POS);
            Merge.updateOrderHIST("HIST", new_Buymount, new_BuyPrice, BuyOrderId_POS);

            SETSQL.setReserve(SETSQL.getReserve() - DCA_Amount);
        }
    }
}
