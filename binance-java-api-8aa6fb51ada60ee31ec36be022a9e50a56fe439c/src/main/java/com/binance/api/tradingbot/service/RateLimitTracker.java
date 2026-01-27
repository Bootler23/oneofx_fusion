package com.binance.api.tradingbot.service;

import com.binance.api.tradingbot.constants.ApiEndpointWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verwaltet das Tracking von Binance API Rate Limits.
 * 
 * Diese Klasse überwacht die Anzahl der API-Requests und deren Weight-Verbrauch
 * innerhalb eines 1-Minuten-Fensters (Sliding Window). Binance limitiert API-Calls
 * auf 6.000 Weight-Einheiten pro Minute.
 * 
 * Das Sliding-Window-Prinzip:
 * - Es werden nur Requests der letzten 60 Sekunden gezählt
 * - Ältere Requests werden automatisch "vergessen"
 * - Dies verhindert plötzliche Blockaden am Beginn einer neuen Minute
 * 
 * Thread-Safety:
 * - Alle öffentlichen Methoden sind thread-safe
 * - Verwendet ConcurrentHashMap für parallele Zugriffe
 * - AtomicInteger/AtomicLong für atomare Operationen
 * 
 * @author Trading Bot
 * @version 1.0
 * @since 2025-12-01
 */
