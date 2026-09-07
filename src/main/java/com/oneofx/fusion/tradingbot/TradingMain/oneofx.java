package com.oneofx.fusion.tradingbot.TradingMain;

import com.oneofx.fusion.tradingbot.BuyOrderProcess.BuyOrderPocess;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.CheckOrderStatus;
import com.oneofx.fusion.tradingbot.BuyOrderProcess.Ticker;
import com.oneofx.fusion.tradingbot.HelperFunctions.Asset;
import com.oneofx.fusion.tradingbot.HelperFunctions.BalanceReconciliation;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.sleep;
import com.oneofx.fusion.tradingbot.Indicator.Merge;
import com.oneofx.fusion.tradingbot.Indicator.StochRSI;
import com.oneofx.fusion.client.model.CandlestickInterval;
import com.oneofx.fusion.tradingbot.SQL_Database.CurrencyDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
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
import com.oneofx.fusion.tradingbot.service.TradingRulesService;
import com.oneofx.fusion.tradingbot.domain.TradingRules;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.Stream.PricePoller;
import com.oneofx.fusion.client.FusionApiException;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.ArrayList;

public class oneofx {

    private static volatile boolean running = true;

    private static final CurrencyDAO currencyDAO = new CurrencyDAO();
    private static final HistDAO histDAO = new HistDAO();
    private static final PositionDAO positionDAO = new PositionDAO();
    private static PricePoller pricePoller;

    /**
     * Aktives Waehrungs-Array — wird im Update-Zyklus direkt aus der DB geladen.
     * Volatile fuer sicheren Zugriff aus dem Main-Loop.
     */
    private static volatile String[] activeCurrencies = new String[0];

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

        // ========== Schutz gegen Doppelstart ==========
        try {
            FileChannel lockChannel = new RandomAccessFile("oneofx.lock", "rw").getChannel();
            FileLock lock = lockChannel.tryLock();
            if (lock == null) {
                System.err.println("⛔ Eine andere Instanz läuft bereits. Abbruch.");
                System.exit(1);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { lock.release(); lockChannel.close(); } catch (Exception ignored) {}
            }));
        } catch (Exception e) {
            // REVIEW [HOCH]: Bei einem Lock-Fehler laeuft der Bot trotzdem weiter.
            // Dann koennen zwei Instanzen gleichzeitig dieselben Positionen lesen
            // und doppelte Orders ausloesen. Ein sicherer Start sollte hier abbrechen.
            System.err.println("⚠️ Lock-Datei konnte nicht erstellt werden: " + e.getMessage());
        }

        // ========== Trading-Rules initialisieren ==========

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

        String[] BuyCurrencies = CurrencyConfig.getBuyCurrencies();

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
        // Aktives Waehrungs-Array initialisieren. Es wird im Update-Zyklus aus der DB neu geladen.
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

                        // Waehrungen aus DB aktualisieren (buystatus true/false)
                        String[] dbCurrencies = CurrencyConfig.getBuyCurrencies();
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

                    // Buy
                    BuyOrderPocess.setBuyOrder(currency, FusionClientProvider.getClient(), LivePrice);

                    // Check
                    OrderIdList.clear();
                    OrderIdList.addAll(positionDAO.getBuyOrderIdsWhereStatusZero(currency));
                    CheckOrderStatus.OrderStatus(currency, FusionClientProvider.getClient(), OrderIdList, LivePrice);

                    // Sell
                    getDataRecords.clear();
                    getDataRecords.addAll(positionDAO.getDataRecordsWhereStatusOneOrSeven(currency));
                    SellOrderProcess.setSellOrder(currency, FusionClientProvider.getClient(), getDataRecords, LivePrice);
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
                System.out.println(Time.getCurrent_DateTimeWith_HHmmss());
                System.out.println("↻ Warte 60s und versuche erneut...");
                sleep.for_60_seconds();
                continue;
            }
        }       
    }

}
