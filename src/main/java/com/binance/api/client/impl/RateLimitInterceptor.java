package com.binance.api.client.impl;

import com.binance.api.tradingbot.constants.ApiEndpointWeights;
import com.binance.api.tradingbot.service.RateLimitTracker;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * OkHttp-Interceptor zum Tracking von Binance API Rate Limits.
 * 
 * Dieser Interceptor wird in die HTTP-Request-Chain eingebunden und:
 * 1. Extrahiert die Endpoint-URL aus jedem Request
 * 2. Berechnet das Weight basierend auf dem Endpoint-Typ
 * 3. Liest Response-Header von Binance (X-MBX-USED-WEIGHT-1M, etc.)
 * 4. Übergibt alle Informationen an den RateLimitTracker
 * 
 * Ablauf eines Requests:
 * ┌─────────────┐
 * │   Client    │  (z.B. client.getAccount())
 * └──────┬──────┘
 *        │
 *        ▼
 * ┌─────────────────────┐
 * │ RateLimitInterceptor│  ◄── Dieser Interceptor
 * └──────┬──────────────┘
 *        │  1. Extrahiere URL
 *        │  2. Berechne Weight
 *        │
 *        ▼
 * ┌────────────────────────┐
 * │ AuthenticationInterceptor│  (API-Key & Signature)
 * └──────┬─────────────────┘
 *        │
 *        ▼
 * ┌─────────────┐
 * │ HTTP-Request│ ──────► Binance Server
 * └──────┬──────┘
 *        │
 *        ▼
 * ┌─────────────┐
 * │HTTP-Response│ ◄────── Binance Server
 * └──────┬──────┘         (mit X-MBX-USED-WEIGHT-1M Header)
 *        │
 *        ▼
 * ┌─────────────────────┐
 * │ RateLimitInterceptor│  3. Lese Response-Header
 * └──────┬──────────────┘  4. Aktualisiere Tracker
 *        │
 *        ▼
 * ┌─────────────┐
 * │   Client    │  (Response-Objekt)
 * └─────────────┘
 * 
 * Thread-Safety:
 * - Dieser Interceptor wird von mehreren Threads gleichzeitig aufgerufen
 * - Daher keine Instanzvariablen für Request-spezifische Daten verwenden
 * - RateLimitTracker ist intern thread-safe
 * 
 * @author Trading Bot
 * @version 1.0
 * @since 2025-12-01
 */
