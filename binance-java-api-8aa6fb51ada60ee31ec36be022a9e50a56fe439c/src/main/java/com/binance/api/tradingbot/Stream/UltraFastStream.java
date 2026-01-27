package com.binance.api.tradingbot.Stream;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.TickerEvent;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.tradingbot.constants.TradingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resiliente Stream-Klasse mit Auto-Reconnect und REST-Fallback.
 * 
 * Features:
 * - Echtzeit-Preise via WebSocket (kein Polling, kein Rate-Limit-Verbrauch)
 * - Automatischer Reconnect mit Exponential Backoff bei Verbindungsabbruch
 * - Stale-Data-Detection: Erkennt wenn Daten veraltet sind
 * - REST-Fallback: Nach max. Reconnect-Versuchen wechselt auf REST-Polling
 * - Single-Currency-Optimiert: start(String symbol) für einfache Nutzung
 * 
 * Verwendung:
 * <pre>
 *   UltraFastStream stream = new UltraFastStream();
 *   stream.start("LTCEUR");
 *   
 *   // In der Trading-Schleife:
 *   Double price = stream.getPrice();
 *   if (price == null) {
 *       // Fallback auf REST via Ticker.get_CurrencyPair_Price()
 *   }
 *   
 *   // Am Ende:
 *   stream.stop();
 * </pre>
 * 
 * @author Trading Bot
 */
public class UltraFastStream {
    
    private static final Logger logger = LoggerFactory.getLogger(UltraFastStream.class);
    
    // WebSocket Client & Connection
    private final BinanceApiClientFactory factory;
    private BinanceApiWebSocketClient webSocketClient;
    private Closeable connection;
    
    // REST Client für Fallback
    private BinanceApiRestClient restClient;
    
    // Aktuelles Symbol
    private String symbol;
    
    // Preis-Cache (thread-safe)
    private volatile BigDecimal currentPrice;
    private volatile long lastUpdateTime;
    
    // Status-Flags (thread-safe)
    private volatile boolean active = false;
    private volatile boolean reconnecting = false;
    private volatile ConnectionStatus status = ConnectionStatus.STOPPED;
    
    // Reconnect-Tracking
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    // Scheduler für Reconnect und Health-Check
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> healthCheckFuture;
    private ScheduledFuture<?> restFallbackFuture;
    
    /**
     * Verbindungsstatus des Streams.
     */
    public enum ConnectionStatus {
        /** Stream ist gestoppt */
        STOPPED,
        /** WebSocket verbunden und Daten werden empfangen */
        CONNECTED,
        /** Reconnect-Versuch läuft */
        RECONNECTING,
        /** Daten sind veraltet (älter als Threshold) */
        STALE,
        /** REST-Fallback-Modus aktiv */
        REST_FALLBACK
    }
    
    /**
     * Erstellt einen neuen UltraFastStream.
     */
    public UltraFastStream() {
        this.factory = BinanceApiClientFactory.newInstance();
        this.webSocketClient = factory.newWebSocketClient();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Startet den WebSocket-Stream für ein einzelnes Symbol.
     * 
     * @param symbol Das Währungspaar (z.B. "LTCEUR")
     */
    public void start(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol darf nicht leer sein");
        }
        
        this.symbol = symbol.toUpperCase();
        this.active = true;
        this.status = ConnectionStatus.RECONNECTING;
        
        logger.info("🚀 Starte UltraFastStream für: {}", this.symbol);
        
        // WebSocket-Verbindung starten
        connectWebSocket();
        
        // Health-Check starten (prüft alle 10 Sekunden auf stale data)
        startHealthCheck();
    }
    
