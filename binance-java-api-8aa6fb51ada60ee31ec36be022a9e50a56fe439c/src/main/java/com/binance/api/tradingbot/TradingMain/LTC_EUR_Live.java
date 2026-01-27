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
import com.binance.api.tradingbot.HelperFunctions.round;
import com.binance.api.tradingbot.HelperFunctions.sleep;
import com.binance.api.tradingbot.Indicator.ATR;
import com.binance.api.tradingbot.Indicator.ATR.ATRResult;
import com.binance.api.tradingbot.Indicator.BollingerBands;
import com.binance.api.tradingbot.Indicator.EMA;
import com.binance.api.tradingbot.Indicator.EMA.PriceType;
import com.binance.api.tradingbot.Indicator.Stochastic;
import com.binance.api.tradingbot.Indicator.StochRSI;
import com.binance.api.tradingbot.Indicator.StochRSI.StochRSIResult;
import com.binance.api.tradingbot.Indicator.MACD;
import com.binance.api.tradingbot.Indicator.BollingerBands.BollingerBandsResult;
import com.binance.api.tradingbot.HelperFunctions.BollingerBandsPrint;
import com.binance.api.tradingbot.Indicator.Merge;
import com.binance.api.tradingbot.Indicator.RSI;
import com.binance.api.tradingbot.Indicator.RSI.RSIResult;
import com.binance.api.tradingbot.SQL_Database.ATHSQL;
import com.binance.api.tradingbot.SQL_Database.HISTSQL;
import com.binance.api.tradingbot.SQL_Database.POSSQL;
import com.binance.api.tradingbot.SQL_Database.SETSQL;
import com.binance.api.tradingbot.SQL_Database.WPDSQL;
import com.binance.api.tradingbot.SellAsset.SellAsset;
import com.binance.api.tradingbot.SellOrderProcess.SellOrderProcess;
import com.binance.api.tradingbot.SellOrderProcess.Update;
import com.binance.api.tradingbot.Settings.set;
import com.binance.api.tradingbot.Strategie.StrategieService;
import com.binance.api.tradingbot.TradeInformation.getTrade;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.Settings.CurrencyConfig;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.domain.OrderStatus;
import com.binance.api.tradingbot.Database.dbUrl;
import com.binance.api.tradingbot.service.RateLimitTracker;
import com.binance.api.tradingbot.Stream.UltraFastStream;

import java.util.List;

import org.ta4j.core.Indicator;

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

    /**
     * Prüft ob der Bot läuft.
     */
    public static boolean isRunning() {
        return running;
    }

    public static void main(String[] args) {

        running = true;

        // ========== Rate-Limit-Tracking starten ==========
        //
        // Der RateLimitTracker überwacht alle Binance API-Calls und gibt
        // alle 5 Sekunden eine Statusmeldung auf der Console aus.
        //
        // Diese zeigt:
        // - Aktuelles Weight (z.B. 1234/6000 = 20.6%)
        // - Verbleibende Kapazität
        // - Order-Count
        // - Top-3 meistgenutzte Endpoints
        // - Requests/Minute-Statistik
        //
        // Bei Auslastung >80% wird eine Warnung ausgegeben.
        //
        if (TradingConstants.RATE_LIMIT_TRACKING_ENABLED) {
            RateLimitTracker rateLimitTracker = RateLimitTracker.getInstance();
            rateLimitTracker.startTracking();
            System.out.println("✅ Rate-Limit-Tracking aktiviert - Console-Output alle "
                    + TradingConstants.RATE_LIMIT_REPORT_INTERVAL_SECONDS + " Sekunden");
        }

        // ========== WebSocket Stream starten ==========
        //
        // Der UltraFastStream liefert Echtzeit-Preise via WebSocket.
        // Vorteile:
        // - Kein Rate-Limit-Verbrauch für Preis-Abrufe
        // - Echtzeit-Updates (keine 1-Sekunden-Verzögerung)
        // - Automatischer Reconnect bei Verbindungsabbruch
        // - REST-Fallback wenn WebSocket dauerhaft fehlschlägt
        //
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
                double ema73low = 0.0;

                List<Long> OrderIdList = new ArrayList<Long>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();

                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                while (running) {

                    state = set.Currency(BuyCurrencies, state);
                    currency = BuyCurrencies[state];

                    if (RateLimitTracker.getInstance().isOverThreshold()) {
                        sleep.for_1_second(); // Langsamer bei hoher Last
                    } else {
                        sleep.for_02_second(); // Normal: 5 Durchläufe/Sekunde
                    }

                    Schedule.DCA_Fake_every_x_Seconds(currency, 600); // alle 10 Minuten DCA ausführen

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

                    ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);

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

                        // SETSQL.CompareBalanceInSQLWithBinanceBalance(bnb.getClient());

                        HISTSQL.getDataRecords_WhereStatusOne(currency, getDataRecords);
                        Merge.splitValue(currency, getDataRecords);

                        WPDSQL.getGewinnAfterTax();

                        // BNB Balance nur alle 5 Minuten prüfen
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastBnbBalanceCheck >= 5 * 60 * 1000) { // 5 Minuten in Millisekunden
                            Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient());
                            lastBnbBalanceCheck = currentTime;
                        }

                        count = 0;

                        // ema73low = round.two(EMA.getValue(bnb.getClient(), "LTCEUR",
                        // CandlestickInterval.FIFTEEN_MINUTES, 73, PriceType.LOW));
                        // System.out.println(ema73low + " | " + LivePrice.get(0));
                        FirstRound = false;
                    }

                    count++;

                    // Buy

                    if (SETSQL.getStatus("BUYING")) {
                        // if (LivePrice.get(0) > ema73low) {
                        BuyOrderPocess.setBuyOrder(currency, bnb.getClient(), LivePrice);
                        // }
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
