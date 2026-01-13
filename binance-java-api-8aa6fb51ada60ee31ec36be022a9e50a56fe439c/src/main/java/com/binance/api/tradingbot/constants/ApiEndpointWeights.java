package com.binance.api.tradingbot.constants;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Definiert die API-Weight-Werte für alle Binance REST API Endpoints.
 * 
 * Diese Klasse enthält die offiziellen Weight-Werte gemäß Binance API-Dokumentation (Stand 2025).
 * Das globale Limit beträgt 6.000 Weight-Einheiten pro Minute für alle Requests.
 * 
 * Weight-Einheiten bestimmen, wie "schwer" ein API-Request für die Binance-Server ist.
 * Einfache Abfragen haben niedrige Weights (1-4), komplexe Abfragen höhere Weights (10-80).
 * 
 * @see <a href="https://developers.binance.com/docs/binance-spot-api-docs/rest-api/limits">Binance API Limits</a>
 */
public final class ApiEndpointWeights {

    // ========== Binance API Rate Limits (Stand: Dezember 2025) ==========
    
    /**
     * Globales Request-Weight-Limit pro Minute.
     * Alle REST API Calls teilen sich dieses Limit.
     */
    public static final int GLOBAL_WEIGHT_LIMIT_PER_MINUTE = 6000;
    
    /**
     * Order-Limit pro Sekunde (gilt nur für Order-Platzierungen).
     * Unabhängig vom Weight-Limit.
     */
    public static final int ORDER_LIMIT_PER_SECOND = 10;
    
    /**
     * Order-Limit pro Minute (gilt nur für Order-Platzierungen).
     */
    public static final int ORDER_LIMIT_PER_MINUTE = 600;

    // ========== Endpoint Weight Mapping ==========
    
    /**
     * Map mit allen bekannten Endpoint-Patterns und ihren Weight-Werten.
     * Key: Regex-Pattern des Endpoints
     * Value: Weight-Wert
     */
    private static final Map<Pattern, Integer> ENDPOINT_WEIGHTS = new HashMap<>();
    
