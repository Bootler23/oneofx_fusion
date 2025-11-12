package com.binance.api.tradingbot.TradingMain;

import com.binance.api.tradingbot.Arbitrage.TriangularArbitrageExample;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.tradingbot.Arbitrage.TriangularArbitrageBot;
import java.math.BigDecimal;
import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.BalanceChecker;
import com.binance.api.tradingbot.HelperFunctions.Schedule;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.Indicator.BollingerBands;
import com.binance.api.tradingbot.Indicator.BollingerBands.BollingerBandsResult;
import com.binance.api.tradingbot.HelperFunctions.BollingerBandsPrint;
import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SQL_Database.WPDSQL;
import com.binance.api.tradingbot.SellAsset.SellAsset;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;
import com.binance.api.tradingbot.SellOrderProcess.Update;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.TradeInformation.getTrade;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.Settings.CurrencyConfig;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.domain.OrderStatus;
import com.binance.api.tradingbot.Database.dbUrl;

import java.util.List;

import org.ta4j.core.Indicator;

import java.util.ArrayList;

public class LTC_EUR_Live {
    
    private static volatile boolean running = true;
    
    /**
     * Stoppt den Trading Bot.
     */
    public static void stop() {
        running = false;
    }
    
    /**
     * Prüft ob der Bot läuft.
     */
    public static boolean isRunning() {
        return running;
    }
    
    public static void main(String[] args) {
        
        running = true;

        while (running) {
            try {

                String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();
                String currency = "";
                int state = 0;

                int count = 0;
                boolean FirstRound = true;

                List<Long> OrderIdList = new ArrayList<Long>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();
                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                // Jeden Tag 5€ DCA auf gebunde Assets -> 5€ über Sparplan aus Datenbank jeden
                // Tag 5e holen.

                while (running) {

                    state = set.Currency(BuyCurrencies, state);
                    currency = BuyCurrencies[state];

                    sleep.for_1_second();

                    Ticker.get_CurrencyPair_Price(currency, bnb.getClient(), LivePrice);

                    ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);
                    //ATHSQL.CheckForNewAllTimeHighOneOfX(currency, LivePrice);

                    BuyAmountFunktion.getBuyAmount(currency, bnb.getClient(), LivePrice, false);

                    ATHSQL.updateLPP(currency);


                    if (count == TradingConstants.UPDATE_CYCLE_COUNT || FirstRound) {

                        // BalanceChecker.showCurrencyBalance("LTC", bnb.getClient());

                        getTrade.RecordsByStatus(dbUrl.getHIST(), TradingConstants.TABLE_HIST, 0,
                                TradingConstants.HIST_COLUMNS_SELL_TRADES, getDataRecords);
                        Update.getSellTradeInformation(bnb.getClient(), getDataRecords);

                        getTrade.RecordsByStatus(dbUrl.getPOS(), TradingConstants.TABLE_POS, 5,
                                TradingConstants.POS_COLUMNS_BUY_TRADES, getDataRecords);
                        Update.getBuyTradeInformation(bnb.getClient(), getDataRecords);

                        SETSQL.CompareBalanceInSQLWithBinanceBalance(bnb.getClient());

                        HISTSQL.getDataRecords_WhereStatusOne(currency, getDataRecords);
                        Merge.splitValue(currency, getDataRecords);                       

                        // Wieviel ist mein Portfolio im Minus
                        WPDSQL.getGewinnAfterTax();

                        Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient());

                        count = 0;

                        FirstRound = false;

                    }                  
                    // BollingerBandsPrint.printBollingerBandsCompact(bnb.getClient(), currency, CandlestickInterval.FIVE_MINUTES);

                    // Schedule.Sell_Every_X_Seconds(currency, 300);

                    count++;

                    // Buy
                    if (SETSQL.getStatus("BUYING")) {
                        BuyOrderPocess.setBuyOrder(currency, bnb.getClient(), LivePrice);
                    }

                    // Check
                    POSSQL.get_BuyOrderId_WhereStatusZero(OrderIdList, currency);
                    CheckOrderStatus.OrderStatus(currency, bnb.getClient(), OrderIdList, LivePrice);

                    // Sell
                    if (SETSQL.getStatus("SELLING")) {
                        POSSQL.getDataRecords_WhereStatusOneOrSeven(currency, getDataRecords);
                        SellOrderProcess.setSellOrder(currency, bnb.getClient(), getDataRecords, LivePrice);
                    }
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
