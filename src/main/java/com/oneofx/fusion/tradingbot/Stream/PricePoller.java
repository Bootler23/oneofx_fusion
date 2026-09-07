package com.oneofx.fusion.tradingbot.Stream;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.TickerPrice;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Preis-Polling via Bitpanda Fusion REST API.
 *
 * Holt den Preis je Symbol einzeln per getPrice() (Weight 1 pro Symbol).
 * Ersetzt den WebSocket-Stream durch einfaches REST-Polling.
 */
public class PricePoller {

    private static final Logger logger = LoggerFactory.getLogger(PricePoller.class);

    private static final long POLL_INTERVAL_MS = 1000;

    private final FusionApiClient restClient;

    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private final Set<String> watchedSymbols = ConcurrentHashMap.newKeySet();

    private volatile boolean active = false;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;

    public PricePoller() {
        this.restClient = FusionClientProvider.getClient();
    }

    /**
     * Startet das REST-Polling für die angegebenen Symbole.
     *
     * @param symbols Array von Währungspaaren (z.B. {"LTCEUR", "BTCUSDC"})
     */
    public void start(String[] symbols) {
        if (symbols == null || symbols.length == 0) {
            throw new IllegalArgumentException("Symbols-Array darf nicht leer sein");
        }

        for (String symbol : symbols) {
            watchedSymbols.add(FusionSymbol.compactPair(symbol));
        }

        this.active = true;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PricePoller-Thread");
            t.setDaemon(true);
            return t;
        });

        // Sofortige Initialisierung
        fetchPrices();

        pollFuture = scheduler.scheduleAtFixedRate(
                this::fetchPrices,
                POLL_INTERVAL_MS,
                POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        logger.info("[PricePoller] Gestartet – {} Symbole, Intervall: {}ms",
                watchedSymbols.size(), POLL_INTERVAL_MS);
    }

    /**
     * Fügt neue Symbole zum Polling hinzu.
     *
     * @param newSymbols Array neuer Währungspaare
     */
    public synchronized void addSymbols(String[] newSymbols) {
        if (newSymbols == null || newSymbols.length == 0) {
            return;
        }

        int added = 0;
        for (String symbol : newSymbols) {
            if (watchedSymbols.add(FusionSymbol.compactPair(symbol))) {
                added++;
            }
        }

        if (added > 0) {
            logger.info("[PricePoller] {} neue Symbole hinzugefügt – total: {}", added, watchedSymbols.size());
        }
    }

    private void fetchPrices() {
        if (!active) return;
        try {
            for (TickerPrice ticker : restClient.getAllPrices()) {
                if (ticker == null || ticker.getSymbol() == null || ticker.getPrice() == null) continue;
                String compactPair = FusionSymbol.compactPair(ticker.getSymbol());
                if (watchedSymbols.contains(compactPair)) {
                    prices.put(compactPair, new BigDecimal(ticker.getPrice()));
                }
            }
        } catch (Exception e) {
            logger.warn("[PricePoller] Fehler beim Abrufen der Fusion-Preise: {}", e.getMessage());
        }
    }

    /**
     * Gibt den aktuellen Preis für ein Symbol zurück.
     *
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @return Aktueller Preis oder null wenn nicht verfügbar
     */
    public Double getPrice(String symbol) {
        BigDecimal price = prices.get(FusionSymbol.compactPair(symbol));
        return price != null ? price.doubleValue() : null;
    }

    /**
     * Prüft ob ein Symbol bereits Daten hat.
     */
    public boolean hasData(String symbol) {
        return prices.containsKey(FusionSymbol.compactPair(symbol));
    }

    /**
     * Anzahl der Symbole für die bereits Daten vorhanden sind.
     */
    public int getActiveSymbolCount() {
        int count = 0;
        for (String sym : watchedSymbols) {
            if (prices.containsKey(sym)) count++;
        }
        return count;
    }

    /**
     * Stoppt das Polling und gibt alle Ressourcen frei.
     */
    public void stop() {
        logger.info("[PricePoller] Stoppe...");
        active = false;

        if (pollFuture != null) {
            pollFuture.cancel(false);
        }

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
        }

        logger.info("[PricePoller] Gestoppt");
    }
}
