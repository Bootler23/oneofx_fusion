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

        while (running) {
            try {

                String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();
                String currency = "";
                int state = 0;

                int count = 0;
                boolean FirstRound = true;
                double ema73low = 0.0;

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
                    // sleep.valueOffMillieSeconds(5);

                    Ticker.get_CurrencyPair_Price(currency, bnb.getClient(), LivePrice);

                    ATHSQL.CheckForNewAllTimeHigh(currency, LivePrice);
                    // ATHSQL.CheckForNewAllTimeHighOneOfX(currency, LivePrice);

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

                        WPDSQL.getGewinnAfterTax();
                        Asset.getBNB_Balance("BNBEUR", "BNB", bnb.getClient());
                        count = 0;

                        // ------- Indikator Strategie -------

                        // double rsi = RSI.getRSIWithSmoothing(bnb.getClient(), "LTCEUR",
                        // CandlestickInterval.FIVE_MINUTES, 14, 14).getRSI();

                        // StrategieService.executeRsiStrategy(rsi);

                        // double ema100 = round.two(EMA.getValue(bnb.getClient(), "LTCEUR",
                        // CandlestickInterval.FIVE_MINUTES, 100));

                        // if (SETSQL.getEMA_value() > ema100) {
                        // SETSQL.setEMA(true);
                        // } else {
                        // SETSQL.setEMA_value(ema100);
                        // SETSQL.setEMA(false);
                        // }

                        // ATRResult atrResult = ATR.getATR(bnb.getClient(), "LTCEUR",
                        // CandlestickInterval.FIVE_MINUTES,
                        // 14);
                        // double atr = atrResult.getATR();

                        // // Eigene Parameter (rsiPeriod, stochPeriod, kPeriod, dPeriod):
                        // StochRSIResult stochRsiResult = StochRSI.getStochRSI(bnb.getClient(),
                        // "LTCEUR",
                        // CandlestickInterval.FIVE_MINUTES, 14, 14, 2, 2);
                        // double stochK = round.two(stochRsiResult.getK() * 100);
                        // double stochD = round.two(stochRsiResult.getD() * 100);

                        // System.out.println(
                        // "RSI: " + rsi + " | EMA100: " + ema100 + " | StochRSI K: " + stochK + " D: "
                        // + stochD
                        // + " | ATR: " + atr);

                        // if ((SETSQL.getRSI() == true) && (LivePrice.get(0) > ema100) && (stochK >
                        // stochD)
                        // && (stochK < 23)) {
                        // System.out.println();
                        // // SETSQL.setRSI();

                        // SETSQL.setStopLoss(3 * atr);
                        // }

                        // ------------------------------------
                        ema73low = round.two(EMA.getValue(bnb.getClient(), "LTCEUR", CandlestickInterval.FIFTEEN_MINUTES, 73, PriceType.LOW));

                        System.out.println(ema73low + " | " + LivePrice.get(0));

                        FirstRound = false;
                    }
                  
                    count++;

                    // Buy

                    if (SETSQL.getStatus("BUYING")) {
                        if (LivePrice.get(0) > ema73low) {
                            BuyOrderPocess.setBuyOrder(currency, bnb.getClient(), LivePrice);
                        }
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
