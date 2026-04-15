package com.binance.api.tradingbot.TradingMain;

import com.binance.api.tradingbot.BuyOrderProcess.BuyAmountFunktion;
import com.binance.api.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.binance.api.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.binance.api.tradingbot.BuyOrderProcess.Ticker;
import com.binance.api.tradingbot.HelperFunctions.Asset;
import com.binance.api.tradingbot.HelperFunctions.BalanceReconciliation;
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
import com.binance.api.tradingbot.service.CurrencyWatcher;
import com.binance.api.tradingbot.Stream.CombinedTickerStream;
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.Indicator.StochRSI;
import com.binance.api.tradingbot.Indicator.CCI;
import com.binance.api.tradingbot.Indicator.ATR;
import com.binance.api.tradingbot.Indicator.RSI;
import com.binance.api.tradingbot.Indicator.EMA;
import com.binance.api.tradingbot.SQL_Database.CurrencySQL;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;

import java.util.List;
import java.util.ArrayList;

public class oneofx {

    private static volatile boolean running = true;
    private static CombinedTickerStream combinedStream;
    private static CurrencyWatcher currencyWatcher;
    private static long lastBnbBalanceCheck = 0;

    /**
     * Aktives Waehrungs-Array. Wird vom CurrencyWatcher zur Laufzeit erweitert
     * wenn neue Waehrungen in der DB auf buystatus=true gesetzt werden.
     * Volatile fuer sicheren Zugriff aus Main-Loop und CurrencyWatcher-Thread.
     */
    private static volatile String[] activeCurrencies = new String[0];

    // Zwischenspeicher fuer StochRSI aller Timeframes + CCI 4h + ATR 4h + RSI 4h.
    // StochRSI_2h() schreibt alle Werte gemeinsam in die DB.
    private static double stochCache_k4h = 0.0;
    private static double stochCache_d4h = 0.0;
    private static double stochCache_k12h = 0.0;
    private static double stochCache_d12h = 0.0;
    private static double stochCache_k1d = 0.0;
    private static double stochCache_d1d = 0.0;
    private static double stochCache_k3d = 0.0;
    private static double stochCache_d3d = 0.0;
    private static double stochCache_k1w = 0.0;
    private static double stochCache_d1w = 0.0;
    private static double stochCache_k1m = 0.0;
    private static double stochCache_d1m = 0.0;
    private static double stochCache_k5m = 0.0;
    private static double stochCache_d5m = 0.0;
    private static double stochCache_cci4h = 0.0;
    private static double stochCache_atr4h = 0.0;
    private static double stochCache_rsi4h = 0.0;
    private static double stochCache_emaFast = 0.0;
    private static double stochCache_emaSlow = 0.0;

    private static double PnL = 0.0;