public class RateLimitInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    
    /**
     * Singleton-Instanz des RateLimitTrackers.
     * Wird beim ersten Zugriff initialisiert.
     */
    private final RateLimitTracker rateLimitTracker;
    
    /**
     * Konstruktor initialisiert den RateLimitTracker.
     */
    public RateLimitInterceptor() {
        this.rateLimitTracker = RateLimitTracker.getInstance();
        // Initialisierung abgeschlossen - Tracking ist aktiv
    }
    
    /**
     * Interceptor-Hauptmethode - wird für jeden HTTP-Request aufgerufen.
     * 
     * Diese Methode läuft SYNCHRON und blockiert den Request-Thread,
     * daher sollte sie schnell durchlaufen.
     * 
     * Ablauf:
     * 1. Request-Details extrahieren (URL, HTTP-Methode)
     * 2. Weight basierend auf Endpoint berechnen
     * 3. Request an nächsten Interceptor weitergeben
     * 4. Response-Header auslesen (X-MBX-USED-WEIGHT-1M, etc.)
     * 5. RateLimitTracker aktualisieren
     * 6. Response zurückgeben
     * 
     * @param chain Die Interceptor-Chain (enthält Request und folgende Interceptors)
     * @return Die HTTP-Response vom Server
     * @throws IOException Bei Netzwerkfehlern
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        // ========== 1. Request-Details extrahieren ==========
        
        Request request = chain.request();
        String url = request.url().toString();
        String httpMethod = request.method(); // GET, POST, DELETE, etc.
        
        // Vollständige Endpoint-URL mit Parametern für genaue Weight-Berechnung
        String endpoint = url;
        
        // ========== 2. Weight berechnen ==========
        
        // ApiEndpointWeights analysiert die URL und gibt das korrekte Weight zurück
        // Berücksichtigt auch Spezialfälle wie:
        // - /api/v3/ticker/price ohne Symbol = 4 Weight
        // - /api/v3/ticker/price mit Symbol = 2 Weight
        int weight = ApiEndpointWeights.getWeight(endpoint);
        
        // ========== 3. Prüfen ob es sich um eine Order-Platzierung handelt ==========
        
        // Orders zählen zusätzlich zum ORDER-Limit (10/Sekunde)
        // Nur POST-Requests auf /api/v3/order sind Order-Platzierungen
        boolean isOrder = httpMethod.equals("POST") && endpoint.contains("/api/v3/order");
        
        // Debug-Logging deaktiviert um Console-Spam zu vermeiden
        // Bei Bedarf kann dies für Debugging reaktiviert werden
        
        // ========== 4. Request an nächsten Interceptor weitergeben ==========
        
        // chain.proceed() führt den eigentlichen HTTP-Request aus
        // und gibt die Response zurück
        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            // Bei Netzwerkfehlern: Request trotzdem tracken (für Statistik)
            rateLimitTracker.recordRequest(endpoint, weight, isOrder);
            throw e; // Exception weiterwerfen
        }
        
        // ========== 5. Response-Header auslesen ==========
        
        // Binance sendet in jedem Response wichtige Rate-Limit-Informationen:
        //
        // X-MBX-USED-WEIGHT-1M: Aktuell verbrauchtes Weight (1-Minuten-Fenster)
        //   Beispiel: "1234" bedeutet 1234 von 6000 Weight verbraucht
        //
        // X-MBX-ORDER-COUNT-1M: Anzahl platzierter Orders (1-Minuten-Fenster)
        //   Beispiel: "5" bedeutet 5 Orders in der letzten Minute
        //
        // Retry-After: Wartezeit in Sekunden bei Rate-Limit-Überschreitung (nur bei Error 429)
        //   Beispiel: "60" bedeutet 60 Sekunden warten
        
        String usedWeightHeader = response.header("X-MBX-USED-WEIGHT-1M");
        String orderCountHeader = response.header("X-MBX-ORDER-COUNT-1M");
        String retryAfterHeader = response.header("Retry-After");
        
        // ========== 6. RateLimitTracker aktualisieren ==========
        
        // Request im Tracker registrieren
        rateLimitTracker.recordRequest(endpoint, weight, isOrder);
        
        // Server-gemeldetes Weight übernehmen (falls vorhanden)
        if (usedWeightHeader != null && !usedWeightHeader.isEmpty()) {
            try {
                int serverWeight = Integer.parseInt(usedWeightHeader);
                rateLimitTracker.setServerReportedWeight(serverWeight);
            } catch (NumberFormatException e) {
                logger.warn("Ungültiger X-MBX-USED-WEIGHT-1M Header: {}", usedWeightHeader);
            }
        }
        
        // Server-gemeldeten Order-Count übernehmen (falls vorhanden)
        if (orderCountHeader != null && !orderCountHeader.isEmpty()) {
            try {
                int orderCount = Integer.parseInt(orderCountHeader);
                rateLimitTracker.setServerReportedOrderCount(orderCount);
            } catch (NumberFormatException e) {
                logger.warn("Ungültiger X-MBX-ORDER-COUNT-1M Header: {}", orderCountHeader);
            }
        }
        
        // ========== 7. Rate-Limit-Fehler (HTTP 429) behandeln ==========
        
        if (response.code() == 429) {
            // HTTP 429 = Too Many Requests (Rate Limit überschritten!)
            
            // Warnung ausgeben
            logger.error("⛔ RATE-LIMIT ÜBERSCHRITTEN! HTTP 429 von Binance erhalten!");
            logger.error("   Endpoint: {}", endpoint);
            logger.error("   Aktuelles Weight: {}/{}", 
                rateLimitTracker.getUsedWeightInWindow(), 
                ApiEndpointWeights.GLOBAL_WEIGHT_LIMIT_PER_MINUTE);
            
            // Retry-After-Header auswerten
            if (retryAfterHeader != null && !retryAfterHeader.isEmpty()) {
                try {
                    int retryAfterSeconds = Integer.parseInt(retryAfterHeader);
                    logger.error("   Retry-After: {} Sekunden", retryAfterSeconds);
                    logger.error("   ⏳ Bitte warten Sie {} Sekunden bevor Sie weitere Requests senden!", 
                        retryAfterSeconds);
                } catch (NumberFormatException e) {
                    logger.warn("Ungültiger Retry-After Header: {}", retryAfterHeader);
                }
            }
            
            // Hinweis auf mögliche Ursachen
            logger.error("   💡 Mögliche Ursachen:");
            logger.error("      - Zu viele API-Calls in kurzer Zeit");
            logger.error("      - Schwere Endpoints (getAccount, myTrades) zu oft aufgerufen");
            logger.error("      - Mehrere Trading-Bot-Instanzen mit demselben API-Key");
            logger.error("   💡 Lösungsvorschläge:");
            logger.error("      - Caching für Account-Daten verwenden");
            logger.error("      - WebSocket statt REST für Live-Preise nutzen");
            logger.error("      - Sleep-Intervalle zwischen Requests erhöhen");
        }
        
        // ========== 8. Response zurückgeben ==========
        
        return response;
    }
}
