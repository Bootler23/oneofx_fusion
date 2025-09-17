package com.binance.api.tradingbot.Stream;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Beispiel-Klasse für die Verwendung der MultiPriceFetcher-Funktionalität
 * zum gleichzeitigen Abrufen von Preisen mehrerer Währungspaare
 */
public class MultiPriceExample {
    
    public static void main(String[] args) {
        System.out.println("🚀 MultiPriceFetcher Beispiel gestartet\n");
        
        // MultiPriceFetcher-Instanz erstellen
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        
        // Verbindung testen
        if (!priceFetcher.testConnection()) {
            System.err.println("❌ Verbindung zur Binance API fehlgeschlagen. Beende Programm.");
            return;
        }
        
        // Beispiel 1: Top 10 Kryptowährungen
        beispielTop10Crypto(priceFetcher);
        
        // Beispiel 2: Benutzerdefinierte Währungspaare
        beispielBenutzerdefiniertePaare(priceFetcher);
        
        // Beispiel 3: EUR-Paare
        beispielEURPaare(priceFetcher);
        
        // Beispiel 4: Verschiedene Ausgabeformate
        beispielVerschiedeneFormate(priceFetcher);
        
        // Beispiel 5: Arbitrage-Analyse
        beispielArbitrageAnalyse(priceFetcher);
        
        System.out.println("✅ Alle Beispiele erfolgreich abgeschlossen!");
    }
    
    /**
     * Beispiel 1: Top 10 Kryptowährungen abrufen
     */
    private static void beispielTop10Crypto(MultiPriceFetcher priceFetcher) {
        System.out.println("=== Beispiel 1: Top 10 Kryptowährungen ===");
        
        Map<String, BigDecimal> prices = priceFetcher.getTop10CryptoPrices();
        
        // Zusätzliche Analyse
        if (!prices.isEmpty()) {
            BigDecimal highestPrice = prices.values().stream()
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            
            BigDecimal lowestPrice = prices.values().stream()
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            
            System.out.printf("📈 Höchster Preis: $%.4f%n", highestPrice);
            System.out.printf("📉 Niedrigster Preis: $%.4f%n", lowestPrice);
        }
        
        System.out.println();
    }
    
    /**
     * Beispiel 2: Benutzerdefinierte Währungspaare
     */
    private static void beispielBenutzerdefiniertePaare(MultiPriceFetcher priceFetcher) {
        //System.out.println("=== Beispiel 2: Benutzerdefinierte Währungspaare ===");
        
        // Benutzerdefinierte Liste von Währungspaaren
        List<String> customSymbols = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "XRPUSDT",
            "DOGEUSDT", "DOTUSDT", "UNIUSDT", "LTCUSDT", "LINKUSDT"
        );
        
        priceFetcher.getMultiplePricesAndPrint(customSymbols);
        
        // Preisänderungen simulieren (für Demo-Zwecke)
        System.out.println("💡 Tipp: Für Live-Updates verwenden Sie die Stream-Klassen");
        
