package com.binance.api.tradingbot.Arbitrage;

import java.math.BigDecimal;

/**
 * Schneller Test für den Triangular Arbitrage Bot
 * Diese Klasse demonstriert die einfachste Verwendung
 */
public class QuickTriangularTest {
    
    
    /**
     * Testet verschiedene Startkapitalien
     */
    private static void testVerschiedeneStartkapitalien() {
        BigDecimal[] kapitalien = {
            new BigDecimal("500.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("5000.00")
        };
        
        for (BigDecimal kapital : kapitalien) {
            System.out.printf("\n💡 Startkapital: €%.2f%n", kapital);
            System.out.println("──────────────────────────────────────");
            
            TriangularArbitrageBot testBot = new TriangularArbitrageBot(kapital);
            
            // Nur das Aufgabenstellungs-Beispiel für schnellen Test
            //testBot.beispielAufgabenstellung();
        }
    }
    
    /**
     * Einfache Funktion für einen schnellen Arbitrage-Check
     */
    public static void schnellerArbitrageCheck() {
        System.out.println("⚡ === SCHNELLER ARBITRAGE CHECK ===");
        
        TriangularArbitrageBot bot = new TriangularArbitrageBot(new BigDecimal("1000.00"));
        
        // Nur das Beispiel aus der Aufgabenstellung
        //bot.beispielAufgabenstellung();
        
        System.out.println("✅ Schneller Check abgeschlossen");
    }
    
    /**
     * Demonstriert Gewinn-Berechnungen
     */
    public static void gewinnDemo() {
        System.out.println("💰 === GEWINN DEMONSTRATION ===");
        
        System.out.println("Beispiel: Wie sich verschiedene Startkapitalien auswirken\n");
        
        // Simuliere das Beispiel aus der Aufgabenstellung mit verschiedenen Beträgen
        BigDecimal[] startBetraege = {
            new BigDecimal("100.00"),
            new BigDecimal("1000.00"),  // Original aus Aufgabenstellung
            new BigDecimal("10000.00")
        };
        
        // Preise aus der Aufgabenstellung
        BigDecimal btcEur = new BigDecimal("50000.00");
        BigDecimal ethBtc = new BigDecimal("0.08");
        BigDecimal ethEur = new BigDecimal("4100.00");
        
        for (BigDecimal startBetrag : startBetraege) {
            System.out.printf("📊 Startbetrag: €%.2f%n", startBetrag);
            
            // Berechnung wie in der Aufgabenstellung
            BigDecimal btcMenge = startBetrag.divide(btcEur, 8, java.math.RoundingMode.HALF_UP);
            BigDecimal ethMenge = btcMenge.divide(ethBtc, 8, java.math.RoundingMode.HALF_UP);
            BigDecimal endBetrag = ethMenge.multiply(ethEur);
            BigDecimal gewinn = endBetrag.subtract(startBetrag);
            
            System.out.printf("   → Endbetrag: €%.2f%n", endBetrag);
            System.out.printf("   → Gewinn: €%.2f%n", gewinn);
            System.out.printf("   → Rendite: %.2f%%%n%n", 
                             gewinn.divide(startBetrag, 4, java.math.RoundingMode.HALF_UP)
                                   .multiply(new BigDecimal("100")));
        }
        
        System.out.println("💡 Erkenntnis: Der absolute Gewinn steigt mit dem Startkapital,");
        System.out.println("   aber die prozentuale Rendite bleibt gleich!");
    }
    
    /**
     * Zeigt, was passiert wenn keine Arbitrage-Möglichkeit vorhanden ist
     */
    public static void keinArbitrageBeispiel() {
        System.out.println("❌ === BEISPIEL OHNE ARBITRAGE-MÖGLICHKEIT ===");
        
        // Simuliere Preise, bei denen keine Arbitrage möglich ist
        System.out.println("Angenommen, die Preise sind perfekt ausbalanciert:");
        System.out.println("BTC/EUR → 1 BTC = 50.000 €");
        System.out.println("ETH/BTC → 1 ETH = 0,08 BTC");
        System.out.println("ETH/EUR → 1 ETH = 4.000 € (statt 4.100 €)");
        System.out.println();
        
        BigDecimal startBetrag = new BigDecimal("1000.00");
        BigDecimal btcEur = new BigDecimal("50000.00");
        BigDecimal ethBtc = new BigDecimal("0.08");
        BigDecimal ethEur = new BigDecimal("4000.00"); // Niedrigerer Preis
        
        System.out.println("🔄 Arbitrage-Schritte:");
        
        BigDecimal btcMenge = startBetrag.divide(btcEur, 8, java.math.RoundingMode.HALF_UP);
        System.out.printf("Schritt 1: €%.2f → %.8f BTC%n", startBetrag, btcMenge);
        
        BigDecimal ethMenge = btcMenge.divide(ethBtc, 8, java.math.RoundingMode.HALF_UP);
        System.out.printf("Schritt 2: %.8f BTC → %.8f ETH%n", btcMenge, ethMenge);
        
        BigDecimal endBetrag = ethMenge.multiply(ethEur);
        System.out.printf("Schritt 3: %.8f ETH → €%.2f%n", ethMenge, endBetrag);
        
        BigDecimal verlust = startBetrag.subtract(endBetrag);
        System.out.printf("\n💰 Ergebnis: €%.2f Verlust (%.2f%%)%n", verlust, 
                         verlust.divide(startBetrag, 4, java.math.RoundingMode.HALF_UP)
                               .multiply(new BigDecimal("100")));
        
        System.out.println("❌ Keine profitable Arbitrage-Möglichkeit!");
        System.out.println("\n💡 Dies zeigt, warum ständige Marktüberwachung wichtig ist.");
    }
}
