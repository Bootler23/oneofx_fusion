package com.binance.api.tradingbot.service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.tradingbot.Settings.bnb;
import com.binance.api.tradingbot.domain.VolumeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class VolumeService {

    private static final Logger logger = LoggerFactory.getLogger(VolumeService.class);

    // ========== Singleton-Pattern (Thread-Safe) ==========

    /**
     * Singleton-Instanz des VolumeService.
     * Wird beim ersten Zugriff initialisiert (Lazy Initialization).
     */
    private static volatile VolumeService instance;

    /**
     * Gibt die Singleton-Instanz zurück.
     * Thread-safe durch Double-Checked Locking.
     * 
     * @return Die einzige Instanz des VolumeService
     */
    public static VolumeService getInstance() {
        // Erste Prüfung ohne Synchronisation (Performance-Optimierung)
        if (instance == null) {
            synchronized (VolumeService.class) {
                // Zweite Prüfung mit Synchronisation (Thread-Safety)
                if (instance == null) {
                    instance = new VolumeService();
                }
            }
        }
        return instance;
    }

    // ========== Konfiguration ==========

    /**
     * Maximale Anzahl von Retry-Versuchen bei API-Fehlern.
     */
    private static final int MAX_RETRIES = 10;

    /**
     * Initial-Wartezeit bei Retry in Millisekunden.
     */
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    /**
     * Exponential-Backoff-Faktor für Retry-Delays.
     */
    private static final double BACKOFF_MULTIPLIER = 1.5;

    /**
     * Maximale Wartezeit zwischen Retries in Millisekunden.
     */
    private static final long MAX_RETRY_DELAY_MS = 30000;

    // ========== Instanzvariablen ==========

    /**
     * Binance API REST Client für API-Calls.
     */
    private final BinanceApiRestClient client;

    // ========== Konstruktor (Private - Singleton) ==========

    /**
     * Privater Konstruktor für Singleton-Pattern.
     * Initialisiert den BinanceApiRestClient über die Factory.
     */
    private VolumeService() {
        this.client = bnb.getClient();
        logger.info("VolumeService initialisiert");
    }

    // ========== Einzelabfrage-Methoden ==========

    /**
     * Ruft die 24h-Volumen-Daten für ein einzelnes Währungspaar ab.
     * 
     * Diese Methode verwendet Retry-Logic mit Exponential Backoff bei Fehlern.
     * API-Weight: 2
     * 
     * @param symbol Währungspaar (z.B. "LTCEUR", "BTCEUR")
     * @return VolumeData-Objekt mit allen relevanten Daten, oder null bei Fehler
     * @throws IllegalArgumentException wenn symbol null oder leer ist
     */
    public VolumeData get24HrVolume(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol darf nicht null oder leer sein");
        }

        // logger.debug("Rufe 24h-Volumen für {} ab", symbol);

        int retryCount = 0;
        long retryDelay = INITIAL_RETRY_DELAY_MS;

        while (retryCount < MAX_RETRIES) {
            try {
                // API-Call zu Binance
                TickerStatistics stats = client.get24HrPriceStatistics(symbol);

                // Konvertierung zu VolumeData
                VolumeData volumeData = mapToVolumeData(stats);

                // logger.debug("24h-Volumen für {} erfolgreich abgerufen: {} EUR", symbol, volumeData.getVolumeQuote());

                return volumeData;

            } catch (BinanceApiException e) {
                // SocketTimeout → Retry mit Backoff
                if (e.getCause() instanceof SocketTimeoutException && retryCount < MAX_RETRIES - 1) {
                    retryCount++;
                    logger.warn("Timeout bei 24h-Volumen-Abfrage für {}, Retry {}/{} nach {}ms", 
                               symbol, retryCount, MAX_RETRIES, retryDelay);
                    
                    sleep(retryDelay);
                    
                    // Exponential Backoff
                    retryDelay = Math.min((long) (retryDelay * BACKOFF_MULTIPLIER), MAX_RETRY_DELAY_MS);
                    
                } else {
                    // Andere Fehler oder maximale Retries erreicht
                    logger.error("Fehler beim Abrufen von 24h-Volumen für {}: {} (Code: {})", 
                                symbol, e.getMessage(), 
                                e.getError() != null ? e.getError().getCode() : "N/A");
                    return null;
                }
                
            } catch (Exception e) {
                logger.error("Unerwarteter Fehler beim Abrufen von 24h-Volumen für {}: {}", 
                            symbol, e.getMessage(), e);
                return null;
            }
        }

        logger.error("Maximale Anzahl von Retries ({}) für 24h-Volumen-Abfrage von {} erreicht", 
                    MAX_RETRIES, symbol);
        return null;
    }

    /**
     * Prüft, ob ein Währungspaar ein Mindestvolumen erreicht.
     * 
     * Diese Methode ist optimiert für schnelle Boolean-Checks ohne
     * Rückgabe der vollständigen VolumeData.
     * 
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @param minVolume Mindestvolumen in Quote-Währung (z.B. EUR)
     * @return true wenn Volumen >= minVolume, false sonst oder bei Fehler
     */
    public boolean hasMinimumVolume(String symbol, BigDecimal minVolume) {
        if (symbol == null || minVolume == null) {
            return false;
        }

        VolumeData volumeData = get24HrVolume(symbol);
        if (volumeData == null || !volumeData.isValid()) {
            return false;
        }

        boolean hasMinVolume = volumeData.hasMinimumVolume(minVolume);
        
        // logger.debug("{} Mindestvolumen-Check: {} EUR {} {} EUR", 
        //             symbol, 
        //             volumeData.getVolumeQuote().setScale(0, RoundingMode.HALF_UP),
        //             hasMinVolume ? ">=" : "<",
        //             minVolume);

        return hasMinVolume;
    }

    // ========== Bulk-Abfrage-Methoden ==========

    /**
     * Ruft alle Währungspaare mit hohem Volumen ab.
     * 
     * Diese Methode filtert aus allen verfügbaren Währungspaaren diejenigen heraus,
     * die ein bestimmtes Mindestvolumen in der Quote-Währung (z.B. EUR) überschreiten.
     * 
     * API-Weight: 80 (!)
     * Hinweis: Diese Methode sollte sparsam verwendet werden wegen des hohen API-Weights.
     * 
     * @param quoteAsset Quote-Währung zum Filtern (z.B. "EUR", "USDT")
     * @param minVolumeQuote Mindestvolumen in Quote-Währung
     * @return Liste von Währungspaaren (Symbolen) die das Mindestvolumen überschreiten
     * @throws IllegalArgumentException wenn Parameter ungültig sind
     */
    public List<String> getHighVolumeSymbols(String quoteAsset, BigDecimal minVolumeQuote) {
        if (quoteAsset == null || quoteAsset.trim().isEmpty()) {
            throw new IllegalArgumentException("Quote-Asset darf nicht null oder leer sein");
        }
        if (minVolumeQuote == null || minVolumeQuote.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Mindestvolumen muss >= 0 sein");
        }

        logger.info("Suche nach Währungspaaren mit >{} {} Volumen", minVolumeQuote, quoteAsset);

        List<String> highVolumeSymbols = new ArrayList<>();

        try {
            // Alle 24h-Statistiken abrufen (API-Weight: 80!)
            List<TickerStatistics> allStats = client.getAll24HrPriceStatistics();
            
            logger.debug("Alle 24h-Statistiken abgerufen: {} Symbole", allStats.size());

            // Filtern nach Quote-Asset und Mindestvolumen
            for (TickerStatistics stats : allStats) {
                String symbol = stats.getSymbol();
                
                // Nur Paare mit gewünschter Quote-Währung
                if (!symbol.endsWith(quoteAsset)) {
                    continue;
                }

                try {
                    // Volumen berechnen
                    BigDecimal volumeBase = new BigDecimal(stats.getVolume());
                    BigDecimal lastPrice = new BigDecimal(stats.getLastPrice());
                    BigDecimal volumeQuote = volumeBase.multiply(lastPrice);

                    // Volumen-Check
                    if (volumeQuote.compareTo(minVolumeQuote) >= 0) {
                        highVolumeSymbols.add(symbol);
                        logger.debug("  ✓ {} - Volumen: {} {}", symbol, volumeQuote, quoteAsset);
                    }

                } catch (NumberFormatException e) {
                    logger.warn("Fehler beim Parsen von Volumen/Preis für {}: {}", symbol, e.getMessage());
                }
            }

            logger.info("Gefundene High-Volume-Symbole: {} von {} {}-Paaren", 
                       highVolumeSymbols.size(),
                       allStats.stream().filter(s -> s.getSymbol().endsWith(quoteAsset)).count(),
                       quoteAsset);

        } catch (BinanceApiException e) {
            logger.error("Fehler beim Abrufen aller 24h-Statistiken: {} (Code: {})", 
                        e.getMessage(), 
                        e.getError() != null ? e.getError().getCode() : "N/A");
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler bei Bulk-Volumen-Abfrage: {}", e.getMessage(), e);
        }

        return highVolumeSymbols;
    }

    /**
     * Filtert eine Liste von Symbolen nach Mindestvolumen.
     * 
     * Im Gegensatz zu getHighVolumeSymbols() prüft diese Methode nur eine
     * vordefinierte Liste von Symbolen anstatt alle verfügbaren Symbole.
     * Dies ist effizienter wenn du bereits eine Auswahl hast.
     * 
     * API-Weight: 2 pro Symbol
     * 
     * @param symbols Liste von Währungspaaren zum Prüfen
     * @param minVolumeQuote Mindestvolumen in Quote-Währung
     * @return Gefilterte Liste von Symbolen mit ausreichendem Volumen
     */
    public List<String> filterByVolume(List<String> symbols, BigDecimal minVolumeQuote) {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }
        if (minVolumeQuote == null || minVolumeQuote.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Mindestvolumen muss >= 0 sein");
        }

        logger.info("Filtere {} Symbole nach Mindestvolumen {} EUR", symbols.size(), minVolumeQuote);

        List<String> filteredSymbols = new ArrayList<>();

        for (String symbol : symbols) {
            if (hasMinimumVolume(symbol, minVolumeQuote)) {
                filteredSymbols.add(symbol);
            }
        }

        logger.info("Nach Volumen-Filter: {} von {} Symbolen übrig", 
                   filteredSymbols.size(), symbols.size());

        return filteredSymbols;
    }

    // ========== Ranking-Methoden ==========

    /**
     * Erstellt ein Ranking von Währungspaaren nach 24h-Volumen (absteigend).
     * 
     * Diese Methode ruft für alle angegebenen Symbole das Volumen ab
     * und sortiert sie nach Volumen in Quote-Währung (höchstes zuerst).
     * 
     * API-Weight: 2 pro Symbol
     * 
     * @param symbols Liste von Währungspaaren zum Ranken
     * @return Sortierte Liste von VolumeData-Objekten (höchstes Volumen zuerst)
     */
    public List<VolumeData> getVolumeRanking(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }

        logger.info("Erstelle Volumen-Ranking für {} Symbole", symbols.size());

        List<VolumeData> volumeDataList = new ArrayList<>();

        // Alle Volume-Daten sammeln
        for (String symbol : symbols) {
            VolumeData volumeData = get24HrVolume(symbol);
            if (volumeData != null && volumeData.isValid()) {
                volumeDataList.add(volumeData);
            }
        }

        // Nach Volumen sortieren (absteigend)
        volumeDataList.sort(Comparator.comparing(VolumeData::getVolumeQuote).reversed());

        logger.info("Volumen-Ranking erstellt: {} gültige Einträge", volumeDataList.size());

        // Log Top 5
        if (logger.isInfoEnabled() && !volumeDataList.isEmpty()) {
            logger.info("Top 5 nach Volumen:");
            volumeDataList.stream()
                    .limit(5)
                    .forEach(vd -> logger.info("  {} - {} EUR", 
                            vd.getSymbol(), vd.getVolumeQuote()));
        }

        return volumeDataList;
    }

    /**
     * Gibt eine sortierte Liste von Symbolen nach Volumen zurück (nur Symbol-Namen).
     * 
     * Praktische Variante von getVolumeRanking() wenn nur die Symbol-Namen benötigt werden.
     * 
     * @param symbols Liste von Währungspaaren zum Ranken
     * @return Sortierte Liste von Symbolen (höchstes Volumen zuerst)
     */
    public List<String> getVolumeRankedSymbols(List<String> symbols) {
        return getVolumeRanking(symbols).stream()
                .map(VolumeData::getSymbol)
                .collect(Collectors.toList());
    }

    // ========== Hilfsmethoden ==========

    /**
     * Konvertiert TickerStatistics von Binance API zu VolumeData.
     * 
     * @param stats TickerStatistics von Binance
     * @return VolumeData-Objekt mit allen relevanten Daten
     */
    private VolumeData mapToVolumeData(TickerStatistics stats) {
        VolumeData volumeData = new VolumeData();

        // Basis-Daten
        volumeData.setSymbol(stats.getSymbol());

        // Volumen-Daten
        try {
            BigDecimal volumeBase = new BigDecimal(stats.getVolume());
            BigDecimal lastPrice = new BigDecimal(stats.getLastPrice());
            
            volumeData.setVolumeBase(volumeBase);
            volumeData.setLastPrice(lastPrice);
            // VolumeQuote wird automatisch in setLastPrice() berechnet
            
        } catch (NumberFormatException e) {
            logger.warn("Fehler beim Parsen von Volumen/Preis für {}: {}", 
                       stats.getSymbol(), e.getMessage());
        }

        // Preis-Daten
        try {
            if (stats.getPriceChange() != null) {
                volumeData.setPriceChange(new BigDecimal(stats.getPriceChange()));
            }
            if (stats.getPriceChangePercent() != null) {
                volumeData.setPriceChangePercent(new BigDecimal(stats.getPriceChangePercent()));
            }
            if (stats.getHighPrice() != null) {
                volumeData.setHighPrice(new BigDecimal(stats.getHighPrice()));
            }
            if (stats.getLowPrice() != null) {
                volumeData.setLowPrice(new BigDecimal(stats.getLowPrice()));
            }
            if (stats.getWeightedAvgPrice() != null) {
                volumeData.setWeightedAvgPrice(new BigDecimal(stats.getWeightedAvgPrice()));
            }
        } catch (NumberFormatException e) {
            logger.debug("Fehler beim Parsen von Preis-Daten für {}: {}", 
                        stats.getSymbol(), e.getMessage());
        }

        // Zeitstempel und Trade-Count
        volumeData.setTradeCount(stats.getCount());
        volumeData.setOpenTime(stats.getOpenTime());
        volumeData.setCloseTime(stats.getCloseTime());
        volumeData.setFetchedAt(LocalDateTime.now());

        return volumeData;
    }

    /**
     * Sleep-Hilfsmethode für Retry-Delays.
     * 
     * @param milliseconds Wartezeit in Millisekunden
     */
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sleep wurde unterbrochen", e);
        }
    }

    // ========== Statistische Methoden ==========

    /**
     * Berechnet das Gesamtvolumen über mehrere Währungspaare.
     * 
     * @param symbols Liste von Währungspaaren
     * @return Summe aller Volumen in Quote-Währung
     */
    public BigDecimal getTotalVolume(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalVolume = BigDecimal.ZERO;

        for (String symbol : symbols) {
            VolumeData volumeData = get24HrVolume(symbol);
            if (volumeData != null && volumeData.isValid()) {
                totalVolume = totalVolume.add(volumeData.getVolumeQuote());
            }
        }

        return totalVolume;
    }

    /**
     * Gibt das durchschnittliche Volumen über mehrere Währungspaare zurück.
     * 
     * @param symbols Liste von Währungspaaren
     * @return Durchschnitt aller Volumen in Quote-Währung
     */
    public BigDecimal getAverageVolume(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalVolume = getTotalVolume(symbols);
        return totalVolume.divide(new BigDecimal(symbols.size()), 2, RoundingMode.HALF_UP);
    }
}
