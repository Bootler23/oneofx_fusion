package com.binance.api.examples.TradingMain;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.examples.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.examples.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.examples.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.examples.BuyOrderProcess.Ticker;
import com.binance.api.examples.HelperFunctions.Asset;
import com.binance.api.examples.HelperFunctions.HourlySchedulerExample;
import com.binance.api.examples.HelperFunctions.Time;
import com.binance.api.examples.HelperFunctions.sleep;
import com.binance.api.examples.MarketSell_OneTime.Market;
import com.binance.api.examples.SQL_Database.ATHSQL;
import com.binance.api.examples.SQL_Database.HISTSQL;
import com.binance.api.examples.SQL_Database.POSSQL;
import com.binance.api.examples.SQL_Database.SETSQL;
import com.binance.api.examples.SQL_Database.WPDSQL;
import com.binance.api.examples.SellAsset.SellAsset;
import com.binance.api.examples.SellOrderProcess.Update;
import com.binance.api.examples.Settings.set;
import com.binance.api.examples.SellOrderProcess.SellOrderProcess;

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

                int grid;
                int count = 0;
                boolean FirstRound = true;
                boolean StartStop = true;

                double percent = 1.31; // +% mit wieviel die Position verkauft wird
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

                // ----------------------------------------------------------------------------------------------------------------------

                // -> Set New Key 01/2024
                String key = "3p2AemKbKyaKUocMgQuMY442UaAgScPriHcSsS57fvt5y1iP5LLCV2jqQVovQBNv";

                // -> Set New Secret 01/2024
                String secret = "HZXnE4kFzRHPHO8Dr7B9v4HkLocCCG3UmuRIhxZaHlm3i24mA3Fei9kCv3Kq1zGI";

                BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(key, secret);
                BinanceApiRestClient client = factory.newRestClient();

                final String SET = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/SETTING.db";
                final String ATH = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ATH_LTCEUR.db";
                final String POS = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR.db";
                final String HIST = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/POS_LTCEUR_HIST.db";
                // final String EXPO =
                // "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/ExpoTag.db";
                final String WPD = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/WPD.db";

                List<Long> OrderIdList = new ArrayList<Long>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();
                
                

                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                while (StartStop) {

                    state = set.Currency(currencies, state);
                    currency = currencies[state];

                    grid = set.getGridforCurrency(currency);

                    sleep.for_1_second();

                    Ticker.get_CurrencyPair_Price(currency, client, LivePrice);                  

                    ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);

                    BuyAmountFunktion.getBuyAmount(currency, EURO, grid, client, ATH, POS, SET, LivePrice, false);

                    ATHSQL.updateLPP(ATH, currency);

                    if (count == 41 || FirstRound) {

                        HISTSQL.get_SellTrade_Records_WhereStatusZero(HIST, getDataRecords);                    
                        Update.getSellTradeInformation(client, getDataRecords, POS, HIST);                            
                       
                        POSSQL.get_BuyTrade_Records_WhereStatusFive(POS, getDataRecords);   
                        Update.getBuyTradeInformation(client, getDataRecords, POS, HIST);   
                        
                        SETSQL.CompareBalanceInSQLWithBinanceBalance(SET, EURO, client);

                        // Wieviel ist mein Portfolio im Minus
                        WPDSQL.getGewinnAfterTax(HIST, WPD, SET);                     

                        count = 0;
                        
                        FirstRound = false;

                        Asset.getBNB_Balance("BNBEUR", "BNB", client);                       
                    }                  

                    Market.SellOneTimePerDay(client, POS, HIST);  
                    HourlySchedulerExample.executeHourly(currency);
                    SellAsset.Three_TimesPerDay(client); 

                    count++;

                    // Buy
                    BuyOrderPocess.setBuyOrder(currency, EURO, grid, client, ATH, POS, SET, HIST,
                            LivePrice);

                    // Check
                    POSSQL.get_BuyOrderId_WhereStatusZero(POS, OrderIdList, currency);
                    CheckOrderStatus.OrderStatus(currency, grid, client, POS, HIST, OrderIdList, ATH, SET,
                            LivePrice);

                    // // Sell                  
                    POSSQL.getDataRecords_WhereStatusOne(currency, POS, getDataRecords);  
                    SellOrderProcess.setSellOrder(currency, percent, client, POS, HIST, SET,
                            getDataRecords, LivePrice);
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
