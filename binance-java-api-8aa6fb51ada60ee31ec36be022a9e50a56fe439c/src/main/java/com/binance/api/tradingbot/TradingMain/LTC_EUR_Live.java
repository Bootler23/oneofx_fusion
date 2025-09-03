package com.binance.api.tradingbot.TradingMain;

import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.HourlySchedulerExample;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.Indicator.MACD;
import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SQL_Database.WPDSQL;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;
import com.binance.api.tradingbot.SellOrderProcess.Update;
import com.binance.api.tradingbot.HelperFunctions.CalcPercenToSell;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.Settings.bnb;

import java.util.List;
import java.util.ArrayList;

public class LTC_EUR_Live {
    public static void main(String[] args) {

        while (true) {
            try {

                String[] currencies = {"LTCEUR"};
                String currency = "";
                int state = 0;

                String EURO = "EUR";
             
                int count = 0;
                boolean FirstRound = true;
                boolean StartStop = true;

                //double percent = 0.5; // +% mit wieviel die Position verkauft wird
                // soll dynamisch gea,cht werden genausso wie die % bei dem Verkauf
                // Primzahlen bis ab 2 -> 2, 3, 5, 7, 11, 13, 17, 19, 23,
                // 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79,
                // 100 -> 101, 103, 107, 109, 113, 127, 131, 137, 139, 149,
                // 151, 157, 163, 167, 173,

                // ----------------------------------------------------------------------------------------------------------------------

                // ToDo´s ---> BIG THREE <---

                // BuyAmountFunktion -> wie kann ich relativ hohe Buy-Orders generieren?
                    // Jeden Tag die Größte Position verkaufen -> abschneiden der verluste
                        // -> ggf. bei einem Absturz -7% verkaufen -> wenn sie vorher schon weg sind durch abschneiden wäre das gut !!!

                        // jeden Tag die Position welche am weitesten im Minus ist verkaufen
                            // wenn die Position unter -7% fällt -> verkaufen ???
                                // Wenn es kein Geld mehr gibt -> verkaufen von oben
                                    // 1% vom Balance als BuyPrice ???
                                        // -> derzeit nach einem Low -> dauert Lange bis wieder Geld Liquide wird von oben

                // DatenBankelogik ???

                // WPD einfacher gestalten -> Logik überarbeiten
                
                // percent dynamisch vom count der Position erstellen. wenig Positionen -> viel percent -> viel Positionen wenig percent
                    // 0.5% -> x%              

                List<Long> OrderIdList = new ArrayList<Long>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();  
                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                while (StartStop) {

                    state = set.Currency(currencies, state);
                    currency = currencies[state];                  

                    sleep.for_1_second();

                    Ticker.get_CurrencyPair_Price(currency, bnb.getClient(), LivePrice);

                    ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);

                    BuyAmountFunktion.getBuyAmount(currency, EURO, bnb.getClient(), LivePrice, false);

                    ATHSQL.updateLPP(currency);

                    if (count == 42 || FirstRound) {

                        HISTSQL.get_SellTrade_Records_WhereStatusZero(getDataRecords);                    
                        Update.getSellTradeInformation(bnb.getClient(), getDataRecords);                           

                        POSSQL.get_BuyTrade_Records_WhereStatusFive(getDataRecords);   
                        Update.getBuyTradeInformation(bnb.getClient(), getDataRecords);   

                        SETSQL.CompareBalanceInSQLWithBinanceBalance(EURO, bnb.getClient());

                        HISTSQL.getDataRecords_WhereStatusOne(getDataRecords);
                        Merge.splitValue(currency, getDataRecords);
                                    
                        // Wieviel ist mein Portfolio im Minus
                        WPDSQL.getGewinnAfterTax();                     

                        count = 0;
                        
                        FirstRound = false;

                        Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient());

                        System.out.println("Percent: " + CalcPercenToSell.PercentToSell(currency));

                        //MACD.printMACD(bnb.getClient(), currency, CandlestickInterval.DAILY);
                    }    
                    //MACD.getMACD(bnb.getClient(), currency, CandlestickInterval.HOURLY);
                  
                    // verkaufe die die am weitestem im Minus ist
                    //HourlySchedulerExample.executeHourly();
                    //SellAsset.Three_TimesPerDay(currency, bnb.getClient());

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
