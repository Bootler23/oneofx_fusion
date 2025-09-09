package com.binance.api.tradingbot.TradingMain;

import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.HourlySchedulerExample;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SQL_Database.WPDSQL;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;
import com.binance.api.tradingbot.SellOrderProcess.Update;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.Settings.CurrencyConfig;

import java.util.List;
import java.util.ArrayList;

public class LTC_EUR_Live {
    public static void main(String[] args) {

        while (true) {
            try {

                String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();             
                String currency = "";
                int state = 0;

                String EURO = "EUR";
             
                int count = 0;
                boolean FirstRound = true;
                boolean StartStop = true;             

                // ----------------------------------------------------------------------------------------------------------------------

                // ToDo´s ---> BIG THREE <---

                // Wenn BNB gleich alle dann nachkaufen.

                // DatenBankelogik ???

                // WPD einfacher gestalten -> Logik überarbeiten
                
                // percent dynamisch vom count der Position erstellen. wenig Positionen -> viel percent -> viel Positionen wenig percent
                    // 0.5% -> x%              

                List<Long> OrderIdList = new ArrayList<Long>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();  
                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                while (StartStop) {

                    state = set.Currency(BuyCurrencies, state);
                    currency = BuyCurrencies[state];                  

                    sleep.for_1_second();

                    Ticker.get_CurrencyPair_Price(currency, bnb.getClient(), LivePrice);

                    ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);

                    BuyAmountFunktion.getBuyAmount(currency, EURO, bnb.getClient(), LivePrice, false);

                    ATHSQL.updateLPP(currency);

                    if (count == 41 || FirstRound) {

                        HISTSQL.get_SellTrade_Records_WhereStatusZero(getDataRecords);                    
                        Update.getSellTradeInformation(bnb.getClient(), getDataRecords);                           

                        POSSQL.get_BuyTrade_Records_WhereStatusFive(getDataRecords);   
                        Update.getBuyTradeInformation(bnb.getClient(), getDataRecords);   

                        SETSQL.CompareBalanceInSQLWithBinanceBalance(EURO, bnb.getClient());

                        HISTSQL.getDataRecords_WhereStatusOne(currency,getDataRecords);
                        Merge.splitValue(currency, getDataRecords);

                        // POSSQL.getPositionSmallerThen10AndMinus7Percent(currency, getDataRecords);
                        // POSSQL.mergePosition(currency, getDataRecords);

                        // Wieviel ist mein Portfolio im Minus
                        WPDSQL.getGewinnAfterTax();                     

                        count = 0;
                        
                        FirstRound = false;

                        Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient());
                    }                      
                  
                    // verkaufe die die am weitestem im Minus ist
                    HourlySchedulerExample.executeHourly();

                    count++;

                    // Buy
                    BuyOrderPocess.setBuyOrder(currency, EURO, bnb.getClient(), LivePrice);

                    // Check
                    POSSQL.get_BuyOrderId_WhereStatusZero(OrderIdList, currency);
                    CheckOrderStatus.OrderStatus(currency, bnb.getClient(), OrderIdList, LivePrice);

                    // // Sell                  
                    POSSQL.getDataRecords_WhereStatusOneOrSeven(currency, getDataRecords);
                    SellOrderProcess.setSellOrder(currency, bnb.getClient(), getDataRecords, LivePrice);
                }

            } catch (IndexOutOfBoundsException e) {
                String FehlerMessage = "Fehler: Index out of bounds! Der Fehler liegt in einem leerem Array irgendwo in dem Code!";
                System.err.println(FehlerMessage);
                System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                sleep.for_60_seconds();
                continue;
            }
        }
    }
}
