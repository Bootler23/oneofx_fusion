package com.binance.api.tradingbot.Stream;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Schneller Test für die MultiPriceFetcher-Funktionalität
 * Diese Klasse demonstriert die einfachste Verwendung der neuen Funktion
 */
public class QuickPriceTest {
    
    public static void main(String[] args) {
        System.out.println("🚀 Quick Price Test gestartet\n");
        
        // MultiPriceFetcher-Instanz erstellen
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        
        // Verbindung testen
        System.out.println("🔍 Teste Verbindung zur Binance API...");
        if (!priceFetcher.testConnection()) {
            System.err.println("❌ Verbindung fehlgeschlagen. Beende Test.");
            return;
        }
        
        // Test 1: Top 10 Kryptowährungen
        System.out.println("\n📊 Test 1: Top 10 Kryptowährungen");
        System.out.println("=====================================");
        Map<String, BigDecimal> top10Prices = priceFetcher.getTop10CryptoPrices();
        
        // Test 2: EUR-Paare
        System.out.println("\n🇪🇺 Test 2: Top 10 EUR-Paare");
        System.out.println("==============================");
        Map<String, BigDecimal> eurPrices = priceFetcher.getTop10EURPrices();
        
        // Test 3: Benutzerdefinierte Paare
        System.out.println("\n🎯 Test 3: Benutzerdefinierte Währungspaare");
        System.out.println("=============================================");
        
        List<String> customSymbols = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "XRPUSDT",
            "DOGEUSDT", "DOTUSDT", "UNIUSDT", "LTCUSDT", "LINKUSDT"
        );
        
        Map<String, BigDecimal> customPrices = priceFetcher.getMultiplePricesAndPrint(customSymbols);
        
        // Test 4: Verschiedene Ausgabeformate
        System.out.println("\n📝 Test 4: Verschiedene Ausgabeformate");
        System.out.println("=======================================");
        
        List<String> testSymbols = Arrays.asList("BTCUSDT", "ETHUSDT", "BNBUSDT");
        
        System.out.println("Als String:");
        String priceString = priceFetcher.getMultiplePricesAsString(testSymbols);
        System.out.println(priceString);
        
        System.out.println("\nAls JSON:");
        String priceJson = priceFetcher.getMultiplePricesAsJson(testSymbols);
        System.out.println(priceJson);
        
        // Test 5: Verfügbare Symbole
        System.out.println("\n📈 Test 5: Verfügbare Symbole");
        System.out.println("=============================");
        int symbolCount = priceFetcher.getAvailableSymbolsCount();
        
        // Zusammenfassung
        System.out.println("\n✅ Test Zusammenfassung");
        System.out.println("========================");
        System.out.println("Top 10 Crypto Preise: " + top10Prices.size() + " geladen");
        System.out.println("EUR Paare: " + eurPrices.size() + " geladen");
        System.out.println("Benutzerdefinierte Paare: " + customPrices.size() + " geladen");
        System.out.println("Verfügbare Symbole insgesamt: " + symbolCount);
        
        System.out.println("\n🎉 Alle Tests erfolgreich abgeschlossen!");
        
        // Beispiel für die Verwendung in einem Trading-Bot
        System.out.println("\n🤖 Beispiel für Trading Bot Verwendung:");
        System.out.println("=======================================");
        
        // Simuliere Trading-Bot Logik
        if (!top10Prices.isEmpty()) {
            System.out.println("Trading Bot analysiert Preise:");
            
            // Finde höchsten und niedrigsten Preis
            BigDecimal highestPrice = top10Prices.values().stream()
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            
            BigDecimal lowestPrice = top10Prices.values().stream()
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            
            System.out.printf("📈 Höchster Preis: $%.4f%n", highestPrice);
            System.out.printf("📉 Niedrigster Preis: $%.4f%n", lowestPrice);
            
            // Finde das Symbol mit dem höchsten Preis
            String highestSymbol = top10Prices.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(highestPrice))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("Unbekannt");
            
            System.out.printf("🏆 Höchster Preis gehört zu: %s%n", highestSymbol);
        }
        
        System.out.println("\n💡 Tipp: Für Live-Updates verwenden Sie die Stream-Klassen (FastStream, UltraFastStream)");
        System.out.println("💡 Diese MultiPriceFetcher-Klasse ist ideal für einmalige Preisabfragen");
    }
    
    /**
     * Einfache Funktion zum Abrufen von 10 Währungspreisen
     * Diese Funktion kann direkt in Ihrem Trading-Bot verwendet werden
     */
    public static Map<String, BigDecimal> get10CryptoPrices() {
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        
        // Top 10 Kryptowährungen
        List<String> symbols = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "XRPUSDT",
            "DOGEUSDT", "DOTUSDT", "UNIUSDT", "LTCUSDT", "LINKUSDT"
        );
        
        return priceFetcher.getMultiplePrices(symbols);
    }
    
    /**
     * Einfache Funktion zum Abrufen von 10 EUR-Paaren
     */
    public static Map<String, BigDecimal> get10EURPrices() {
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        return priceFetcher.getMultiplePrices(MultiPriceFetcher.getCommonEURPairs());
    }
    
    /**
     * Einfache Funktion zum Abrufen von 10 BTC-Paaren
     */
    public static Map<String, BigDecimal> get10BTCPrices() {
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        return priceFetcher.getMultiplePrices(MultiPriceFetcher.getCommonBTCPairs());
    }
}