public class RateLimitTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitTracker.class);
    
    // ========== Singleton-Pattern (Thread-Safe) ==========
    
    /**
     * Singleton-Instanz des RateLimitTrackers.
     * Wird beim ersten Zugriff initialisiert (Lazy Initialization).
     */
    private static volatile RateLimitTracker instance;
    
    /**
     * Gibt die Singleton-Instanz zurück.
     * Thread-safe durch Double-Checked Locking.
     * 
     * @return Die einzige Instanz des RateLimitTrackers
     */
    public static RateLimitTracker getInstance() {
        // Erste Prüfung ohne Synchronisation (Performance-Optimierung)
        if (instance == null) {
            synchronized (RateLimitTracker.class) {
                // Zweite Prüfung mit Synchronisation (Thread-Safety)
                if (instance == null) {
                    instance = new RateLimitTracker();
                }
            }
        }
        return instance;
    }
    
    // ========== Konfiguration ==========
    
    /**
     * Binance API Weight-Limit pro Minute.
     * Alle API-Calls dürfen zusammen maximal 6.000 Weight-Einheiten/Minute verbrauchen.
     */
    private static final int WEIGHT_LIMIT_PER_MINUTE = ApiEndpointWeights.GLOBAL_WEIGHT_LIMIT_PER_MINUTE;
    
    /**
     * Zeitfenster für Sliding Window in Millisekunden (60 Sekunden).
     */
    private static final long WINDOW_SIZE_MS = 60_000L;
    
    /**
     * Intervall für Console-Output in Sekunden.
     */
    private static final int REPORT_INTERVAL_SECONDS = 5;
    
    /**
     * Warnschwelle in Prozent (0.0 - 1.0).
     * Bei Überschreitung wird eine Warnung ausgegeben.
     */
    private static final double WARNING_THRESHOLD = 0.80; // 80%
    
    // ========== Datenstrukturen für Request-Tracking ==========
    
    /**
     * Speichert alle Requests mit Timestamp.
     * Key: Timestamp in Millisekunden
     * Value: RequestRecord mit Details
     * 
     * ConcurrentHashMap ermöglicht thread-safe Zugriffe von mehreren Threads.
     */
    private final ConcurrentHashMap<Long, RequestRecord> requestHistory;
    
    /**
     * Zählt die Gesamtanzahl der Requests (über alle Zeit).
     * AtomicLong sorgt für thread-safe Inkrementierung.
     */
    private final AtomicLong totalRequests;
    
    /**
     * Summiert das gesamte verbrauchte Weight (über alle Zeit).
     */
    private final AtomicLong totalWeight;
    
    /**
     * Zählt die Anzahl der platzierten Orders (über alle Zeit).
     */
    private final AtomicLong totalOrders;
    
    /**
     * Speichert kumuliertes Weight pro Endpoint-Typ.
     * Wird für Top-Endpoint-Statistik verwendet.
     */
    private final ConcurrentHashMap<String, AtomicLong> endpointWeightMap;
    
    /**
     * Aktuelles Weight aus Binance Response-Header (X-MBX-USED-WEIGHT-1M).
     * Wird vom RateLimitInterceptor gesetzt.
     */
    private final AtomicInteger serverReportedWeight;
    
    /**
     * Aktueller Order-Count aus Binance Response-Header (X-MBX-ORDER-COUNT-1M).
     */
    private final AtomicInteger serverReportedOrderCount;
    
    /**
     * Scheduler für periodische Console-Ausgabe.
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * Flag ob Tracking aktiv ist.
     */
    private volatile boolean isTracking;
    
    // ========== Konstruktor (Private für Singleton) ==========
    
    /**
     * Privater Konstruktor initialisiert alle Datenstrukturen und startet
     * den periodischen Report-Scheduler.
     */
    private RateLimitTracker() {
        this.requestHistory = new ConcurrentHashMap<>();
        this.totalRequests = new AtomicLong(0);
        this.totalWeight = new AtomicLong(0);
        this.totalOrders = new AtomicLong(0);
        this.endpointWeightMap = new ConcurrentHashMap<>();
        this.serverReportedWeight = new AtomicInteger(0);
        this.serverReportedOrderCount = new AtomicInteger(0);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.isTracking = false;
        
        logger.info("RateLimitTracker initialisiert - Limit: {} Weight/Minute", WEIGHT_LIMIT_PER_MINUTE);
    }
    
    // ========== Öffentliche API ==========
    
    /**
     * Startet das Rate-Limit-Tracking und die periodische Console-Ausgabe.
     * 
     * Die Console-Ausgabe erfolgt alle 5 Sekunden und zeigt:
     * - Aktuelles Weight und Prozent-Auslastung
     * - Verbleibende Kapazität
     * - Order-Count
     * - Top-3-Endpoints nach Weight
     * - Requests/Minute-Statistik
     */
    public void startTracking() {
        if (isTracking) {
            logger.warn("Rate-Limit-Tracking läuft bereits!");
            return;
        }
        
        isTracking = true;
        
        // Scheduler für periodische Ausgabe starten
        scheduler.scheduleAtFixedRate(
            this::printRateLimitStatus,
            REPORT_INTERVAL_SECONDS, // Initial Delay
            REPORT_INTERVAL_SECONDS, // Period
            TimeUnit.SECONDS
        );
        
        logger.info("Rate-Limit-Tracking gestartet - Console-Output alle {} Sekunden", REPORT_INTERVAL_SECONDS);
    }
    
    /**
     * Stoppt das Rate-Limit-Tracking und beendet den Scheduler.
     */
    public void stopTracking() {
        if (!isTracking) {
            return;
        }
        
        isTracking = false;
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Rate-Limit-Tracking gestoppt");
    }
    
    /**
     * Registriert einen neuen API-Request im Tracking-System.
     * 
     * Diese Methode wird vom RateLimitInterceptor nach jedem API-Call aufgerufen.
     * Sie speichert den Request mit Timestamp, Endpoint und Weight für die
     * Sliding-Window-Berechnung.
     * 
     * @param endpoint Die URL des aufgerufenen Endpoints
     * @param weight Das Weight des Requests (1-80)
     * @param isOrder True wenn es eine Order-Platzierung war (zählt zum Order-Limit)
     */
    public void recordRequest(String endpoint, int weight, boolean isOrder) {
        // Aktueller Timestamp
        long timestamp = System.currentTimeMillis();
        
        // Request-Record erstellen
        RequestRecord record = new RequestRecord(timestamp, endpoint, weight, isOrder);
        
        // In History speichern
        requestHistory.put(timestamp, record);
        
        // Globale Zähler aktualisieren (atomic operations)
        totalRequests.incrementAndGet();
        totalWeight.addAndGet(weight);
        
        if (isOrder) {
            totalOrders.incrementAndGet();
        }
        
        // Endpoint-Statistik aktualisieren
        String endpointName = ApiEndpointWeights.getEndpointDescription(endpoint);
        endpointWeightMap
            .computeIfAbsent(endpointName, k -> new AtomicLong(0))
            .addAndGet(weight);
        
        // Alte Einträge aufräumen (außerhalb des 1-Minuten-Fensters)
        cleanupOldRecords(timestamp);
        
        // Warnung bei hoher Auslastung (basierend auf Server-Weight)
        int serverWeight = serverReportedWeight.get();
        if (serverWeight > 0) {
            double usagePercent = (double) serverWeight / WEIGHT_LIMIT_PER_MINUTE;
            if (usagePercent > WARNING_THRESHOLD) {
                logger.warn("⚠️ HOHE API-AUSLASTUNG: {}/{} Weight ({}%) - Endpoint: {}", 
                    serverWeight, WEIGHT_LIMIT_PER_MINUTE, 
                    String.format("%.1f", usagePercent * 100), endpointName);
            }
        }
    }
    
    /**
     * Setzt das vom Binance-Server gemeldete Weight.
     * 
     * Binance sendet in jedem Response den Header "X-MBX-USED-WEIGHT-1M",
     * der das aktuelle Weight aus Sicht des Servers angibt.
     * Dieser Wert ist autoritativ und sollte mit unserem internen Tracking
     * übereinstimmen.
     * 
     * @param weight Der vom Server gemeldete Weight-Wert
     */
    public void setServerReportedWeight(int weight) {
        serverReportedWeight.set(weight);
        
        // Hinweis: Diskrepanz zwischen Server-Weight und lokalem Weight ist normal,
        // da der Server auch interne API-Calls mitzählt (z.B. WebSocket-Keepalive)
        // und unser Tracking nur REST-Calls erfasst.
    }
    
    /**
     * Setzt den vom Binance-Server gemeldeten Order-Count.
     * 
     * @param count Der vom Server gemeldete Order-Count
     */
    public void setServerReportedOrderCount(int count) {
        serverReportedOrderCount.set(count);
    }
    
    /**
     * Berechnet das aktuell verbrauchte Weight im 1-Minuten-Fenster.
     * 
     * Sliding-Window-Prinzip:
     * - Nur Requests der letzten 60 Sekunden werden gezählt
     * - Timestamps außerhalb des Fensters werden ignoriert
     * 
     * @return Aktuelles Weight (0 - 6000)
     */
    public int getUsedWeightInWindow() {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE_MS;
        
        // Summiere Weight aller Requests im Zeitfenster
        return requestHistory.entrySet().stream()
            .filter(entry -> entry.getKey() >= windowStart) // Nur letzte 60 Sekunden
            .mapToInt(entry -> entry.getValue().weight)
            .sum();
    }
    
    /**
     * Berechnet die Anzahl der Requests im 1-Minuten-Fenster.
     * 
     * @return Anzahl der Requests in der letzten Minute
     */
    public int getRequestCountInWindow() {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE_MS;
        
        return (int) requestHistory.entrySet().stream()
            .filter(entry -> entry.getKey() >= windowStart)
            .count();
    }
    
    /**
     * Gibt die prozentuale Auslastung des Weight-Limits zurück.
     * 
     * @return Prozent-Auslastung (0.0 - 1.0+)
     */
    public double getUsagePercentage() {
        return (double) getUsedWeightInWindow() / WEIGHT_LIMIT_PER_MINUTE;
    }
    
    /**
     * Prüft ob noch genug Weight-Kapazität verfügbar ist.
     * 
     * @param requiredWeight Das benötigte Weight für den nächsten Request
     * @return True wenn genug Kapazität vorhanden ist
     */
    public boolean hasCapacity(int requiredWeight) {
        int currentWeight = getUsedWeightInWindow();
        return (currentWeight + requiredWeight) <= WEIGHT_LIMIT_PER_MINUTE;
    }
    
    /**
     * Prüft ob das Server-Weight über dem Warnschwellenwert (80%) liegt.
     * 
     * Nutzt den Server-gemeldeten Weight-Wert für genaue Auslastungsprüfung.
     * Wenn noch kein Server-Weight empfangen wurde, wird false zurückgegeben.
     * 
     * @return True wenn Server-Weight > 80% des Limits
     */
    public boolean isOverThreshold() {
        int serverWeight = serverReportedWeight.get();
        if (serverWeight <= 0) {
            return false; // Noch kein Server-Weight erhalten
        }
        return (serverWeight / (double) WEIGHT_LIMIT_PER_MINUTE) > WARNING_THRESHOLD;
    }
    
    // ========== Private Hilfsmethoden ==========
    
    /**
     * Entfernt alte Request-Records außerhalb des 1-Minuten-Fensters.
     * 
     * Diese Cleanup-Methode verhindert, dass die requestHistory-Map
     * unbegrenzt wächst. Sie wird nach jedem recordRequest() aufgerufen.
     * 
     * @param currentTimestamp Der aktuelle Timestamp in Millisekunden
     */
    private void cleanupOldRecords(long currentTimestamp) {
        long windowStart = currentTimestamp - WINDOW_SIZE_MS;
        
        // Entferne alle Einträge älter als 60 Sekunden
        requestHistory.entrySet().removeIf(entry -> entry.getKey() < windowStart);
    }
    
    /**
     * Gibt den aktuellen Rate-Limit-Status auf der Console aus.
     * 
     * Diese Methode wird alle 5 Sekunden vom Scheduler aufgerufen und
     * zeigt eine formatierte Tabelle mit:
     * - Timestamp
     * - Weight-Auslastung (absolut und prozentual)
     * - Verbleibende Kapazität
     * - Order-Count
     * - Top-3-Endpoints nach Weight (NUR letzte Minute!)
     * - Requests/Minute
     * 
     * Bei Auslastung >80% wird eine farbige Warnung ausgegeben.
     */
    private void printRateLimitStatus() {
        // Server-Weight von Binance (der echte Wert!)
        int serverWeight = serverReportedWeight.get();
        int requestCount = getRequestCountInWindow();
        
        // Berechne Orders in der letzten Minute
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE_MS;
        int ordersLastMinute = (int) requestHistory.entrySet().stream()
            .filter(entry -> entry.getKey() >= windowStart)
            .filter(entry -> entry.getValue().isOrder)
            .count();
        
        // Timestamp formatieren
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        // ========== EINZEILIGE AUSGABE (NUR SERVER-WEIGHT) ==========
        // Format: [HH:mm:ss] API-Limit: 725/1200 (60.4%) | Requests: 145 | Orders: 9/10
        
        // Nur ausgeben wenn Server-Weight verfügbar
        if (serverWeight <= 0) {
            return; // Noch kein Server-Weight erhalten
        }
        
        double usagePercent = (serverWeight * 100.0) / WEIGHT_LIMIT_PER_MINUTE;
        
        String output = String.format("[%s] API-Limit: %d/%d (%5.1f%%) | Requests: %d | Orders: %d/10",
            timestamp, serverWeight, WEIGHT_LIMIT_PER_MINUTE, usagePercent,
            requestCount, ordersLastMinute);
        
        // Ausgabe je nach Auslastung (basierend auf Server-Weight)
        if (usagePercent > WARNING_THRESHOLD * 100) {
            // WARNUNG bei hoher Auslastung (über System.err für rote Farbe)
            System.err.println("⚠️ " + output);
        } else {
            // Normale Ausgabe
            System.out.println(output);
        }
    }
    
    /**
     * Kürzt einen String auf eine maximale Länge.
     * 
     * @param str Der zu kürzende String
     * @param maxLength Maximale Länge
     * @return Gekürzter String mit "..." am Ende falls nötig
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    // ========== Innere Klasse: RequestRecord ==========
    
    /**
     * Repräsentiert einen einzelnen API-Request mit allen relevanten Details.
     * 
     * Diese Klasse ist immutable (unveränderbar) für Thread-Safety.
     */
    private static class RequestRecord {
        final long timestamp;      // Zeitpunkt des Requests in Millisekunden
        final String endpoint;     // URL des Endpoints
        final int weight;          // Weight-Wert des Requests
        final boolean isOrder;     // True wenn es eine Order-Platzierung war
        
        RequestRecord(long timestamp, String endpoint, int weight, boolean isOrder) {
            this.timestamp = timestamp;
            this.endpoint = endpoint;
            this.weight = weight;
            this.isOrder = isOrder;
        }
    }
}
