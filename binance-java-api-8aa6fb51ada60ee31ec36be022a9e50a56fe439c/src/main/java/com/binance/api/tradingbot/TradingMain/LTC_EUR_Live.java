package com.binance.api.tradingbot.TradingMain;

import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.BalanceChecker;
import com.binance.api.tradingbot.HelperFunctions.CompoundInterestCalculator;
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
import com.binance.api.tradingbot.TradeInformation.getTradeInformation;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.Settings.CurrencyConfig;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.service.RateLimitTracker;
import com.binance.api.tradingbot.Stream.UltraFastStream;
import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;

import java.util.List;

import java.util.ArrayList;

public class LTC_EUR_Live {

    private static volatile boolean running = true;
    private static UltraFastStream priceStream;
    private static long lastBnbBalanceCheck = 0;

    /**
     * Stoppt den Trading Bot.
     */
    public static void stop() {
        running = false;

        // WebSocket Stream stoppen
        if (priceStream != null) {
            priceStream.stop();
        }

        // Rate-Limit-Tracker stoppen
        if (TradingConstants.RATE_LIMIT_TRACKING_ENABLED) {
            RateLimitTracker.getInstance().stopTracking();
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static void main(String[] args) {

        running = true;

        if (TradingConstants.RATE_LIMIT_TRACKING_ENABLED) {
            RateLimitTracker rateLimitTracker = RateLimitTracker.getInstance();
            rateLimitTracker.startTracking();
            System.out.println("✅ Rate-Limit-Tracking aktiviert - Console-Output alle "
                    + TradingConstants.RATE_LIMIT_REPORT_INTERVAL_SECONDS + " Sekunden");
        }

        // ========== WebSocket Stream starten ==========

        String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();
        String currency = BuyCurrencies[0]; // Erste (und einzige) Währung

        priceStream = new UltraFastStream();
        priceStream.start(currency);

        // Warte kurz auf erste Daten (max 5 Sekunden)
        int waitCount = 0;
        while (!priceStream.hasData() && waitCount < 50 && running) {
            sleep.valueOffMillieSeconds(100);
            waitCount++;
        }

        if (priceStream.hasData()) {
            System.out.println("✅ WebSocket Stream aktiv für " + currency);
        } else {
            System.out.println("⚠️ Noch keine Stream-Daten - REST-Fallback wird automatisch genutzt");
        }

        while (running) {
            try {

                int state = 0;

                int count = 0;
                boolean FirstRound = true;

                List<Long> OrderIdList = new ArrayList<Long>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();

                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                while (running) {

                    state = set.Currency(BuyCurrencies, state);
                    currency = BuyCurrencies[state];

                    int sleepMs = getSleepMs();
                    sleep.valueOffMillieSeconds(sleepMs);

                    Double livePrice = priceStream.getPrice();
                    if (livePrice == null || livePrice == 0.0) {

                        System.out.println("x");
                        sleep.for_1_second();
                        Ticker.get_CurrencyPair_Price(currency, bnb.getClient(), LivePrice);

                        if (LivePrice.isEmpty() || LivePrice.get(0) == 0.0) {
                            System.err.println("p");
                            sleep.for_1_second();
                            continue;
                        }
                    } else {
                        LivePrice.clear();
                        LivePrice.add(livePrice);
                    }

                    // ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);

                    // BuyAmountFunktion.getBuyAmount(currency, bnb.getClient(), LivePrice, false);

                    // ATHSQL.updateLPP(currency);

                    if (count == TradingConstants.UPDATE_CYCLE_COUNT || FirstRound) {

                        getTradeInformation.RecordsByStatus(dbUrl.getHIST(),
                                TradingConstants.TABLE_HIST, 0,
                                TradingConstants.HIST_COLUMNS_SELL_TRADES, getDataRecords);
                        Update.getSellTradeInformation(bnb.getClient(), getDataRecords);

                        getTradeInformation.RecordsByStatus(dbUrl.getPOS(),
                                TradingConstants.TABLE_POS, 5,
                                TradingConstants.POS_COLUMNS_BUY_TRADES, getDataRecords);
                        Update.getBuyTradeInformation(bnb.getClient(), getDataRecords);

                        SETSQL.CompareBalanceInSQLWithBinanceBalance(bnb.getClient());

                        HISTSQL.getDataRecords_WhereStatusOne(currency, getDataRecords);
                        Merge.splitValue(currency, getDataRecords);

                        WPDSQL.getGewinnAfterTax();

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastBnbBalanceCheck >= 60 * 1000) { // TODO Prozess verbessern
                            Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient()); // TODO -> evtl. anderer Ort

                            SETSQL.getAVG_BalanceToAsset_atBuy();
                            BalanceChecker.showCurrencyBalance("LTC", bnb.getClient());
                            lastBnbBalanceCheck = currentTime;

                            // com.binance.api.tradingbot.Indicator.Update.calcPercentToAddForNextBuy();

                        }

                        count = 0;
                        FirstRound = false;
                    }
                    count++;  
                    System.out.print(".");                

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

    private static int getSleepMs() {
        RateLimitTracker tracker = RateLimitTracker.getInstance();
       
        int currentWeight = tracker.getServerReportedWeight();
        if (currentWeight <= 0) {
            currentWeight = tracker.getUsedWeightInWindow();
            System.out.println("⚠️ Fallback auf genutztes Gewicht im Fenster für Sleep-Berechnung");
        }
      
        int sleepMs;       

        if (currentWeight <= 0) {
            sleepMs = 2000;
            System.out.println("⚠️ Kein Gewicht verfügbar, setze Sleep auf 2000 ms");
        } else if (currentWeight >= 1200) {
            sleepMs = 1200;
            System.out.println("⚠️ Gewicht über 1200, setze Sleep auf 1200 ms");
        } else {
            sleepMs = 200 + (currentWeight * 1000 / 1200);
            sleepMs = ((sleepMs + 50) / 100) * 100;
        }
        return sleepMs;
    }
}