    static {
        // ========== Market Data Endpoints (öffentlich, kein API-Key nötig) ==========
        
        // GET /api/v3/ping - Weight: 1
        // Testet die Konnektivität zur REST API
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/ping.*"), 1);
        
        // GET /api/v3/time - Weight: 1
        // Testet die Konnektivität und gibt Server-Zeit zurück
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/time.*"), 1);
        
        // GET /api/v3/exchangeInfo - Weight: 20
        // Gibt Informationen über Exchange-Regeln und Symbol-Informationen zurück
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/exchangeInfo.*"), 20);
        
        // GET /api/v3/depth - Weight: variiert (5, 10, 50, 100, 500, 1000, 5000)
        // OrderBook-Tiefe, hier: Default 10 für typische Limits
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/depth.*"), 10);
        
        // GET /api/v3/trades - Weight: 2
        // Holt die letzten Trades für ein Symbol
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/trades.*"), 2);
        
        // GET /api/v3/historicalTrades - Weight: 10
        // Holt ältere Trades (benötigt API-Key)
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/historicalTrades.*"), 10);
        
        // GET /api/v3/aggTrades - Weight: 2
        // Aggregierte Trades
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/aggTrades.*"), 2);
        
        // GET /api/v3/klines - Weight: 2
        // Candlestick-Daten (OHLCV)
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/klines.*"), 2);
        
        // GET /api/v3/avgPrice - Weight: 2
        // Durchschnittspreis über ein Zeitfenster
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/avgPrice.*"), 2);
        
        // GET /api/v3/ticker/24hr - Weight: 2 (ein Symbol) / 80 (alle Symbole)
        // 24-Stunden-Ticker-Statistiken
        // Wichtig: Ohne Symbol-Parameter = 80 Weight!
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/ticker/24hr.*"), 2);
        
        // GET /api/v3/ticker/price - Weight: 2 (ein Symbol) / 4 (alle Symbole)
        // Aktueller Preis für Symbol(e)
        // Wichtig: Ohne Symbol-Parameter = 4 Weight!
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/ticker/price.*"), 2);
        
        // GET /api/v3/ticker/bookTicker - Weight: 2 (ein Symbol) / 4 (alle Symbole)
        // Bester Bid/Ask-Preis
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/ticker/bookTicker.*"), 2);
        
        // ========== Trading Endpoints (benötigen API-Key + Signature) ==========
        
        // POST /api/v3/order - Weight: 1
        // Neue Order platzieren (LIMIT, MARKET, etc.)
        // Zusätzlich: Zählt zum ORDER-Limit (10/Sekunde)
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/order.*"), 1);
        
        // GET /api/v3/order - Weight: 4
        // Status einer einzelnen Order abfragen
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/order\\?.*"), 4);
        
        // DELETE /api/v3/order - Weight: 1
        // Order stornieren
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/order.*"), 1);
        
        // GET /api/v3/openOrders - Weight: 6 (ein Symbol) / 80 (alle Symbole)
        // Alle offenen Orders abfragen
        // Wichtig: Ohne Symbol-Parameter = 80 Weight!
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/openOrders.*"), 6);
        
        // GET /api/v3/allOrders - Weight: 20
        // Alle Orders (inkl. historische) für ein Symbol
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/allOrders.*"), 20);
        
        // POST /api/v3/order/oco - Weight: 1
        // OCO (One-Cancels-Other) Order platzieren
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/order/oco.*"), 1);
        
        // DELETE /api/v3/orderList - Weight: 1
        // OCO Order stornieren
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/orderList.*"), 1);
        
        // GET /api/v3/orderList - Weight: 4
        // OCO Order-Status abfragen
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/orderList\\?.*"), 4);
        
        // GET /api/v3/allOrderList - Weight: 20
        // Alle OCO Orders abfragen
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/allOrderList.*"), 20);
        
        // ========== Account Endpoints (benötigen API-Key + Signature) ==========
        
        // GET /api/v3/account - Weight: 20
        // Account-Informationen inkl. Balances
        // WICHTIG: Dieser Endpoint ist "schwer" - sparsam verwenden oder cachen!
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/account.*"), 20);
        
        // GET /api/v3/myTrades - Weight: 20
        // Trade-Historie für ein Symbol
        // WICHTIG: Ebenfalls "schwer" - sollte gecacht werden
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/myTrades.*"), 20);
        
        // GET /api/v3/rateLimit/order - Weight: 40
        // Aktueller Order-Count und Limits
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/rateLimit/order.*"), 40);
        
        // ========== Margin Trading Endpoints ==========
        
        // POST /sapi/v1/margin/order - Weight: 6
        // Margin-Order platzieren
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/sapi/v1/margin/order.*"), 6);
        
        // DELETE /sapi/v1/margin/order - Weight: 1
        // Margin-Order stornieren
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/sapi/v1/margin/order.*"), 1);
        
        // GET /sapi/v1/margin/account - Weight: 10
        // Margin-Account-Informationen
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/sapi/v1/margin/account.*"), 10);
        
        // ========== User Data Stream Endpoints ==========
        
        // POST /api/v3/userDataStream - Weight: 2
        // User Data Stream starten
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/userDataStream.*"), 2);
        
        // PUT /api/v3/userDataStream - Weight: 2
        // User Data Stream Keep-Alive
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/userDataStream.*"), 2);
        
        // DELETE /api/v3/userDataStream - Weight: 2
        // User Data Stream schließen
        ENDPOINT_WEIGHTS.put(Pattern.compile(".*/api/v3/userDataStream.*"), 2);
    }
    