    /**
     * Stoppt den Trading Bot.
     */
    public static void stop() {
        running = false;

        // CurrencyWatcher stoppen
        if (currencyWatcher != null) {
            currencyWatcher.stop();
        }

        // Combined WebSocket Stream stoppen
        if (combinedStream != null) {
            combinedStream.stop();
        }

        // Rate-Limit-Tracker stoppen
        if (TradingConstants.RATE_LIMIT_TRACKING_ENABLED) {
            RateLimitTracker.getInstance().stopTracking();
        }
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Gibt das aktuell aktive Waehrungs-Array zurueck.
     * Thread-safe dank volatile Referenz.
     *
     * @return Kopie-sichere Referenz auf das aktive Array (nie null nach Init)
     */
    public static String[] getActiveCurrencies() {
        return activeCurrencies;
    }

    /**
     * Setzt ein neues aktives Waehrungs-Array (atomarer Referenz-Swap).
     * Wird vom CurrencyWatcher aufgerufen wenn neue Waehrungen erkannt werden.
     *
     * @param currencies Neues Waehrungs-Array (darf nicht null sein)
     */
    public static void setActiveCurrencies(String[] currencies) {
        if (currencies == null) {
            throw new IllegalArgumentException("Currencies-Array darf nicht null sein");
        }
        activeCurrencies = currencies;
    }

    public static void main(String[] args) {

        running = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown-Signal empfangen – stoppe Bot...");
            stop();
        }, "ShutdownHook-Thread"));

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

        // ========== Combined WebSocket Stream starten (1 Connection für alle Symbole)
        // ==========

        String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();

        System.out.println(
                "📡 Starte Combined WebSocket Stream für " + BuyCurrencies.length + " Währungen (1 Connection)...");

        combinedStream = new CombinedTickerStream();
        combinedStream.start(BuyCurrencies);

        // Warte auf erste Daten (max. 15 Sekunden)
        System.out.println("⏳ Warte auf WebSocket-Daten...");
        long timeout = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < timeout) {
            if (combinedStream.getActiveSymbolCount() == BuyCurrencies.length)
                break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int activeStreams = combinedStream.getActiveSymbolCount();
        System.out.println("✅ " + activeStreams + "/" + BuyCurrencies.length + " Symbole empfangen Daten");
        if (activeStreams < BuyCurrencies.length) {
            System.out.println("⚠️ Einige Symbole ohne Daten - REST-Fallback wird genutzt");
        }
        // ========== Aktives Waehrungs-Array initialisieren und CurrencyWatcher starten
        // ==========

        activeCurrencies = BuyCurrencies;

        currencyWatcher = new CurrencyWatcher(combinedStream);
        currencyWatcher.start();
        System.out.println("\uD83D\uDD0D CurrencyWatcher gestartet \u2014 pruefe alle "
                + TradingConstants.CURRENCY_WATCH_INTERVAL_SECONDS + "s auf neue Waehrungen");
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

                    // Lokale Kopie des volatilen Arrays — sicher bei Aenderungen durch
                    // CurrencyWatcher
                    String[] currentCurrencies = activeCurrencies;

                    // Falls noch keine Waehrungen aktiv (Edge-Case: alle deaktiviert)
                    if (currentCurrencies.length == 0) {
                        sleep.for_1_second();
                        continue;
                    }

                    state = set.Currency(currentCurrencies, state);
                    if (state < 0) {
                        sleep.for_1_second();
                        continue;
                    }
                    String currency = currentCurrencies[state];

                    int sleepMs = getSleepMs();
                    sleep.valueOffMillieSeconds(sleepMs);

                    sleep.for_1_second();

                    // Hole Preis aus dem Combined Stream
                    Double livePrice = combinedStream.getPrice(currency);

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

                        getTradeInformation.RecordsByStatus(dbUrl.getoneOfX(),
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
                    if (currentTime - lastBnbBalanceCheck >= 60 * 1000) {
                        Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient());

                        SETSQL.getAVG_BalanceToAsset_atBuy();

                        // Balance-Abgleich: Exchange vs. Datenbank pro Waehrungspaar
                        BalanceReconciliation.reconcileAll(currentCurrencies, bnb.getClient());

                        // Portfolio aller aktiven Waehrungspaare in einem Block anzeigen
                        PortfolioMonitor.showAllPortfolioStatus(currentCurrencies, combinedStream);

                        lastBnbBalanceCheck = currentTime;
                    }

                    if (CurrencySQL.isStale(currency)) {
                        StochRSI_4h(currency);
                        // StochRSI_12h(currency);
                        // StochRSI_1d(currency);
                        // StochRSI_3d(currency);
                        // StochRSI_1w(currency);
                        // StochRSI_1m(currency);
                        StochRSI_5m(currency);
                        // CCI_4h(currency);
                        // ATR_4h(currency);
                        // RSI_4h(currency);
                        // EMA(currency);
                        // StochRSI_2h(currency); // schreibt alle Werte in die DB
                    }

                    // Buy
                    double[] stoch = CurrencySQL.getStochRSI(currency);
                    double k2h = stoch[2], d2h = stoch[3];                
                    if (k2h > d2h) {
                        BuyOrderPocess.setBuyOrder(currency, bnb.getClient(), LivePrice);
                    }

                    // Check
                    POSSQL.get_BuyOrderId_WhereStatusZero(OrderIdList, currency);
                    CheckOrderStatus.OrderStatus(currency, bnb.getClient(), OrderIdList, LivePrice);

                    // Sell
                    POSSQL.getDataRecords_WhereStatusOneOrSeven(currency, getDataRecords);
                    SellOrderProcess.setSellOrder(currency, bnb.getClient(), getDataRecords, LivePrice, PnL);
                }

            } catch (BinanceApiException e) {
                System.err.println("⚠️ Binance API Fehler: " + e.getMessage());
                System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                System.out.println("↻ Warte 60s und versuche erneut...");
                sleep.for_60_seconds();
                continue;
            } catch (IndexOutOfBoundsException e) {
                String FehlerMessage = "Fehler: Index out of bounds! Der Fehler liegt in einem leerem Array irgendwo in dem Code!";
                System.err.println(FehlerMessage);
                System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                sleep.for_60_seconds();
                continue;
            } catch (Exception e) {
                System.err.println("⚠️ Unerwarteter Fehler: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                System.out.println("↻ Warte 60s und versuche erneut...");
                sleep.for_60_seconds();
                continue;
            }
        }       
    }

    private static void StochRSI_4h(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.FOUR_HOURLY);

        stochCache_k4h = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d4h = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
    }

    private static void StochRSI_2h(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.TWO_HOURLY);

        double k2h = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        double d2h = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));

        // 24h-Volumen holen (1 API-Call, Weight=1)
        double volume24h = 0.0;
        try {
            com.binance.api.client.domain.market.TickerStatistics ticker = bnb.getClient()
                    .get24HrPriceStatistics(currency);
            double baseVolume = Double.parseDouble(ticker.getVolume());
            double lastPrice = Double.parseDouble(ticker.getLastPrice());
            volume24h = round.two(baseVolume * lastPrice); // Quote-Volume in EUR/USDC
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen des 24h-Volumens fuer " + currency + ": " + e.getMessage());
        }

        // Alle Timeframe-Werte gemeinsam in die DB schreiben
        CurrencySQL.saveStochRSI(currency,
                stochCache_k4h, stochCache_d4h,
                k2h, d2h,
                stochCache_k12h, stochCache_d12h,
                stochCache_k1d, stochCache_d1d,
                stochCache_k3d, stochCache_d3d,
                stochCache_k1w, stochCache_d1w,
                stochCache_k1m, stochCache_d1m,
                stochCache_k5m, stochCache_d5m,
                stochCache_cci4h,
                stochCache_atr4h,
                stochCache_rsi4h,
                volume24h,
                stochCache_emaFast,
                stochCache_emaSlow);
    }

    /**
     * Berechnet EMA 20 (fast) und EMA 50 (slow) fuer den 4h-Timeframe.
     */
    private static void EMA(String currency) {
        try {
            stochCache_emaFast = round.two(
                    EMA.getValue(bnb.getClient(), currency, CandlestickInterval.FIVE_MINUTES, 20));
            stochCache_emaSlow = round.two(
                    EMA.getValue(bnb.getClient(), currency, CandlestickInterval.FIVE_MINUTES, 50));
        } catch (Exception e) {
            System.err.println("Fehler beim Berechnen der EMA 20/50 fuer " + currency + ": " + e.getMessage());
        }
    }

    private static void StochRSI_1m(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.MONTHLY);
        stochCache_k1m = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d1m = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
    }

    private static void StochRSI_5m(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.FIVE_MINUTES);
        stochCache_k5m = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d5m = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
    }

    private static void RSI_4h(String currency) {
        try {
            stochCache_rsi4h = round.two(
                    RSI.getRSI(bnb.getClient(), currency, CandlestickInterval.FOUR_HOURLY, 14));
        } catch (Exception e) {
            System.err.println("Fehler beim Berechnen des RSI 4h fuer " + currency + ": " + e.getMessage());
        }
    }

    private static void ATR_4h(String currency) {
        try {
            ATR.ATRResult result = ATR.getATR(bnb.getClient(), currency,
                    CandlestickInterval.FIVE_MINUTES);
            stochCache_atr4h = round.two(result.getATR());
        } catch (Exception e) {
            System.err.println("Fehler beim Berechnen des ATR 5m fuer " + currency + ": " + e.getMessage());
        }
    }

    /**
     * Berechnet den CCI (Commodity Channel Index) fuer den 4h-Timeframe.
     * Periode: 20 (Standard)
     * Wertebereich: typisch -200 bis +200; Overbought > 100, Oversold < -100
     */
    private static void CCI_4h(String currency) {
        try {
            stochCache_cci4h = CCI.getCCI(bnb.getClient(), currency,
                    CandlestickInterval.FOUR_HOURLY, 20);
        } catch (Exception e) {
            System.err.println("Fehler beim Berechnen des CCI 4h fuer " + currency + ": " + e.getMessage());
        }
    }

    private static void StochRSI_12h(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.TWELVE_HOURLY);
        stochCache_k12h = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d12h = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
    }

    private static void StochRSI_1d(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.DAILY);
        stochCache_k1d = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d1d = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
    }

    private static void StochRSI_3d(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.THREE_DAILY);
        stochCache_k3d = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d3d = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
    }

    private static void StochRSI_1w(String currency) {
        StochRSI.StochRSIResult result = StochRSI.getStochRSI(bnb.getClient(), currency,
                CandlestickInterval.WEEKLY);
        stochCache_k1w = Math.max(0.0, Math.min(100.0, round.two(result.getK() * 100)));
        stochCache_d1w = Math.max(0.0, Math.min(100.0, round.two(result.getD() * 100)));
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
            sleepMs = 5000;
            System.out.println("⚠️ Kein Gewicht verfügbar, setze Sleep auf 5000 ms");
        } else if (currentWeight >= 1000) {
            sleepMs = 2000;
            System.out.println("⚠️ Gewicht über 1000, setze Sleep auf 2000 ms");
        } else {
            sleepMs = 300 + (currentWeight * 1000 / 1200);
            sleepMs = ((sleepMs + 50) / 100) * 100;
        }
        return sleepMs;
    }
}