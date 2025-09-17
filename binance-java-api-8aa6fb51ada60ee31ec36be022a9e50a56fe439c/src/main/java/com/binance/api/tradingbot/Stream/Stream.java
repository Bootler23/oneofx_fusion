package com.binance.api.tradingbot.Stream;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.event.TickerEvent;

import java.io.Closeable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hochperformante Stream-Klasse für das gleichzeitige Abrufen von Ticker-Preisen
 * mit garantierten 1-Sekunden-Updates
 */
public class Stream {
    
    private final BinanceApiWebSocketClient webSocketClient;
    private final Map<String, BigDecimal> currentPrices;
    private final Map<String, TickerEvent> currentTickerData;
    private final Map<String, Long> lastUpdateTimes;
    private Closeable streamConnection;
    private Consumer<Map<String, BigDecimal>> priceUpdateCallback;
    private Consumer<String> errorCallback;
    private ScheduledExecutorService scheduler;
    private volatile boolean isActive = false;
    private long lastCallbackTime = 0;
    
    /**
     * Konstruktor für die Stream-Klasse
     */
    public Stream() {
        // WebSocket-Client ohne Authentifizierung erstellen (für öffentliche Daten)
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
        this.webSocketClient = factory.newWebSocketClient();
        this.currentPrices = new ConcurrentHashMap<>();
        this.currentTickerData = new ConcurrentHashMap<>();
        this.lastUpdateTimes = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Startet den Stream für die angegebenen Währungspaare mit garantierten 1-Sekunden-Updates
     * 
     * @param symbols Liste der Währungspaare (z.B. "BTCEUR", "ETHEUR", "LTCEUR")
     * @param onPriceUpdate Callback-Funktion die jede Sekunde aufgerufen wird
     * @param onError Callback-Funktion für Fehlerbehandlung (optional)
     */
    public void startPriceStream(List<String> symbols, 
                                Consumer<Map<String, BigDecimal>> onPriceUpdate,
                                Consumer<String> onError) {
        
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Währungsliste darf nicht leer sein");
        }
        
        this.priceUpdateCallback = onPriceUpdate;
        this.errorCallback = onError != null ? onError : (error) -> System.err.println("Stream Error: " + error);
        
        // Symbole zu einem komma-separierten String konvertieren und zu Großbuchstaben
        List<String> upperSymbols = new ArrayList<>();
        for (String symbol : symbols) {
            upperSymbols.add(symbol.toUpperCase());
        }
        String symbolsString = String.join(",", upperSymbols);
        
        System.out.println("Starte Price Stream für: " + symbolsString);
        System.out.println("Erwarte Daten in den nächsten 5 Sekunden...");
        
        try {
            // WebSocket-Stream für Ticker-Events starten
            this.streamConnection = webSocketClient.onTickerEvent(symbolsString, new BinanceApiCallback<TickerEvent>() {
                @Override
                public void onResponse(TickerEvent tickerEvent) {
                    handleTickerUpdate(tickerEvent);
                }
                
                @Override
                public void onFailure(Throwable cause) {
                    String errorMsg = "WebSocket Fehler: " + cause.getMessage();
                    errorCallback.accept(errorMsg);
                    System.err.println(errorMsg);
                    cause.printStackTrace();
                }
            });
            
            isActive = true;
            
            // Timer für garantierte 1-Sekunden-Updates
            scheduler.scheduleAtFixedRate(() -> {
                if (isActive && priceUpdateCallback != null) {
                    Map<String, BigDecimal> currentSnapshot = new HashMap<>(currentPrices);
                    if (!currentSnapshot.isEmpty()) {
                        lastCallbackTime = System.currentTimeMillis();
                        priceUpdateCallback.accept(currentSnapshot);
                    } else {
                        System.out.println("Warte auf erste Preisdaten...");
                    }
                }
            }, 1, 1, TimeUnit.SECONDS);
            
            // Überwachungsthread für Verbindungsprobleme
            scheduler.scheduleAtFixedRate(() -> {
                if (isActive) {
                    long now = System.currentTimeMillis();
                    if (currentPrices.isEmpty() && (now - lastCallbackTime) > 10000) {
                        errorCallback.accept("Keine Daten seit 10 Sekunden - möglicherweise Verbindungsproblem");
                    }
                }
            }, 10, 5, TimeUnit.SECONDS);
            
            System.out.println("Stream erfolgreich gestartet!");
            
        } catch (Exception e) {
            String errorMsg = "Fehler beim Starten des Streams: " + e.getMessage();
            errorCallback.accept(errorMsg);
            System.err.println(errorMsg);
            e.printStackTrace();
        }
    }
    
    /**
     * Vereinfachte Methode zum Starten des Streams nur mit Preis-Updates
     * 
     * @param symbols Liste der Währungspaare
     * @param onPriceUpdate Callback für Preisupdates (wird jede Sekunde aufgerufen)
     */
    public void startPriceStream(List<String> symbols, Consumer<Map<String, BigDecimal>> onPriceUpdate) {
        startPriceStream(symbols, onPriceUpdate, null);
    }
    
