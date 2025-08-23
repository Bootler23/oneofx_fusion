package com.binance.api.tradingbot.TradingMain;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.HourlySchedulerExample;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.MarketSell_OneTime.Market;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SQL_Database.WPDSQL;
import com.binance.api.tradingbot.SellAsset.SellAsset;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;
import com.binance.api.tradingbot.SellOrderProcess.Update;
import com.binance.api.tradingbot.Settings.set;

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

                    BuyAmountFunktion.getBuyAmount(currency, EURO, client, LivePrice, false);
                    
                    ATHSQL.updateLPP(currency);

                    if (count == 41 || FirstRound) {

                        HISTSQL.get_SellTrade_Records_WhereStatusZero(getDataRecords);                    
                        Update.getSellTradeInformation(client, getDataRecords);                            
                       
                        POSSQL.get_BuyTrade_Records_WhereStatusFive(getDataRecords);   
                        Update.getBuyTradeInformation(client, getDataRecords);   
                        
                        SETSQL.CompareBalanceInSQLWithBinanceBalance(EURO, client);

                        // Wieviel ist mein Portfolio im Minus
                        WPDSQL.getGewinnAfterTax();                     

                        count = 0;
                        
                        FirstRound = false;

                        Asset.getBNB_Balance("BNBEUR", "BNB", client);                       
                    }                  

                    // verkaufe die die am weitestem im Minus ist
                    HourlySchedulerExample.executeHourly(currency);                    
                    SellAsset.Three_TimesPerDay(client);

                    count++;

                    // Buy
                    BuyOrderPocess.setBuyOrder(currency, EURO, client, LivePrice);

                    // Check
                    POSSQL.get_BuyOrderId_WhereStatusZero(OrderIdList, currency);
                    CheckOrderStatus.OrderStatus(currency, client, OrderIdList, LivePrice);

                    // // Sell                  
                    POSSQL.getDataRecords_WhereStatusOne(currency, getDataRecords);  
                    SellOrderProcess.setSellOrder(currency, percent, client, getDataRecords, LivePrice);
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