    /**
     * Erstellt die WebSocket-Verbindung.
     */
    private void connectWebSocket() {
        try {
            // Falls alte Connection noch existiert, schließen
            closeConnection();
            
            // Neuen WebSocket-Client erstellen (wichtig für Reconnect!)
            this.webSocketClient = factory.newWebSocketClient();
            
            // Symbol in Kleinbuchstaben für Binance WebSocket API
            String wsSymbol = symbol.toLowerCase();
            logger.info("🔌 Verbinde WebSocket für Symbol: {} (WS: {})", symbol, wsSymbol);
            
            // WebSocket starten
            this.connection = webSocketClient.onTickerEvent(wsSymbol, 
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
            
            logger.info("✅ WebSocket-Verbindung hergestellt für {}", symbol);
            
        } catch (Exception e) {
            logger.error("❌ Fehler beim WebSocket-Verbindungsaufbau: {}", e.getMessage(), e);
            handleWebSocketFailure(e);
        }
    }
    
    /**
     * Verarbeitet eingehende Ticker-Updates.
     */
    private void handleTickerUpdate(TickerEvent ticker) {
        String priceStr = ticker.getCurrentDaysClosePrice();
        String tickerSymbol = ticker.getSymbol();
        
        // Debug: Zeige was ankommt
        if (currentPrice == null) {
            logger.info("📥 Erster Ticker empfangen: Symbol={}, Preis={}", tickerSymbol, priceStr);
        }
        
        if (priceStr != null) {
            try {
                BigDecimal price = new BigDecimal(priceStr);
                this.currentPrice = price;
                this.lastUpdateTime = System.currentTimeMillis();
                
                // Erfolgreiche Verbindung - Status aktualisieren
                if (status != ConnectionStatus.CONNECTED) {
                    status = ConnectionStatus.CONNECTED;
                    reconnectAttempts.set(0);
                    reconnecting = false;
                    
                    // Falls REST-Fallback aktiv war, stoppen
                    stopRestFallback();
                    
                    logger.info("✅ Stream CONNECTED - Empfange Live-Preise für {} = €{}", symbol, price);
                }
                
            } catch (NumberFormatException e) {
                logger.warn("Fehler beim Parsen des Preises '{}': {}", priceStr, e.getMessage());
            }
        } else {
            logger.warn("⚠️ Ticker empfangen aber Preis ist null für Symbol: {}", tickerSymbol);
        }
    }
    
    /**
     * Behandelt WebSocket-Fehler und startet Reconnect.
     */
    private void handleWebSocketFailure(Throwable cause) {
        logger.error("❌ WebSocket-Fehler für {}: {}", symbol, cause.getMessage());
        
        if (active && !reconnecting) {
            attemptReconnect();
        }
    }
    
    /**
     * Versucht eine Wiederverbindung mit Exponential Backoff.
     */
    private void attemptReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();
        
        if (attempts > TradingConstants.STREAM_MAX_RECONNECT_ATTEMPTS) {
            logger.warn("⚠️ Max Reconnect-Versuche ({}) erreicht. Wechsle zu REST-Fallback.", 
                TradingConstants.STREAM_MAX_RECONNECT_ATTEMPTS);
            switchToRestFallback();
            return;
        }
        
        reconnecting = true;
        status = ConnectionStatus.RECONNECTING;
        
        long delay = calculateBackoff(attempts);
        logger.info("🔄 Reconnect-Versuch {}/{} in {}ms für {}", 
            attempts, TradingConstants.STREAM_MAX_RECONNECT_ATTEMPTS, delay, symbol);
        
        scheduler.schedule(() -> {
            try {
                connectWebSocket();
            } catch (Exception e) {
                logger.error("❌ Reconnect fehlgeschlagen: {}", e.getMessage());
                reconnecting = false;
                attemptReconnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Berechnet Exponential Backoff: 1s, 2s, 4s, 8s, ... max 60s
     */
    private long calculateBackoff(int attempt) {
        long delay = TradingConstants.STREAM_INITIAL_RECONNECT_DELAY_MS * (long) Math.pow(2, attempt - 1);
        return Math.min(delay, TradingConstants.STREAM_MAX_RECONNECT_DELAY_MS);
    }
    
    /**
     * Startet den Health-Check-Timer.
     */
    private void startHealthCheck() {
        healthCheckFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!active) return;
            
            long now = System.currentTimeMillis();
            long dataAge = now - lastUpdateTime;
            
            // Prüfe auf stale data
            if (lastUpdateTime > 0 && dataAge > TradingConstants.STREAM_STALE_DATA_THRESHOLD_MS) {
                if (status == ConnectionStatus.CONNECTED) {
                    logger.warn("⚠️ Stale data für {} - Letztes Update vor {}s. Starte Reconnect...", 
                        symbol, dataAge / 1000);
                    status = ConnectionStatus.STALE;
                    attemptReconnect();
                }
            }
            
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Wechselt in den REST-Fallback-Modus.
     */
    private void switchToRestFallback() {
        logger.info("📡 Wechsle zu REST-Fallback-Modus für {}", symbol);
        status = ConnectionStatus.REST_FALLBACK;
        reconnecting = false;
        
        // REST-Client initialisieren falls noch nicht geschehen
        if (restClient == null) {
            restClient = factory.newRestClient();
        }
        
        // REST-Polling starten
        restFallbackFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                TickerPrice tickerPrice = restClient.getPrice(symbol);
                if (tickerPrice != null && tickerPrice.getPrice() != null) {
                    this.currentPrice = new BigDecimal(tickerPrice.getPrice());
                    this.lastUpdateTime = System.currentTimeMillis();
                }
            } catch (Exception e) {
                logger.error("REST-Fallback Fehler für {}: {}", symbol, e.getMessage());
            }
        }, 0, TradingConstants.STREAM_REST_FALLBACK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        // Periodisch WebSocket-Reconnect versuchen (alle 5 Minuten)
        scheduler.scheduleAtFixedRate(() -> {
            if (status == ConnectionStatus.REST_FALLBACK && active) {
                logger.info("🔄 Versuche WebSocket wiederherzustellen für {}...", symbol);
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
            logger.info("REST-Fallback gestoppt - WebSocket wieder aktiv");
        }
    }
    
    /**
     * Schließt die aktuelle WebSocket-Verbindung.
     */
    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                logger.debug("Fehler beim Schließen der Connection: {}", e.getMessage());
            }
            connection = null;
        }
    }
    
    /**
     * Gibt den aktuellen Preis zurück.
     * 
     * Gibt null zurück wenn:
     * - Noch keine Daten empfangen wurden
     * - Daten älter als STALE_DATA_THRESHOLD_MS sind
     * 
     * @return Aktueller Preis als Double, oder null wenn nicht verfügbar
     */
    public Double getPrice() {
        if (currentPrice == null) {
            return null;
        }
        
        // Prüfe ob Daten zu alt sind (nur wenn im CONNECTED-Status)
        if (status == ConnectionStatus.CONNECTED) {
            long dataAge = System.currentTimeMillis() - lastUpdateTime;
            if (dataAge > TradingConstants.STREAM_STALE_DATA_THRESHOLD_MS) {
                logger.debug("Preis für {} ist stale ({}s alt)", symbol, dataAge / 1000);
                return null;
            }
        }
        
        return currentPrice.doubleValue();
    }
    
    /**
     * Gibt den aktuellen Preis als BigDecimal zurück.
     * 
     * @return Aktueller Preis, oder null wenn nicht verfügbar
     */
    public BigDecimal getPriceBigDecimal() {
        Double price = getPrice();
        return price != null ? BigDecimal.valueOf(price) : null;
    }
    
    /**
     * Gibt den aktuellen Verbindungsstatus zurück.
     * 
     * @return ConnectionStatus
     */
    public ConnectionStatus getStatus() {
        return status;
    }
    
    /**
     * Prüft ob der Stream aktiv ist und Daten empfängt.
     * 
     * @return true wenn verbunden und Daten vorhanden
     */
    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED || status == ConnectionStatus.REST_FALLBACK;
    }
    
