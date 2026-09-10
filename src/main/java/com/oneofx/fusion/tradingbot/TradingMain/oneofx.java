package com.oneofx.fusion.tradingbot.TradingMain;

import com.oneofx.fusion.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.Indicator.Merge;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.StrategyStateDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SETSQL;
import com.oneofx.fusion.tradingbot.SQL_Database.TradingRulesSQL;
import com.oneofx.fusion.tradingbot.SellOrderProcess.SellOrderProcess;
import com.oneofx.fusion.tradingbot.SellOrderProcess.Update;
import com.oneofx.fusion.tradingbot.Settings.set;
import com.oneofx.fusion.tradingbot.TradeInformation.getTradeInformation;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import com.oneofx.fusion.tradingbot.Settings.CurrencyConfig;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.Database.dbUrl;
import com.oneofx.fusion.tradingbot.Database.DatabaseSchema;
import com.oneofx.fusion.tradingbot.Database.PortablePaths;
import com.oneofx.fusion.tradingbot.service.TradingRulesService;
import com.oneofx.fusion.tradingbot.service.MarketRegimeService;
import com.oneofx.fusion.tradingbot.desktop.BaseConfigRepository;
import com.oneofx.fusion.tradingbot.desktop.BotBaseConfig;
import com.oneofx.fusion.tradingbot.service.MarketRegimeService.Regime;
import com.oneofx.fusion.tradingbot.domain.TradingRules;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.Stream.PricePoller;
import com.oneofx.fusion.client.FusionApiException;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public class oneofx {

    private static volatile boolean running = true;

    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();
    private static final StrategyStateDAO strategyStateDAO = new StrategyStateDAO();
    private static final BaseConfigRepository baseConfigRepository = new BaseConfigRepository();
    private static PricePoller pricePoller;
    private static FileChannel lockChannel;
    private static FileLock instanceLock;

    /**
     * Aktives Waehrungs-Array — wird im Update-Zyklus direkt aus der DB geladen.
     * Volatile fuer sicheren Zugriff aus dem Main-Loop.
     */
    private static volatile String[] activeCurrencies = new String[0];
    private static volatile Set<String> buyEnabledCurrencies = Set.of();

    /**
     * Stoppt den Trading Bot.
     */
    public static void stop() {
        running = false;

        // PricePoller stoppen
        if (pricePoller != null) {
            pricePoller.stop();
        }

    }

    public static boolean isRunning() {
        return running;
    }

    public static void main(String[] args) {

        running = true;
        PortablePaths.initialize();

        if (!acquireInstanceLock()) {
            System.err.println("⛔ Eine andere Instanz läuft bereits oder die Sperrdatei ist nicht verfügbar.");
            running = false;
            return;
        }

        try {
            // ========== Datenbankschema und Trading-Rules initialisieren ==========

        DatabaseSchema.initialize();

        System.out.println("📋 Initialisiere Trading-Rules...");

        TradingRulesService tradingRulesService = TradingRulesService.getInstance();

        // Trading-Regeln von Bitpanda Fusion abrufen und direkt in DB speichern
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

        String[] BuyCurrencies = CurrencyConfig.getMonitoredCurrencies();
        buyEnabledCurrencies = Set.copyOf(Arrays.asList(tradingCurrencies));

        System.out.println("📡 Starte REST-Preis-Polling für " + BuyCurrencies.length + " Währungen...");

        if (BuyCurrencies.length == 0) {
            System.err.println("⛔ Keine aktiven Währungen konfiguriert. Trading-Loop wird nicht gestartet.");
            return;
        }

        pricePoller = new PricePoller();
        pricePoller.start(BuyCurrencies);

        int activeStreams = pricePoller.getActiveSymbolCount();
        if (!pricePoller.hasDataForAll(BuyCurrencies)) {
            System.err.println("⛔ Nur " + activeStreams + "/" + BuyCurrencies.length
                    + " Preise verfügbar. Trading-Loop wird nicht gestartet.");
            pricePoller.stop();
            return;
        }
        System.out.println("✅ " + activeStreams + "/" + BuyCurrencies.length + " Symbole verfügbar");
        // Auch Paare mit Altpositionen bleiben aktiv, wenn deren buyStatus aus ist.
        activeCurrencies = BuyCurrencies;

        while (running) {
            try {

                int state = 0;
                int count = 0;
                boolean FirstRound = true;

                List<String> OrderIdList = new ArrayList<>();
                List<String> getDataRecords = new ArrayList<String>();
                List<Double> LivePrice = new ArrayList<Double>();

                // -----------------------------------------------------------------------------------------------------------------------------------------------------

                while (running) {

                    OrderIdList.clear();
                    getDataRecords.clear();
                    LivePrice.clear();

                    // Lokale Kopie des aktiven Waehrungs-Arrays fuer diesen Durchlauf
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

                    sleep.valueOffMillieSeconds(TradingConstants.TRADING_LOOP_DELAY_MS);

                    // sleep.for_05_second();

                    // Hole Preis via REST-Polling
                    Double livePrice = pricePoller.getPrice(currency);

                    if (livePrice == null || livePrice == 0.0) {
                        System.out.println("x");
                        sleep.for_1_second();
                        Ticker.get_CurrencyPair_Price(currency, FusionClientProvider.getClient(), LivePrice);

                        if (LivePrice.isEmpty() || LivePrice.get(0) == 0.0) {
                            System.err.println("p");
                            sleep.for_1_second();
                            continue;
                        }
                    } else {
                        LivePrice.clear();
                        LivePrice.add(livePrice);
                    }

                    currencyDAO.checkForNewAllTimeHigh(currency, LivePrice);

                    if (count == TradingConstants.UPDATE_CYCLE_COUNT || FirstRound) {

                        // Kauf-Freigabe und operative Ueberwachung sind getrennt:
                        // Altpositionen muessen auch bei buyStatus=false verkauft werden koennen.
                        String[] dbBuyCurrencies = CurrencyConfig.getBuyCurrencies();
                        String[] dbCurrencies = CurrencyConfig.getMonitoredCurrencies();
                        buyEnabledCurrencies = Set.copyOf(Arrays.asList(dbBuyCurrencies));
                        pricePoller.addSymbols(dbCurrencies);
                        activeCurrencies = dbCurrencies;

                        getTradeInformation.RecordsByStatus(dbUrl.getoneOfX(),
                                TradingConstants.TABLE_HIST, 0,
                                TradingConstants.HIST_COLUMNS_SELL_TRADES, getDataRecords);
                        Update.getSellTradeInformation(FusionClientProvider.getClient(), getDataRecords);

                        getTradeInformation.RecordsByStatus(dbUrl.getoneOfX(),
                                TradingConstants.TABLE_POS, 5,
                                TradingConstants.POS_COLUMNS_BUY_TRADES, getDataRecords);
                        Update.getBuyTradeInformation(FusionClientProvider.getClient(), getDataRecords);

                        SETSQL.compareBalanceWithFusion(FusionClientProvider.getClient());

                        getDataRecords.clear();
                        getDataRecords.addAll(histDAO.getDataRecordsWhereStatusOne(currency));
                        Merge.splitValue(currency, getDataRecords);

                        count = 0;
                        FirstRound = false;
                    }

                    count++;
                    System.out.print(".");

                    // Erst bestehende Orders abgleichen. Dadurch werden Ausführungen und
                    // Stornierungen verbucht, bevor fehlende Kauforders ergänzt werden.
                    OrderIdList.clear();
                    OrderIdList.addAll(positionDAO.getBuyOrderIdsWhereStatusZero(currency));
                    CheckOrderStatus.OrderStatus(currency, FusionClientProvider.getClient(), OrderIdList, LivePrice);

                    MarketRegimeService.Snapshot market = MarketRegimeService.getInstance()
                            .getSnapshot(currency, FusionClientProvider.getClient());

                    // Bestehende Positionen werden vor der Kaufentscheidung geprüft.
                    // Ein Notstopp kann so noch im selben Durchlauf die Kaufsperre setzen.
                    getDataRecords.clear();
                    getDataRecords.addAll(positionDAO.getDataRecordsWhereStatusOneOrSeven(currency));
                    if (market.regime() == Regime.EXIT) {
                        SellOrderProcess.closePositionsForRegime(
                                currency, FusionClientProvider.getClient(), getDataRecords,
                                LivePrice.get(0));
                    } else {
                        SellOrderProcess.setSellOrder(
                                currency, FusionClientProvider.getClient(), getDataRecords, LivePrice);
                    }

                    boolean cooldownActive = strategyStateDAO.isBuyBlocked(currency);
                    boolean baseBuyAllowed = buyEnabledCurrencies.contains(currency)
                            && market.regime() == Regime.BUY_ALLOWED && !cooldownActive;
                    boolean trailingBuyReady = true;
                    BotBaseConfig executionConfig = baseConfigRepository.loadEffective(currency);
                    if (baseBuyAllowed && executionConfig.trailingStopBuyEnabled()) {
                        trailingBuyReady = baseConfigRepository.evaluateTrailingBuy(
                                com.oneofx.fusion.tradingbot.bot.BotRuntime.activeBotId(), currency,
                                LivePrice.get(0), executionConfig.trailingStopBuyActivationPercent(),
                                executionConfig.trailingStopBuyReboundPercent());
                    }
                    if (baseBuyAllowed && trailingBuyReady) {
                        // Kauf: bis zu zwei offene Limit-Orders unter dem Markt ergänzen.
                        BuyOrderPocess.setBuyOrder(currency, FusionClientProvider.getClient(), LivePrice);
                        if (executionConfig.trailingStopBuyEnabled()) {
                            baseConfigRepository.resetTrailingBuy(
                                    com.oneofx.fusion.tradingbot.bot.BotRuntime.activeBotId(),
                                    currency, LivePrice.get(0));
                        }
                    } else {
                        OrderIdList.clear();
                        OrderIdList.addAll(positionDAO.getBuyOrderIdsWhereStatusZero(currency));
                        CheckOrderStatus.cancelOpenBuyOrdersForRegime(
                                currency, FusionClientProvider.getClient(), OrderIdList, LivePrice);
                    }
                }

            } catch (FusionApiException e) {
                System.err.println("⚠️ Bitpanda Fusion API Fehler: " + e.getMessage());
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
                e.printStackTrace(System.err);
                System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                System.out.println("↻ Warte 60s und versuche erneut...");
                sleep.for_60_seconds();
                continue;
            }
        }
        } finally {
            if (pricePoller != null) {
                pricePoller.stop();
                pricePoller = null;
            }
            releaseInstanceLock();
        }
    }

    private static synchronized boolean acquireInstanceLock() {
        try {
            lockChannel = new RandomAccessFile(
                    PortablePaths.getBaseDirectory().resolve("oneofx.lock").toFile(), "rw")
                    .getChannel();
            instanceLock = lockChannel.tryLock();
            if (instanceLock == null) {
                lockChannel.close();
                lockChannel = null;
                return false;
            }
            return true;
        } catch (Exception ex) {
            System.err.println("Sperrdatei konnte nicht erstellt werden: " + ex.getMessage());
            releaseInstanceLock();
            return false;
        }
    }

    private static synchronized void releaseInstanceLock() {
        try {
            if (instanceLock != null && instanceLock.isValid()) instanceLock.release();
        } catch (Exception ignored) {
        } finally {
            instanceLock = null;
        }
        try {
            if (lockChannel != null) lockChannel.close();
        } catch (Exception ignored) {
        } finally {
            lockChannel = null;
        }
    }

}
