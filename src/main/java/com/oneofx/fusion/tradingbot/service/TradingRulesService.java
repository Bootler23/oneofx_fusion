package com.oneofx.fusion.tradingbot.service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.exception.BinanceApiException;
import com.oneofx.fusion.tradingbot.Settings.bnb;
import com.oneofx.fusion.tradingbot.domain.TradingRules;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service für das Abrufen von Trading-Regeln von Binance.
 * 
 * Dieser Service:
 * - Ruft ExchangeInfo von Binance ab (enthält alle Symbol-Filter)
 * - Parst PRICE_FILTER (tickSize), LOT_SIZE (stepSize, minQty) für jedes Währungspaar
 * - Berechnet automatisch die Dezimalstellen für Preis und Quantity
 * - Speichert die Regeln in der Datenbank (über LTC_EUR_Live beim Start)
 * 
 * @author Trading Bot
 * @version 1.0
 * @since 2026-02-26
 */
public class TradingRulesService {

    // ========== Singleton-Pattern (Thread-Safe) ==========

    private static volatile TradingRulesService instance;

    public static TradingRulesService getInstance() {
        if (instance == null) {
            synchronized (TradingRulesService.class) {
                if (instance == null) {
                    instance = new TradingRulesService();
                }
            }
        }
        return instance;
    }

    // ========== Konfiguration ==========

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    // ========== Instanzvariablen ==========

    private final BinanceApiRestClient client;

    // ========== Konstruktor (Private - Singleton) ==========

    private TradingRulesService() {
        this.client = bnb.getClient();
    }

    // ========== Haupt-Methoden ==========

    /**
     * Ruft die Trading-Regeln für ein Währungspaar von Binance ab.
     * 
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @return TradingRules für das Symbol, oder null bei Fehler
     */
    public TradingRules getTradingRules(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol darf nicht null oder leer sein");
        }

        try {
            ExchangeInfo exchangeInfo = fetchExchangeInfo();
            if (exchangeInfo == null) {
                return null;
            }

            SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(symbol);
            return parseSymbolInfo(symbolInfo);

        } catch (BinanceApiException e) {
            System.err.println("Fehler beim Abrufen der Trading-Regeln für " + symbol + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Aktualisiert die Trading-Regeln für eine Liste von Symbolen.
     * 
     * Ruft alle Regeln von Binance ab und gibt sie zurück.
     * Die Caller-Methode kann sie dann in die Datenbank schreiben.
     * 
     * @param symbols Liste der zu aktualisierenden Symbole
     * @return Anzahl der erfolgreich abgerufenen Symbole
     */
    public int updateTradingRules(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return 0;
        }

        System.out.println("Rufe Trading-Regeln für " + symbols.size() + " Symbole von Binance ab...");
        
        try {
            ExchangeInfo exchangeInfo = fetchExchangeInfo();
            if (exchangeInfo == null) {
                return 0;
            }

            int successCount = 0;

            for (String symbol : symbols) {
                try {
                    SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(symbol);
                    TradingRules rules = parseSymbolInfo(symbolInfo);
                    
                    if (rules != null) {
                        successCount++;
                    }
                    
                } catch (BinanceApiException e) {
                    System.err.println("Symbol " + symbol + " nicht gefunden: " + e.getMessage());
                }
            }

            System.out.println("Trading-Regeln abgerufen: " + successCount + "/" + symbols.size() + " Symbole");
            
            return successCount;

        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Trading-Regeln: " + e.getMessage());
            return 0;
        }
    }

    // ========== ExchangeInfo-Abruf mit Retry-Logic ==========

    /**
     * Ruft ExchangeInfo von Binance mit Retry-Logic ab.
     * 
     * @return ExchangeInfo oder null bei Fehler
     */
    private ExchangeInfo fetchExchangeInfo() {
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                return client.getExchangeInfo();

            } catch (BinanceApiException e) {
                retryCount++;
                
                if (retryCount < MAX_RETRIES) {
                    System.err.println("Fehler beim Abrufen von ExchangeInfo, Retry " + retryCount + "/" + MAX_RETRIES + ": " + e.getMessage());
                    sleep(RETRY_DELAY_MS);
                } else {
                    System.err.println("Maximale Anzahl von Retries erreicht: " + e.getMessage());
                    return null;
                }
                
            } catch (Exception e) {
                System.err.println("Unerwarteter Fehler beim Abrufen von ExchangeInfo: " + e.getMessage());
                return null;
            }
        }

        return null;
    }

    // ========== Parsing-Methoden ==========

    /**
     * Parst SymbolInfo zu TradingRules (nur tickSize, stepSize, minQty).
     */
    private TradingRules parseSymbolInfo(SymbolInfo symbolInfo) {
        if (symbolInfo == null) return null;

        try {
            TradingRules rules = new TradingRules(symbolInfo.getSymbol());

            // tickSize aus PRICE_FILTER
            SymbolFilter priceFilter = symbolInfo.getSymbolFilter(FilterType.PRICE_FILTER);
            if (priceFilter != null && priceFilter.getTickSize() != null) {
                rules.setTickSize(new BigDecimal(priceFilter.getTickSize()));
            }

            // stepSize + minQty aus LOT_SIZE
            SymbolFilter lotSizeFilter = symbolInfo.getSymbolFilter(FilterType.LOT_SIZE);
            if (lotSizeFilter != null) {
                if (lotSizeFilter.getStepSize() != null)
                    rules.setStepSize(new BigDecimal(lotSizeFilter.getStepSize()));
                if (lotSizeFilter.getMinQty() != null)
                    rules.setMinQty(new BigDecimal(lotSizeFilter.getMinQty()));
            }

            return rules;

        } catch (Exception e) {
            System.err.println("Fehler beim Parsen von SymbolInfo für " + symbolInfo.getSymbol() + ": " + e.getMessage());
            return null;
        }
    }

    // ========== Hilfsmethoden ==========

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