    /**
     * Ermittelt das Weight für einen bestimmten API-Endpoint.
     * 
     * Diese Methode analysiert die URL des API-Calls und gibt den entsprechenden
     * Weight-Wert zurück. Falls der Endpoint nicht bekannt ist, wird ein
     * Default-Weight von 1 zurückgegeben.
     * 
     * @param endpoint Die vollständige URL des API-Endpoints (z.B. "https://api.binance.com/api/v3/order?symbol=LTCEUR")
     * @return Der Weight-Wert für diesen Endpoint (1-80)
     */
    public static int getWeight(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return 1; // Default-Weight für unbekannte Endpoints
        }
        
        // Durch alle bekannten Endpoint-Patterns iterieren
        for (Map.Entry<Pattern, Integer> entry : ENDPOINT_WEIGHTS.entrySet()) {
            Pattern pattern = entry.getKey();
            Integer weight = entry.getValue();
            
            // Prüfen ob das Pattern auf den Endpoint passt
            if (pattern.matcher(endpoint).matches()) {
                
                // Spezialfall: Endpoints mit variablem Weight basierend auf Parametern
                if (endpoint.contains("/api/v3/ticker/price")) {
                    // Wenn kein "symbol=" Parameter: alle Preise = höheres Weight
                    if (!endpoint.contains("symbol=")) {
                        return 4; // Alle Symbole
                    }
                    return 2; // Ein Symbol
                }
                
                if (endpoint.contains("/api/v3/ticker/24hr")) {
                    if (!endpoint.contains("symbol=")) {
                        return 80; // Alle Symbole
                    }
                    return 2; // Ein Symbol
                }
                
                if (endpoint.contains("/api/v3/ticker/bookTicker")) {
                    if (!endpoint.contains("symbol=")) {
                        return 4; // Alle Symbole
                    }
                    return 2; // Ein Symbol
                }
                
                if (endpoint.contains("/api/v3/openOrders")) {
                    if (!endpoint.contains("symbol=")) {
                        return 80; // Alle Symbole - sehr schwer!
                    }
                    return 6; // Ein Symbol
                }
                
                // Standard-Weight zurückgeben
                return weight;
            }
        }
        
        // Fallback: Unbekannter Endpoint, konservativ 1 Weight annehmen
        return 1;
    }
    
    /**
     * Gibt eine lesbare Beschreibung für einen Endpoint zurück.
     * Hilfreich für Logging und Debugging.
     * 
     * @param endpoint Die vollständige URL des API-Endpoints
     * @return Eine kurze Beschreibung des Endpoints (z.B. "GET /api/v3/order (Order-Status)")
     */
    public static String getEndpointDescription(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "Unknown Endpoint";
        }
        
        // Extrahiere nur den Pfad ohne Domain und Parameter
        String path = endpoint;
        if (endpoint.contains("://")) {
            path = endpoint.substring(endpoint.indexOf("://") + 3);
            if (path.contains("/")) {
                path = "/" + path.substring(path.indexOf("/") + 1);
            }
        }
        
        // Kürze Parameter für Lesbarkeit
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        
        // Zuordnung zu lesbaren Namen
        if (path.contains("/api/v3/ticker/price")) return "Price Ticker";
        if (path.contains("/api/v3/account")) return "Account Info";
        if (path.contains("/api/v3/myTrades")) return "Trade History";
        if (path.contains("/api/v3/order") && endpoint.contains("DELETE")) return "Cancel Order";
        if (path.contains("/api/v3/order") && endpoint.contains("POST")) return "Place Order";
        if (path.contains("/api/v3/order")) return "Order Status";
        if (path.contains("/api/v3/openOrders")) return "Open Orders";
        if (path.contains("/api/v3/exchangeInfo")) return "Exchange Info";
        if (path.contains("/api/v3/klines")) return "Candlestick Data";
        
        return path;
    }
    
    // Verhindern von Instanziierung (Utility-Klasse)
    private ApiEndpointWeights() {
        throw new AssertionError("Cannot instantiate ApiEndpointWeights - this is a utility class");
    }
}
