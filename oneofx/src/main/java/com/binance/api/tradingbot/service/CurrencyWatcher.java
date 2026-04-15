package com.binance.api.tradingbot.service;

import com.binance.api.tradingbot.Settings.CurrencyConfig;
import com.binance.api.tradingbot.SQL_Database.TradingRulesSQL;
import com.binance.api.tradingbot.Stream.CombinedTickerStream;
import com.binance.api.tradingbot.TradingMain.oneofx;
import com.binance.api.tradingbot.constants.TradingConstants;
import com.binance.api.tradingbot.domain.TradingRules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ueberwacht die currency-Tabelle auf neu aktivierte Waehrungen (buystatus=true).
 *
 * Pollt alle {@link TradingConstants#CURRENCY_WATCH_INTERVAL_SECONDS} Sekunden die DB.
 * Wenn neue Waehrungen erkannt werden (buystatus von false auf true gewechselt),
 * werden diese automatisch zum CombinedTickerStream und zum Trading-Loop hinzugefuegt.
 *
 * Bereits aktive Waehrungen werden NICHT entfernt wenn buystatus auf false wechselt —
 * die bestehende Buy-Guard-Logik in der Main-Loop verhindert neue Kaeufe,
 * Check/Sell laufen weiter.
 */
public class CurrencyWatcher {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyWatcher.class);

    private final CombinedTickerStream combinedStream;
    private ScheduledExecutorService scheduler;

    /**
     * Erstellt einen neuen CurrencyWatcher.
     *
     * @param combinedStream Der laufende WebSocket-Stream, zu dem neue Symbole hinzugefuegt werden
     */
    public CurrencyWatcher(CombinedTickerStream combinedStream) {
        if (combinedStream == null) {
            throw new IllegalArgumentException("CombinedTickerStream darf nicht null sein");
        }
        this.combinedStream = combinedStream;
    }

    /**
     * Startet das periodische Polling der currency-Tabelle.
     * Erster Check nach initialem Delay, dann alle CURRENCY_WATCH_INTERVAL_SECONDS.
     */
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CurrencyWatcher-Thread");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::checkForNewCurrencies,
                TradingConstants.CURRENCY_WATCH_INTERVAL_SECONDS,
                TradingConstants.CURRENCY_WATCH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        logger.info("[CURRENCY-WATCHER] Gestartet — pruefe alle {}s auf neue Waehrungen",
                TradingConstants.CURRENCY_WATCH_INTERVAL_SECONDS);
    }

    /**
     * Stoppt den CurrencyWatcher und gibt Ressourcen frei.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("[CURRENCY-WATCHER] Gestoppt");
        }
    }

    /**
     * Prueft ob in der DB neue Waehrungen mit buystatus=true existieren,
     * die noch nicht im aktiven Trading-Array enthalten sind.
     *
     * Bei neuen Waehrungen:
     * 1. Trading-Rules von Binance laden und in DB speichern
     * 2. Waehrungs-Array in LTC_EUR_Live erweitern (atomarer Swap)
     * 3. CombinedTickerStream um neue Symbole erweitern (Reconnect)
     */
    private void checkForNewCurrencies() {
        try {
            // Aktuelle Waehrungen mit buystatus=true aus DB
            String[] dbCurrencies = CurrencyConfig.getBuyCurrencies();

            // Aktuell aktive Waehrungen im Bot
            String[] activeCurrencies = oneofx.getActiveCurrencies();

            if (activeCurrencies == null) {
                logger.debug("[CURRENCY-WATCHER] Bot noch nicht initialisiert — uebersprungen");
                return;
            }

            // Finde Waehrungen die in DB aktiv sind aber noch nicht im Bot
            Set<String> activeSet = new HashSet<>(Arrays.asList(activeCurrencies));
            List<String> newCurrencies = new ArrayList<>();

            for (String dbCurrency : dbCurrencies) {
                if (!activeSet.contains(dbCurrency)) {
                    newCurrencies.add(dbCurrency);
                }
            }

            if (newCurrencies.isEmpty()) {
                return; // Nichts zu tun — keine neuen Waehrungen
            }

            logger.info("[CURRENCY-WATCHER] {} neue Waehrung(en) erkannt: {}",
                    newCurrencies.size(), newCurrencies);

            // 1. Trading-Rules fuer neue Waehrungen laden
            TradingRulesService tradingRulesService = TradingRulesService.getInstance();
            int rulesLoaded = 0;

            for (String symbol : newCurrencies) {
                try {
                    TradingRules rules = tradingRulesService.getTradingRules(symbol);
                    if (rules != null) {
                        TradingRulesSQL.saveTradingRules(rules);
                        rulesLoaded++;
                        logger.info("[CURRENCY-WATCHER] Trading-Rules fuer {} geladen", symbol);
                    } else {
                        logger.warn("[CURRENCY-WATCHER] Keine Trading-Rules fuer {} gefunden", symbol);
                    }
                } catch (Exception e) {
                    logger.error("[CURRENCY-WATCHER] Fehler beim Laden der Trading-Rules fuer {}: {}",
                            symbol, e.getMessage());
                }
            }

            // 2. Waehrungs-Array in LTC_EUR_Live erweitern
            String[] merged = new String[activeCurrencies.length + newCurrencies.size()];
            System.arraycopy(activeCurrencies, 0, merged, 0, activeCurrencies.length);
            for (int i = 0; i < newCurrencies.size(); i++) {
                merged[activeCurrencies.length + i] = newCurrencies.get(i);
            }
            oneofx.setActiveCurrencies(merged);

            // 3. CombinedTickerStream um neue Symbole erweitern
            String[] newSymbolsArray = newCurrencies.toArray(new String[0]);
            combinedStream.addSymbols(newSymbolsArray);

            logger.info("[CURRENCY-WATCHER] Aktivierung abgeschlossen — {} neue Waehrung(en), " +
                            "{} Trading-Rules geladen, {} Symbole total im Bot",
                    newCurrencies.size(), rulesLoaded, merged.length);

        } catch (Exception e) {
            logger.error("[CURRENCY-WATCHER] Fehler beim Pruefen auf neue Waehrungen: {}",
                    e.getMessage(), e);
        }
    }
}
