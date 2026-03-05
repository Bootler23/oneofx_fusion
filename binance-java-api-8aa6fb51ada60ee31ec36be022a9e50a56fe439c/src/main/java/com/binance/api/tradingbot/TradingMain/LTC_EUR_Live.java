package com.binance.api.tradingbot.TradingMain;

import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.BalanceChecker;
import com.binance.api.tradingbot.HelperFunctions.Time;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SQL_Database.WPDSQL;
import com.binance.api.tradingbot.SQL_Database.TradingRulesSQL;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;
import com.binance.api.tradingbot.SellOrderProcess.Update;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.TradeInformation.getTradeInformation;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.Settings.CurrencyConfig;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.service.TradingRulesService;
import com.binance.api.tradingbot.domain.TradingRules;
import com.binance.api.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.binance.api.tradingbot.service.RateLimitTracker;
import com.binance.api.tradingbot.service.PortfolioMonitor;
import com.binance.api.tradingbot.Stream.UltraFastStream;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.Indicator.StochRSI;
import com.binance.api.tradingbot.RiskRewardRatio.CurrencyRRR;
import com.binance.api.tradingbot.SQL_Database.CurrencySQL;
import com.binance.api.client.domain.market.CandlestickInterval;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class LTC_EUR_Live {

    private static volatile boolean running = true;
    private static Map<String, UltraFastStream> priceStreams = new HashMap<>();
    private static long lastBnbBalanceCheck = 0;

    // Zwischenspeicher für StochRSI 4h (wird in updateStochRSI() gemeinsam mit 2h in DB geschrieben)
    private static double stochCache_k4h = 0.0;
    private static double stochCache_d4h = 0.0;

    private static double PnL = 0.0;

    /**
     * Stoppt den Trading Bot.
     */
    public static void stop() {
        running = false;

        // Alle WebSocket Streams stoppen
        for (UltraFastStream stream : priceStreams.values()) {
            if (stream != null) {
                stream.stop();
            }
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

        // ========== Trading-Rules initialisieren ==========
        
        System.out.println("📋 Initialisiere Trading-Rules...");
        
        TradingRulesService tradingRulesService = TradingRulesService.getInstance();
        
        // Trading-Regeln von Binance abrufen und direkt in DB speichern
        String[] tradingCurrencies = CurrencyConfig.getBuyCurrencies();
        int rulesUpdated = 0;
        
        for (String symbol : tradingCurrencies) {
            TradingRules rules = tradingRulesService.getTradingRules(symbol);
            if (rules != null) {
                TradingRulesSQL.saveTradingRules(rules);
                rulesUpdated++;
            }
        }
        
        if (rulesUpdated > 0) {
            System.out.println("✅ Trading-Rules aktualisiert: " + rulesUpdated + " Symbole");
            TradingRulesFormatter.printFormattingInfo(tradingCurrencies[0]);
        } else {
            System.out.println("⚠️ Keine Trading-Rules aktualisiert - verwende Fallback-Werte");
        }

        // ========== WebSocket Streams starten ==========

        String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();
        
        System.out.println("📡 Starte WebSocket Streams für " + BuyCurrencies.length + " Währungen...");
        
        for (String currency : BuyCurrencies) {
            UltraFastStream stream = new UltraFastStream();
            stream.start(currency);
            priceStreams.put(currency, stream);
            System.out.println("✅ WebSocket Stream gestartet für " + currency);
        }

        // Warte auf erste Daten von allen Streams
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        int activeStreams = 0;
        for (Map.Entry<String, UltraFastStream> entry : priceStreams.entrySet()) {
            if (entry.getValue().hasData()) {
                activeStreams++;
            }
        }
        
        System.out.println("✅ " + activeStreams + "/" + BuyCurrencies.length + " WebSocket Streams aktiv");
        if (activeStreams < BuyCurrencies.length) {
            System.out.println("⚠️ Einige Streams ohne Daten - REST-Fallback wird genutzt");
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

                    OrderIdList.clear();
                    getDataRecords.clear();
                    LivePrice.clear();

                    state = set.Currency(BuyCurrencies, state);
                    String currency = BuyCurrencies[state];

                    int sleepMs = getSleepMs();
                    sleep.valueOffMillieSeconds(sleepMs);

                    //sleep.for_05_second();

                    // Hole Stream für die aktuelle Währung
                    UltraFastStream currentStream = priceStreams.get(currency);
                    Double livePrice = currentStream != null ? currentStream.getPrice() : null;
                    
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

                     ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);                  

                    if (count == TradingConstants.UPDATE_CYCLE_COUNT || FirstRound) {

                        getTradeInformation.RecordsByStatus(dbUrl.getoneOfX(),
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

                        count = 0;
                        FirstRound = false;
                    }

                    count++;
                    System.out.print(".");

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBnbBalanceCheck >= 60 * 1000) { // TODO Prozess verbessern
                        Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient()); // TODO -> evtl. anderer Ort

                        SETSQL.getAVG_BalanceToAsset_atBuy();
                        // BalanceChecker.showCurrencyBalance("LTC", bnb.getClient());

                        PnL = PortfolioMonitor.showPortfolioStatus(currency, LivePrice.get(0));
                        lastBnbBalanceCheck = currentTime;
                    }

                    if (CurrencySQL.isStale(currency)) {
                        StochRSI_4h(currency);
                        StochRSI_2h(currency);
                    }                    

                    // Buy
                    if (SETSQL.getStatus("currency", "buystatus", currency)) {
                        double[] stoch = CurrencySQL.getStochRSI(currency);
                        double k4h = stoch[0], d4h = stoch[1], k2h = stoch[2], d2h = stoch[3];
                        if (d4h < k4h) {
                            if (d2h < k2h) {
                                BuyOrderPocess.setBuyOrder(currency, bnb.getClient(), LivePrice);
                            }
                        }
                    }

                    // Check
                    POSSQL.get_BuyOrderId_WhereStatusZero(OrderIdList, currency);
                    CheckOrderStatus.OrderStatus(currency, bnb.getClient(), OrderIdList, LivePrice);
                   
                    // Sell
                    POSSQL.getDataRecords_WhereStatusOneOrSeven(currency, getDataRecords);
                    SellOrderProcess.setSellOrder(currency, bnb.getClient(), getDataRecords, LivePrice, PnL);                    
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

    private static void StochRSI_4h(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency, CandlestickInterval.FOUR_HOURLY);

        stochCache_k4h = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d4h = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));       
    }

    private static void StochRSI_2h(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency, CandlestickInterval.TWO_HOURLY);

        double k2h = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        double d2h = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));       
        CurrencySQL.saveStochRSI(currency, stochCache_k4h, stochCache_d4h, k2h, d2h);
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
            sleepMs = 300 + (currentWeight * 1000 / 1200);
            sleepMs = ((sleepMs + 50) / 100) * 100;
        }
        return sleepMs;
    }
}