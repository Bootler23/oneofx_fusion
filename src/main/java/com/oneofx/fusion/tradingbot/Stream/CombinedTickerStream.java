package com.oneofx.fusion.tradingbot.Stream;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.TickerEvent;
import com.binance.api.client.domain.market.TickerPrice;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Combined Individual Symbol Ticker Stream gemäß Binance Best Practices.
 *
 * Nutzt combined individual streams: <symbol>@ticker für jedes Symbol.
 * Binance Doku: "Individual Symbol Ticker Streams - Update Speed: 1000ms"
 * Jeder <symbol>@ticker wird GARANTIERT jede Sekunde gepusht.
 *
 * WICHTIG - Warum NICHT !ticker@arr:
 * Binance Doku sagt für All-Market-Streams (!ticker@arr, !miniTicker@arr):
 * "Note that only tickers that have changed will be present in the array."
 * Low-Volume EUR-Paare können dadurch fehlen!
 *
 * Architektur:
 * - 1 WebSocket-Connection: /ws/ltceur@ticker/soleur@ticker/...
 * - Jedes Symbol bekommt garantiert jede Sekunde ein Update
 * - REST-Initialisierung beim Start für sofortige Preisverfügbarkeit
 * - Proaktiver 24h-Reconnect (Binance-Limit)
 * - Exponential Backoff bei Verbindungsfehlern
 * - REST-Fallback nach max. Reconnect-Versuchen
 *
 * Verwendung:
 * <pre>
 *   CombinedTickerStream stream = new CombinedTickerStream();
 *   stream.start(new String[]{"LTCEUR", "SOLEUR", "LINKEUR"});
 *
 *   Double price = stream.getPrice("LTCEUR");
 *
 *   stream.stop();
 * </pre>
 */
public class CombinedTickerStream {

    private static final Logger logger = LoggerFactory.getLogger(CombinedTickerStream.class);

    /** Binance trennt WebSocket-Connections nach 24 Stunden. Proaktiver Reconnect vorher. */
    private static final long RECONNECT_BEFORE_24H_MS = 23 * 60 * 60 * 1000L; // 23 Stunden

    // WebSocket Client & Connection
    private final BinanceApiClientFactory factory;
    private BinanceApiWebSocketClient webSocketClient;
    private Closeable connection;

    // REST Client für Fallback
    private BinanceApiRestClient restClient;

    // Symbole die gestreamt werden
    private String[] symbols;
    private Set<String> watchedSymbols;

    // Preis-Cache pro Symbol (thread-safe)
    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    // Globaler Connection-Status
    private volatile boolean active = false;
    private volatile boolean reconnecting = false;
    private volatile ConnectionStatus connectionStatus = ConnectionStatus.STOPPED;
    private volatile long connectionStartTime = 0;
    private volatile long lastTickReceived = 0;

    // Reconnect-Tracking
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile long lastReconnectTime = 0;

    // Scheduler
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> healthCheckFuture;
    private ScheduledFuture<?> reconnect24hFuture;
    private ScheduledFuture<?> restFallbackFuture;

    public enum ConnectionStatus {
        STOPPED, CONNECTED, RECONNECTING, REST_FALLBACK
    }

    public CombinedTickerStream() {
        this.factory = BinanceApiClientFactory.newInstance();
    }

    /**
     * Startet den Combined WebSocket Stream für alle angegebenen Symbole.
     *
     * @param symbols Array von Währungspaaren (z.B. {"LTCEUR", "SOLEUR", "LINKEUR"})
     */
    public void start(String[] symbols) {
        if (symbols == null || symbols.length == 0) {
            throw new IllegalArgumentException("Symbols-Array darf nicht leer sein");
        }

        this.symbols = Arrays.stream(symbols)
                .map(String::toUpperCase)
                .toArray(String[]::new);

        this.watchedSymbols = Arrays.stream(this.symbols)
                .collect(Collectors.toSet());

        this.active = true;
        this.scheduler = Executors.newScheduledThreadPool(3);

        logger.info("[START] Combined Individual Ticker Stream für {} Symbole: {}",
                this.symbols.length, String.join(", ", this.symbols));

        // REST-Initialisierung: Preise sofort verfügbar machen
        initPricesViaRest();

        connectWebSocket();
        startHealthCheck();
        schedule24hReconnect();
    }