    /**
     * Prüft ob Daten vorhanden sind (unabhängig vom Alter).
     * 
     * @return true wenn mindestens ein Preis empfangen wurde
     */
    public boolean hasData() {
        return currentPrice != null;
    }
    
    /**
     * Gibt das aktuelle Symbol zurück.
     * 
     * @return Symbol (z.B. "LTCEUR")
     */
    public String getSymbol() {
        return symbol;
    }
    
    /**
     * Zeigt Status-Informationen im Log.
     */
    public void showStatus() {
        logger.info("📊 === STREAM STATUS ===");
        logger.info("Symbol: {}", symbol);
        logger.info("Status: {}", status);
        logger.info("Reconnect-Versuche: {}", reconnectAttempts.get());
        
        if (currentPrice != null) {
            long secondsAgo = (System.currentTimeMillis() - lastUpdateTime) / 1000;
            logger.info("Preis: €{} (vor {}s)", currentPrice, secondsAgo);
        } else {
            logger.info("Preis: Keine Daten");
        }
        logger.info("========================");
    }
    
    /**
     * Stoppt den Stream und gibt alle Ressourcen frei.
     */
    public void stop() {
        logger.info("🛑 Stoppe UltraFastStream für {}", symbol);
        
        active = false;
        status = ConnectionStatus.STOPPED;
        
        try {
            // Health-Check stoppen
            if (healthCheckFuture != null) {
                healthCheckFuture.cancel(false);
            }
            
            // REST-Fallback stoppen
            stopRestFallback();
            
            // WebSocket-Connection schließen
            closeConnection();
            
            // Scheduler herunterfahren
            if (scheduler != null) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
            
            logger.info("✅ UltraFastStream gestoppt");
            
        } catch (Exception e) {
            logger.error("Fehler beim Stoppen: {}", e.getMessage());
        }
    }
}
