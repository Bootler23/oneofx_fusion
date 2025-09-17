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

/**
 * Ultra-schnelle Stream-Klasse mit garantierten 1-Sekunden-Updates
 */
public class UltraFastStream {
    
    private final BinanceApiWebSocketClient webSocketClient;
    private final Map<String, BigDecimal> prices;
    private final Map<String, Long> lastUpdates;
    private ScheduledExecutorService timer;
    private Closeable connection;
    private boolean active = false;
    
    public UltraFastStream() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
        this.webSocketClient = factory.newWebSocketClient();
        this.prices = new ConcurrentHashMap<>();
        this.lastUpdates = new ConcurrentHashMap<>();
        this.timer = Executors.newSingleThreadScheduledExecutor();
    }
    
    /**
     * Startet Stream mit 1-Sekunden-Updates
     */
    public void start(List<String> symbols, Runnable onUpdate) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbole dürfen nicht leer sein");
        }
        
        // Symbole vorbereiten
        String symbolString = String.join(",", symbols).toUpperCase();
        System.out.println("🚀 Starte Ultra-Fast Stream für: " + symbolString);
        
        try {
            // WebSocket starten
            this.connection = webSocketClient.onTickerEvent(symbolString, 
                new BinanceApiCallback<TickerEvent>() {
                    @Override
                    public void onResponse(TickerEvent ticker) {
                        // Preis sofort speichern
                        String symbol = ticker.getSymbol();
                        String priceStr = ticker.getCurrentDaysClosePrice();
                        
                        if (priceStr != null) {
                            try {
                                BigDecimal price = new BigDecimal(priceStr);
                                prices.put(symbol, price);
                                lastUpdates.put(symbol, System.currentTimeMillis());
                                
                                // Erste Daten-Benachrichtigung
                                if (prices.size() == 1) {
                                    System.out.println("✅ Erste Daten empfangen!");
                                }
                            } catch (Exception e) {
                                System.err.println("Fehler beim Parsen von " + symbol + ": " + e.getMessage());
                            }
                        }
                    }
                    
                    @Override
                    public void onFailure(Throwable cause) {
                        System.err.println("❌ WebSocket Fehler: " + cause.getMessage());
                        cause.printStackTrace();
                    }
                });
            
            active = true;
            
            // Timer für exakte 1-Sekunden-Updates
            timer.scheduleAtFixedRate(() -> {
                if (active && onUpdate != null) {
                    onUpdate.run();
                }
            }, 1, 1, TimeUnit.SECONDS);
            
            System.out.println("✅ Stream aktiv - Updates alle 1 Sekunde");
            
        } catch (Exception e) {
            System.err.println("❌ Fehler beim Starten: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gibt alle aktuellen Preise zurück
     */
    public Map<String, BigDecimal> getPrices() {
        return new HashMap<>(prices);
    }
    
    /**
     * Gibt spezifischen Preis zurück
     */
    public BigDecimal getPrice(String symbol) {
        return prices.get(symbol.toUpperCase());
    }
    
    /**
     * Zeigt Status-Informationen
     */
    public void showStatus() {
        System.out.println("\n📊 === STATUS ===");
        System.out.println("Aktiv: " + active);
        System.out.println("Symbole: " + prices.size());
        
        if (!prices.isEmpty()) {
            System.out.println("Preise:");
            prices.forEach((symbol, price) -> {
                Long lastUpdate = lastUpdates.get(symbol);
                long secondsAgo = lastUpdate != null ? 
                    (System.currentTimeMillis() - lastUpdate) / 1000 : -1;
                System.out.printf("  %s: €%.4f (vor %ds)%n", symbol, price, secondsAgo);
            });
        } else {
            System.out.println("Keine Preise verfügbar");
        }
        System.out.println("================\n");
    }
    
    /**
     * Stoppt den Stream
     */
    public void stop() {
        try {
            active = false;
            
            if (timer != null) {
                timer.shutdown();
                timer.awaitTermination(1, TimeUnit.SECONDS);
            }
            
            if (connection != null) {
                connection.close();
            }
            
            System.out.println("🛑 Stream gestoppt");
            
        } catch (Exception e) {
            System.err.println("Fehler beim Stoppen: " + e.getMessage());
        }
    }
    
    /**
     * Überprüft ob Daten empfangen werden
     */
    public boolean hasData() {
        return !prices.isEmpty();
    }
}