        System.out.println();
    }
    
    /**
     * Beispiel 3: EUR-Paare
     */
    private static void beispielEURPaare(MultiPriceFetcher priceFetcher) {
        System.out.println("=== Beispiel 3: EUR-Paare ===");
        
        Map<String, BigDecimal> eurPrices = priceFetcher.getTop10EURPrices();
        
        // EUR-spezifische Analyse
        if (!eurPrices.isEmpty()) {
            System.out.println("🇪🇺 EUR-Paare erfolgreich geladen:");
            eurPrices.forEach((symbol, price) -> {
                System.out.printf("  %s: €%.2f%n", symbol, price);
            });
        }
        
        System.out.println();
    }
    
    /**
     * Beispiel 4: Verschiedene Ausgabeformate
     */
    private static void beispielVerschiedeneFormate(MultiPriceFetcher priceFetcher) {
        System.out.println("=== Beispiel 4: Verschiedene Ausgabeformate ===");
        
        List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT", "BNBUSDT");
        
        // Format 1: Als String
        System.out.println("📝 Als String:");
        String priceString = priceFetcher.getMultiplePricesAsString(symbols);
        System.out.println(priceString);
        
        // Format 2: Als JSON
        System.out.println("📋 Als JSON:");
        String priceJson = priceFetcher.getMultiplePricesAsJson(symbols);
        System.out.println(priceJson);
        
        System.out.println();
    }
    
    /**
     * Beispiel 5: Arbitrage-Analyse
     */
    private static void beispielArbitrageAnalyse(MultiPriceFetcher priceFetcher) {
        System.out.println("=== Beispiel 5: Arbitrage-Analyse ===");
        
        // Währungspaare für Arbitrage-Analyse
        List<String> arbitrageSymbols = Arrays.asList(
            "BTCUSDT", "BTCEUR", "ETHUSDT", "ETHEUR", "BNBUSDT", "BNBEUR"
        );
        
        Map<String, BigDecimal> prices = priceFetcher.getMultiplePrices(arbitrageSymbols);
        
        if (prices.size() >= 4) {
            // Einfache Arbitrage-Logik (ohne Wechselkurs-Berücksichtigung)
            BigDecimal btcUsdt = prices.get("BTCUSDT");
            BigDecimal btcEur = prices.get("BTCEUR");
            BigDecimal ethUsdt = prices.get("ETHUSDT");
            BigDecimal ethEur = prices.get("ETHEUR");
            
            if (btcUsdt != null && btcEur != null && ethUsdt != null && ethEur != null) {
                System.out.println("🔍 Arbitrage-Analyse:");
                System.out.printf("BTC: $%.2f vs €%.2f%n", btcUsdt, btcEur);
                System.out.printf("ETH: $%.2f vs €%.2f%n", ethUsdt, ethEur);
                
                // Einfache Preisverhältnis-Analyse
                BigDecimal btcRatio = btcUsdt.divide(btcEur, 4, BigDecimal.ROUND_HALF_UP);
                BigDecimal ethRatio = ethUsdt.divide(ethEur, 4, BigDecimal.ROUND_HALF_UP);
                
                System.out.printf("BTC USD/EUR Verhältnis: %.4f%n", btcRatio);
                System.out.printf("ETH USD/EUR Verhältnis: %.4f%n", ethRatio);
                
                if (btcRatio.compareTo(ethRatio) > 0) {
                    System.out.println("💡 BTC zeigt höheres USD/EUR Verhältnis als ETH");
                } else {
                    System.out.println("💡 ETH zeigt höheres USD/EUR Verhältnis als BTC");
                }
            }
        }
        
        System.out.println();
    }
    
    /**
     * Beispiel für die Verwendung in einem Trading-Bot
     */
    public static void tradingBotBeispiel() {
        System.out.println("=== Trading Bot Beispiel ===");
        
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        
        // Währungspaare für den Bot
        List<String> botSymbols = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "XRPUSDT"
        );
        
        // Preise abrufen
        Map<String, BigDecimal> currentPrices = priceFetcher.getMultiplePrices(botSymbols);
        
        // Trading-Logik simulieren
        currentPrices.forEach((symbol, price) -> {
            System.out.printf("🤖 Bot analysiert %s: $%.4f%n", symbol, price);
            
            // Hier würde Ihre Trading-Logik stehen
            // z.B. technische Indikatoren, Support/Resistance, etc.
        });
        
        System.out.println("✅ Trading Bot Analyse abgeschlossen");
    }
    
    /**
     * Beispiel für kontinuierliche Preisüberwachung
     */
    public static void kontinuierlicheUeberwachung() {
        System.out.println("=== Kontinuierliche Preisüberwachung ===");
        
        MultiPriceFetcher priceFetcher = new MultiPriceFetcher();
        
        // Währungspaare für Überwachung
        List<String> watchSymbols = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT"
        );
        
        // Simuliere 5 Updates
        for (int i = 1; i <= 5; i++) {
            System.out.printf("🔄 Update #%d:%n", i);
            
            Map<String, BigDecimal> prices = priceFetcher.getMultiplePrices(watchSymbols);
            
            if (!prices.isEmpty()) {
                prices.forEach((symbol, price) -> {
                    System.out.printf("  %s: $%.4f%n", symbol, price);
                });
            }
            
            // Warten zwischen Updates (in einem echten Bot würde das anders implementiert)
            try {
                Thread.sleep(1000); // 1 Sekunde warten
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("✅ Kontinuierliche Überwachung beendet");
    }
}