    /**
     * Behandelt eingehende Ticker-Updates
     */
    private void handleTickerUpdate(TickerEvent tickerEvent) {
        try {
            String symbol = tickerEvent.getSymbol().toUpperCase();
            String priceString = tickerEvent.getCurrentDaysClosePrice();
            
            if (priceString != null && !priceString.isEmpty()) {
                BigDecimal price = new BigDecimal(priceString);
                long currentTime = System.currentTimeMillis();
                
                // Preis und Zeitstempel aktualisieren
                currentPrices.put(symbol, price);
                currentTickerData.put(symbol, tickerEvent);
                lastUpdateTimes.put(symbol, currentTime);
                
                // Debug-Information nur beim ersten Update jedes Symbols
                if (lastUpdateTimes.get(symbol) == currentTime) {
                    System.out.printf("✓ Erste Daten erhalten für %s: €%.2f%n", symbol, price);
                }
            }
            
        } catch (Exception e) {
            String errorMsg = "Fehler beim Verarbeiten der Ticker-Daten: " + e.getMessage();
            if (errorCallback != null) {
                errorCallback.accept(errorMsg);
            }
            System.err.println(errorMsg);
            e.printStackTrace();
        }
    }
    
    /**
     * Gibt den aktuellen Preis für ein bestimmtes Symbol zurück
     * 
     * @param symbol Das Währungspaar
     * @return Der aktuelle Preis oder null wenn nicht verfügbar
     */
    public BigDecimal getCurrentPrice(String symbol) {
        return currentPrices.get(symbol.toUpperCase());
    }
    
    /**
     * Gibt alle aktuellen Preise zurück
     * 
     * @return Map mit allen aktuellen Preisen
     */
    public Map<String, BigDecimal> getAllCurrentPrices() {
        return new HashMap<>(currentPrices);
    }
    
    /**
     * Gibt detaillierte Ticker-Informationen für ein Symbol zurück
     * 
     * @param symbol Das Währungspaar
     * @return TickerEvent mit allen Daten oder null wenn nicht verfügbar
     */
    public TickerEvent getTickerData(String symbol) {
        return currentTickerData.get(symbol.toUpperCase());
    }
    
    /**
     * Gibt alle verfügbaren Symbole zurück, für die aktuell Daten empfangen werden
     * 
     * @return Set mit allen verfügbaren Symbolen
     */
    public Set<String> getAvailableSymbols() {
        return new HashSet<>(currentPrices.keySet());
    }
    
    /**
     * Überprüft ob der Stream aktiv ist
     * 
     * @return true wenn der Stream läuft
     */
    public boolean isStreamActive() {
        return isActive && streamConnection != null;
    }
    
    /**
     * Gibt Statistiken über die erhaltenen Daten zurück
     */
    public void printStatistics() {
        System.out.println("\n=== Stream Statistiken ===");
        System.out.println("Aktive Symbole: " + currentPrices.size());
        System.out.println("Stream aktiv: " + isStreamActive());
        
        if (!currentPrices.isEmpty()) {
            System.out.println("Letzte Preise:");
            currentPrices.forEach((symbol, price) -> {
                Long lastUpdate = lastUpdateTimes.get(symbol);
                long timeSince = lastUpdate != null ? (System.currentTimeMillis() - lastUpdate) / 1000 : -1;
                System.out.printf("  %s: €%.4f (vor %ds)%n", symbol, price, timeSince);
            });
        }
        System.out.println("==========================\n");
    }
    
    /**
     * Stoppt den Price Stream und schließt die WebSocket-Verbindung
     */
    public void stopPriceStream() {
        try {
            isActive = false;
            
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                }
            }
            
            if (streamConnection != null) {
                streamConnection.close();
                streamConnection = null;
            }
            
            System.out.println("Price Stream erfolgreich gestoppt");
            
        } catch (Exception e) {
            if (errorCallback != null) {
                errorCallback.accept("Fehler beim Stoppen des Streams: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }
    
    /**
     * Hilfsmethode zum Erstellen einer Liste von Standard-Währungspaaren
     * 
     * @return Liste mit häufig verwendeten EUR-Paaren
     */
    public static List<String> getCommonEURPairs() {
        return Arrays.asList(
            "BTCEUR", "ETHEUR", "LTCEUR", "ADAEUR", "DOTEUR",
            "LINKEUR", "BCHEUR", "XLMEUR", "EOSEUR", "TRXEUR"
        );
    }
    
    /**
     * Hilfsmethode zum Erstellen einer Liste von Standard-USDT-Paaren
     * 
     * @return Liste mit häufig verwendeten USDT-Paaren  
     */
    public static List<String> getCommonUSDTPairs() {
        return Arrays.asList(
            "BTCUSDT", "ETHUSDT", "LTCUSDT", "ADAUSDT", "DOTUSDT",
            "LINKUSDT", "BCHUSDT", "XLMUSDT", "EOSUSDT", "TRXUSDT"
        );
    }
}