    /**
     * Initialisiert alle Preise via REST API beim Start.
     * So sind Preise sofort verfügbar, ohne auf den ersten WebSocket-Tick warten zu müssen.
     */
    private void initPricesViaRest() {
        try {
            if (restClient == null) {
                restClient = factory.newRestClient();
            }

            List<TickerPrice> allPrices = restClient.getAllPrices();
            long now = System.currentTimeMillis();
            int initialized = 0;

            for (TickerPrice tp : allPrices) {
                String sym = tp.getSymbol().toUpperCase();
                if (watchedSymbols.contains(sym) && tp.getPrice() != null) {
                    prices.put(sym, new BigDecimal(tp.getPrice()));
                    lastUpdateTimes.put(sym, now);
                    initialized++;
                }
            }

            logger.info("[REST-INIT] {}/{} Preise via REST initialisiert", initialized, watchedSymbols.size());

            if (initialized < watchedSymbols.size()) {
                Set<String> missing = new java.util.HashSet<>(watchedSymbols);
                missing.removeAll(prices.keySet());
                logger.warn("[REST-INIT] Fehlende Symbole (existieren nicht auf Binance?): {}", missing);
            }
        } catch (Exception e) {
            logger.warn("[REST-INIT] REST-Initialisierung fehlgeschlagen: {} - warte auf WebSocket", e.getMessage());
        }
    }

