package com.binance.api.tradingbot.Stream;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerPrice;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Klasse zum gleichzeitigen Abrufen von Preisen mehrerer Währungspaare
 * über die Binance REST API
 */
public class MultiPriceFetcher {
    
    private final BinanceApiRestClient restClient;
       
    public MultiPriceFetcher() {
        // REST-Client ohne Authentifizierung erstellen (für öffentliche Marktdaten)
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
        this.restClient = factory.newRestClient();
    }
    
    /**
     * Ruft die aktuellen Preise für eine Liste von Währungspaaren ab
     * 
     * @param symbols Liste der Währungspaare (z.B. "BTCEUR", "ETHEUR", "LTCEUR")
     * @return Map mit Symbol als Key und Preis als Value
     */
    public Map<String, BigDecimal> getMultiplePrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Währungsliste darf nicht leer sein");
        }
        
        Map<String, BigDecimal> prices = new HashMap<>();
        
        try {
            //System.out.println("📊 Lade Preise für " + symbols.size() + " Währungspaare...");
            
            // Alle verfügbaren Preise abrufen
            List<TickerPrice> allPrices = restClient.getAllPrices();
            
            // Nur die gewünschten Symbole filtern
            Set<String> symbolSet = symbols.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            
            for (TickerPrice tickerPrice : allPrices) {
                String symbol = tickerPrice.getSymbol();
                if (symbolSet.contains(symbol)) {
                    try {
                        BigDecimal price = new BigDecimal(tickerPrice.getPrice());
                        prices.put(symbol, price);
                    } catch (NumberFormatException e) {
                        System.err.println("Fehler beim Parsen des Preises für " + symbol + ": " + tickerPrice.getPrice());
                    }
                }
            }
            
            // System.out.println("✅ " + prices.size() + " von " + symbols.size() + " Preisen erfolgreich geladen");
            
        } catch (Exception e) {
            System.err.println("❌ Fehler beim Abrufen der Preise: " + e.getMessage());
            e.printStackTrace();
        }
        
        return prices;
    }
    
    /**
     * Ruft die aktuellen Preise für eine Liste von Währungspaaren ab und gibt sie formatiert aus
     * 
     * @param symbols Liste der Währungspaare
     * @return Map mit Symbol als Key und Preis als Value
     */
    public Map<String, BigDecimal> getMultiplePricesAndPrint(List<String> symbols) {
        Map<String, BigDecimal> prices = getMultiplePrices(symbols);
        
        if (!prices.isEmpty()) {
            System.out.println("\n💰 === AKTUELLE PREISE ===");
            prices.forEach((symbol, price) -> {
                System.out.printf("%-10s: %s%n", symbol, formatPrice(price, symbol));
            });
            System.out.println("==========================\n");
        } else {
            System.out.println("❌ Keine Preise gefunden für die angegebenen Symbole");
        }
        
        return prices;
    }
    
    /**
     * Ruft die aktuellen Preise für eine Liste von Währungspaaren ab und gibt sie als String zurück
     * 
     * @param symbols Liste der Währungspaare
     * @return Formatierter String mit allen Preisen
     */
    public String getMultiplePricesAsString(List<String> symbols) {
        Map<String, BigDecimal> prices = getMultiplePrices(symbols);
        
        if (prices.isEmpty()) {
            return "Keine Preise gefunden für die angegebenen Symbole";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("=== AKTUELLE PREISE ===\n");
        
        prices.forEach((symbol, price) -> {
            result.append(String.format("%-10s: %s%n", symbol, formatPrice(price, symbol)));
        });
        
        result.append("=======================");
        return result.toString();
    }
    
    /**
     * Formatiert den Preis basierend auf dem Symbol
     * 
     * @param price Der Preis
     * @param symbol Das Währungspaar
     * @return Formatierter Preis-String
     */
    private String formatPrice(BigDecimal price, String symbol) {
        if (symbol.contains("EUR")) {
            return String.format("€%.2f", price);
        } else if (symbol.contains("USDT") || symbol.contains("USD")) {
            return String.format("$%.4f", price);
        } else if (symbol.contains("BTC")) {
            return String.format("₿%.8f", price);
        } else {
            return String.format("%.6f", price);
        }
    }
    
    /**
     * Ruft die aktuellen Preise für eine Liste von Währungspaaren ab und gibt sie als JSON-ähnlichen String zurück
     * 
     * @param symbols Liste der Währungspaare
     * @return JSON-ähnlicher String mit allen Preisen
     */
    public String getMultiplePricesAsJson(List<String> symbols) {
        Map<String, BigDecimal> prices = getMultiplePrices(symbols);
        
        if (prices.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(prices.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> entry = entries.get(i);
            json.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            if (i < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("}");
        return json.toString();
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
    
    /**
     * Hilfsmethode zum Erstellen einer Liste von Standard-BTC-Paaren
     * 
     * @return Liste mit häufig verwendeten BTC-Paaren  
     */
    public static List<String> getCommonBTCPairs() {
        return Arrays.asList(
            "ETHBTC", "LTCBTC", "ADABTC", "DOTBTC", "LINKBTC",
            "BCHBTC", "XLMBTC", "EOSBTC", "TRXBTC", "BNBBTC"
        );
    }
    
    /**
     * Ruft die aktuellen Preise für die Top 10 Kryptowährungen ab
     * 
     * @return Map mit Symbol als Key und Preis als Value
     */
    public Map<String, BigDecimal> getTop10CryptoPrices() {
        List<String> top10Symbols = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "XRPUSDT",
            "DOGEUSDT", "DOTUSDT", "UNIUSDT", "LTCUSDT", "LINKUSDT"
        );
        
        return getMultiplePricesAndPrint(top10Symbols);
    }
    
    /**
     * Ruft die aktuellen Preise für die Top 10 EUR-Paare ab
     * 
     * @return Map mit Symbol als Key und Preis als Value
     */
    public Map<String, BigDecimal> getTop10EURPrices() {
        return getMultiplePricesAndPrint(getCommonEURPairs());
    }
    
    /**
     * Testet die Verbindung zur Binance API
     * 
     * @return true wenn die Verbindung erfolgreich ist
     */
    public boolean testConnection() {
        try {
            restClient.ping();
            System.out.println("✅ Verbindung zur Binance API erfolgreich");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Verbindung zur Binance API fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gibt Informationen über die verfügbaren Währungspaare zurück
     * 
     * @return Anzahl der verfügbaren Währungspaare
     */
    public int getAvailableSymbolsCount() {
        try {
            List<TickerPrice> allPrices = restClient.getAllPrices();
            System.out.println("📊 Verfügbare Währungspaare: " + allPrices.size());
            return allPrices.size();
        } catch (Exception e) {
            System.err.println("❌ Fehler beim Abrufen der Symbol-Informationen: " + e.getMessage());
            return 0;
        }
    }
}