    /**
     * Erstellt die Combined WebSocket-Verbindung für alle Symbole.
     *
     * Binance Doku: "Individual Symbol Ticker Streams - <symbol>@ticker"
     * Update Speed: 1000ms - wird GARANTIERT jede Sekunde pro Symbol gepusht.
     *
     * URL: wss://stream.binance.com:9443/ws/ltceur@ticker/soleur@ticker/...
     */
    private void connectWebSocket() {
        try {
            closeConnection();

            this.webSocketClient = factory.newWebSocketClient();

            // Komma-separierte Symbole in Kleinbuchstaben für Binance API
            String symbolList = Arrays.stream(symbols)
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(","));

            logger.info("[WS] Verbinde <symbol>@ticker Combined Stream: {} Symbole", symbols.length);

            this.connection = webSocketClient.onTickerEvent(symbolList,
                    new BinanceApiCallback<TickerEvent>() {
                        @Override
                        public void onResponse(TickerEvent ticker) {
                            handleTickerUpdate(ticker);
                        }

                        @Override
                        public void onFailure(Throwable cause) {
                            handleWebSocketFailure(cause);
                        }
                    });

            connectionStartTime = System.currentTimeMillis();

        } catch (Exception e) {
            logger.error("[ERROR] WebSocket-Verbindungsaufbau fehlgeschlagen: {}", e.getMessage());
            handleWebSocketFailure(e);
        }
    }

    /**
     * Verarbeitet eingehende Ticker-Events.
     * Jedes <symbol>@ticker Event kommt garantiert jede Sekunde.
     */
    private void handleTickerUpdate(TickerEvent ticker) {
        if (ticker.getSymbol() == null || ticker.getCurrentDaysClosePrice() == null) return;

        String symbol = ticker.getSymbol().toUpperCase();
        long now = System.currentTimeMillis();
        lastTickReceived = now;

        try {
            BigDecimal price = new BigDecimal(ticker.getCurrentDaysClosePrice());
            prices.put(symbol, price);
            lastUpdateTimes.put(symbol, now);
        } catch (NumberFormatException e) {
            logger.warn("Fehler beim Parsen des Preises für {}: {}", symbol, e.getMessage());
        }

        if (connectionStatus != ConnectionStatus.CONNECTED) {
            connectionStatus = ConnectionStatus.CONNECTED;
            reconnecting = false;
            stopRestFallback();
            logger.info("[OK] Combined Ticker Stream CONNECTED \u2013 empfange Daten");
        }

        if (reconnectAttempts.get() > 0 && lastReconnectTime > 0) {
            long stableSince = now - lastReconnectTime;
            if (stableSince > TradingConstants.STREAM_STALE_DATA_THRESHOLD_MS) {
                reconnectAttempts.set(0);
                logger.info("[OK] Verbindung stabil seit {}s \u2013 Reconnect-Counter zurückgesetzt",
                        stableSince / 1000);
            }
        }
    }

    /**
     * Behandelt WebSocket-Fehler.
     */
    private void handleWebSocketFailure(Throwable cause) {
        logger.error("[ERROR] WebSocket-Fehler: {}", cause != null ? cause.getMessage() : "unbekannt");
        if (active && !reconnecting) {
            attemptReconnect();
        }
    }

    /**
     * Reconnect mit Exponential Backoff.
     */
    private void attemptReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();

        if (attempts > TradingConstants.STREAM_MAX_RECONNECT_ATTEMPTS) {
            logger.warn("[WARN] Max Reconnect-Versuche ({}) erreicht. Wechsle zu REST-Fallback.",
                    TradingConstants.STREAM_MAX_RECONNECT_ATTEMPTS);
            switchToRestFallback();
            return;
        }

        reconnecting = true;
        connectionStatus = ConnectionStatus.RECONNECTING;
        lastReconnectTime = System.currentTimeMillis();

        long delay = TradingConstants.STREAM_INITIAL_RECONNECT_DELAY_MS * (long) Math.pow(2, attempts - 1);
        delay = Math.min(delay, TradingConstants.STREAM_MAX_RECONNECT_DELAY_MS);

        logger.info("[RECONNECT] Versuch {}/{} in {}ms", attempts, TradingConstants.STREAM_MAX_RECONNECT_ATTEMPTS, delay);

        scheduler.schedule(() -> {
            try {
                connectWebSocket();
                schedule24hReconnect();
            } catch (Exception e) {
                logger.error("[ERROR] Reconnect fehlgeschlagen: {}", e.getMessage());
                reconnecting = false;
                attemptReconnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Plant proaktiven Reconnect vor dem Binance 24h-Disconnect.
     * 
     * Binance Doku: "A single connection is only valid for 24 hours;
     * expect to be disconnected at the 24 hour mark."
     * 
     * Wir reconnecten nach 23 Stunden proaktiv, um unkontrollierte Disconnects zu vermeiden.
     */
    private void schedule24hReconnect() {
        // Alten Timer canceln falls vorhanden
        if (reconnect24hFuture != null && !reconnect24hFuture.isCancelled()) {
            reconnect24hFuture.cancel(false);
        }

        reconnect24hFuture = scheduler.schedule(() -> {
            if (!active) return;

            logger.info("[24H] Proaktiver Reconnect nach 23h (Binance 24h-Limit)");
            reconnectAttempts.set(0);
            try {
                connectWebSocket();
                schedule24hReconnect(); // Nächsten 24h-Reconnect planen
            } catch (Exception e) {
                logger.error("[24H] Proaktiver Reconnect fehlgeschlagen: {}", e.getMessage());
                attemptReconnect();
            }
        }, RECONNECT_BEFORE_24H_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Health-Check: Prüft ob die Connection noch Daten liefert.
     * Bei individual streams: Jedes Symbol bekommt jede Sekunde ein Event.
     * Wenn 10s lang KEIN Event für IRGENDEIN Symbol kommt, ist die Connection tot.
     */
    private void startHealthCheck() {
        healthCheckFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!active || connectionStatus == ConnectionStatus.RECONNECTING) return;

            long now = System.currentTimeMillis();
            long age = now - lastTickReceived;

            // Seit 10s kein Tick → Connection tot → Reconnect
            if (lastTickReceived > 0 && age > 10_000) {
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    logger.warn("[WARN] Kein Tick seit {}s \u2013 Connection verloren. Starte Reconnect...", age / 1000);
                    connectionStatus = ConnectionStatus.RECONNECTING;
                    attemptReconnect();
                }
            }

            // EUR-Paare handeln oft minutenlang nicht – nur als DEBUG loggen
            if (connectionStatus == ConnectionStatus.CONNECTED && lastTickReceived > 0) {
                for (String sym : symbols) {
                    Long lastUpdate = lastUpdateTimes.get(sym);
                    if (lastUpdate != null) {
                        long symAge = now - lastUpdate;
                        if (symAge > TradingConstants.STREAM_STALE_DATA_THRESHOLD_MS) {
                            // logger.debug("[STALE] {} hat seit {}s kein WS-Update (Low-Volume EUR-Paar)", sym, symAge / 1000);
                        }
                    }
                }
            }

        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * REST-Fallback: holt alle Preise via getAllPrices() wenn WebSocket dauerhaft ausfällt.
     */
    private void switchToRestFallback() {
        logger.info("[REST] Wechsle zu REST-Fallback-Modus für alle {} Symbole", symbols.length);
        connectionStatus = ConnectionStatus.REST_FALLBACK;
        reconnecting = false;

        if (restClient == null) {
            restClient = factory.newRestClient();
        }

        restFallbackFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                List<TickerPrice> allPrices = restClient.getAllPrices();
                long now = System.currentTimeMillis();

                for (TickerPrice tp : allPrices) {
                    String sym = tp.getSymbol().toUpperCase();
                    if (watchedSymbols.contains(sym) && tp.getPrice() != null) {
                        prices.put(sym, new BigDecimal(tp.getPrice()));
                        lastUpdateTimes.put(sym, now);
                    }
                }
            } catch (Exception e) {
                logger.error("REST-Fallback Fehler: {}", e.getMessage());
            }
        }, 0, TradingConstants.STREAM_REST_FALLBACK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Periodisch WebSocket-Reconnect versuchen (alle 5 Minuten)
        scheduler.scheduleAtFixedRate(() -> {
            if (connectionStatus == ConnectionStatus.REST_FALLBACK && active) {
                logger.info("[RECONNECT] Versuche WebSocket wiederherzustellen...");
                reconnectAttempts.set(0);
                attemptReconnect();
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Stoppt den REST-Fallback.
     */
    private void stopRestFallback() {
        if (restFallbackFuture != null && !restFallbackFuture.isCancelled()) {
            restFallbackFuture.cancel(false);
            restFallbackFuture = null;
            logger.info("REST-Fallback gestoppt - Combined WebSocket wieder aktiv");
        }
    }

    /**
     * Schließt die aktuelle WebSocket-Verbindung.
     */
    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (NullPointerException e) {
                logger.trace("Connection bereits geschlossen oder reason=null: {}", e.getMessage());
            } catch (Exception e) {
                logger.trace("Fehler beim Schließen der Connection: {}", e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    // ========== Public API ==========

    /**
     * Gibt den aktuellen Preis für ein Symbol zurück.
     * Preis kommt aus WebSocket (jede Sekunde) oder REST-Initialisierung.
     *
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @return Aktueller Preis oder null wenn nicht verfügbar
     */
    public Double getPrice(String symbol) {
        BigDecimal price = prices.get(symbol.toUpperCase());
        return price != null ? price.doubleValue() : null;
    }

    /**
     * Prüft ob ein Symbol bereits Daten empfangen hat.
     */
    public boolean hasData(String symbol) {
        return prices.containsKey(symbol.toUpperCase());
    }

    /**
     * Anzahl der Symbole für die bereits Daten empfangen wurden.
     */
    public int getActiveSymbolCount() {
        if (watchedSymbols == null) return 0;
        int count = 0;
        for (String symbol : watchedSymbols) {
            if (prices.containsKey(symbol)) count++;
        }
        return count;
    }

    /**
     * Connection-Status des Streams.
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * Gibt zurueck ob der Stream aktiv ist.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Fuegt neue Symbole zum laufenden Stream hinzu.
     *
     * Bereits bekannte Symbole werden ignoriert. Die WebSocket-Verbindung wird
     * mit der erweiterten Symbol-Liste neu aufgebaut (Binance Combined Streams
     * erlauben kein dynamisches Hinzufuegen ohne Reconnect).
     *
     * Ablauf:
     * 1. Neue Symbole identifizieren (noch nicht in watchedSymbols)
     * 2. REST-Preise fuer neue Symbole initialisieren
     * 3. Symbol-Array und watchedSymbols-Set erweitern
     * 4. WebSocket mit komplettem Symbol-Set neu verbinden
     *
     * @param newSymbols Array neuer Waehrungspaare (z.B. {"BNBEUR", "SOLEUR"})
     */
    public synchronized void addSymbols(String[] newSymbols) {
        if (newSymbols == null || newSymbols.length == 0) {
            return;
        }

        // Nur wirklich neue Symbole identifizieren
        List<String> toAdd = new java.util.ArrayList<>();
        for (String sym : newSymbols) {
            String upper = sym.toUpperCase();
            if (!watchedSymbols.contains(upper)) {
                toAdd.add(upper);
            }
        }

        if (toAdd.isEmpty()) {
            logger.debug("[STREAM-UPDATE] Keine neuen Symbole — alle bereits im Stream");
            return;
        }

        logger.info("[STREAM-UPDATE] Fuege {} neue Symbole hinzu: {}", toAdd.size(), toAdd);

        // Symbol-Array erweitern
        String[] merged = new String[symbols.length + toAdd.size()];
        System.arraycopy(symbols, 0, merged, 0, symbols.length);
        for (int i = 0; i < toAdd.size(); i++) {
            merged[symbols.length + i] = toAdd.get(i);
        }
        this.symbols = merged;

        // watchedSymbols-Set erweitern
        this.watchedSymbols = Arrays.stream(this.symbols).collect(Collectors.toSet());

        // REST-Init nur fuer neue Symbole (sofortige Preisverfuegbarkeit)
        initPricesForNewSymbols(toAdd);

        // WebSocket mit komplettem Symbol-Set neu verbinden
        reconnectAttempts.set(0);
        connectWebSocket();
        schedule24hReconnect();

        logger.info("[STREAM-UPDATE] Stream neu verbunden mit {} Symbolen total (neu: {})",
                this.symbols.length, toAdd);
    }

    /**
     * Initialisiert Preise via REST API nur fuer die angegebenen Symbole.
     */
    private void initPricesForNewSymbols(List<String> newSymbols) {
        try {
            if (restClient == null) {
                restClient = factory.newRestClient();
            }

            Set<String> newSet = new java.util.HashSet<>(newSymbols);
            List<TickerPrice> allPrices = restClient.getAllPrices();
            long now = System.currentTimeMillis();
            int initialized = 0;

            for (TickerPrice tp : allPrices) {
                String sym = tp.getSymbol().toUpperCase();
                if (newSet.contains(sym) && tp.getPrice() != null) {
                    prices.put(sym, new BigDecimal(tp.getPrice()));
                    lastUpdateTimes.put(sym, now);
                    initialized++;
                }
            }

            logger.info("[REST-INIT] {}/{} neue Symbole via REST initialisiert", initialized, newSymbols.size());

        } catch (Exception e) {
            logger.warn("[REST-INIT] REST-Init fuer neue Symbole fehlgeschlagen: {} — warte auf WebSocket",
                    e.getMessage());
        }
    }

    /**
     * Stoppe den Stream und gib alle Ressourcen frei.
     */
    public void stop() {
        logger.info("[STOP] Stoppe CombinedTickerStream");
        active = false;
        connectionStatus = ConnectionStatus.STOPPED;

        try {
            if (healthCheckFuture != null) healthCheckFuture.cancel(false);
            if (reconnect24hFuture != null) reconnect24hFuture.cancel(false);
            stopRestFallback();
            closeConnection();

            if (scheduler != null) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
            logger.info("[OK] CombinedTickerStream gestoppt");
        } catch (Exception e) {
            logger.error("Fehler beim Stoppen: {}", e.getMessage());
        }
    }
}
