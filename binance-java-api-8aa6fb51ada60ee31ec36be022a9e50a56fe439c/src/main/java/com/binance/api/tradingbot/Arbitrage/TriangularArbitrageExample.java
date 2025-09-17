package com.binance.api.tradingbot.Arbitrage;

import java.math.BigDecimal;

public class TriangularArbitrageExample {
    

    public static void beispiel2_LivePreise(TriangularArbitrageBot bot) {
        // System.out.println("📡 === BEISPIEL 2: LIVE PREISE VON BINANCE ===");
        
        // Analyse mit echten Binance-Preisen
        bot.analyseTriangularArbitrage("BTCEUR", "ETHBTC", "ETHEUR");
        bot.analyseTriangularArbitrage("ETHBTC", "BNBETH", "BNBBTC");
        
        
        warteKurz();
    }    
  
    private static void beispiel4_KontinuierlicheUeberwachung(TriangularArbitrageBot bot) {
        System.out.println("📡 === BEISPIEL 4: KONTINUIERLICHE ÜBERWACHUNG ===");
        System.out.println("Demo mit 3 Updates (normalerweise würde das länger laufen)");
        System.out.println();
        
        // Kurze Demo mit nur 3 Updates
        bot.kontinuierlicheUeberwachung(3);
        
        warteKurz();
    }    
  
    public static void erweiterteCurrencyCombination() {
        System.out.println("🚀 === ERWEITERTE ARBITRAGE KOMBINATIONEN (TOP 30 VOLUMEN) ===");
        
        TriangularArbitrageBot bot = new TriangularArbitrageBot(new BigDecimal("2000.00"));
        
        if (!bot.testVerbindung()) {
            System.err.println("❌ Keine API-Verbindung");
            return;
        }        
      
        String[][] hochvolumenKombinationen = {
            // Tier 1: Höchstes Volumen (Milliarden täglich)
            {"BTCEUR", "ETHBTC", "ETHEUR"},     // Bitcoin → Ethereum → EUR
            {"BTCEUR", "BNBBTC", "BNBEUR"},     // Bitcoin → Binance Coin → EUR
            {"BTCEUR", "SOLBTC", "SOLEUR"},     // Bitcoin → Solana → EUR
            
            // Tier 2: Sehr hohes Volumen (Hunderte Millionen)
            {"BTCEUR", "ADABTC", "ADAEUR"},     // Bitcoin → Cardano → EUR
            {"BTCEUR", "XRPBTC", "XRPEUR"},     // Bitcoin → Ripple → EUR
            {"BTCEUR", "MATICBTC", "MATICEUR"}, // Bitcoin → Polygon → EUR
            {"BTCEUR", "DOTBTC", "DOTEUR"},     // Bitcoin → Polkadot → EUR
            
            // Tier 3: Hohes Volumen (Stabile Altcoins)
            {"BTCEUR", "AVAXBTC", "AVAXEUR"},   // Bitcoin → Avalanche → EUR
            {"BTCEUR", "LINKBTC", "LINKEUR"},   // Bitcoin → Chainlink → EUR
            {"BTCEUR", "ATOMBTC", "ATOMEUR"},   // Bitcoin → Cosmos → EUR
            
            // Alternative Strategien (ETH als Basis)
            {"ETHEUR", "BNBETH", "BNBEUR"},     // Ethereum → BNB → EUR
            {"ETHEUR", "ADAETH", "ADAEUR"},     // Ethereum → Cardano → EUR
            {"ETHEUR", "SOLETH", "SOLEUR"},     // Ethereum → Solana → EUR
            
            // USDT-Strategien (für Stablecoin-Arbitrage)
            {"BTCUSDT", "ETHBTC", "ETHUSDT"},   // BTC → ETH → USDT
            {"BTCUSDT", "BNBBTC", "BNBUSDT"}    // BTC → BNB → USDT
        };
        
        for (int i = 0; i < hochvolumenKombinationen.length; i++) {
            String[] kombination = hochvolumenKombinationen[i];
            
            System.out.printf("\n🔍 Kombination %d/%d: %s%n", 
                             i + 1, hochvolumenKombinationen.length,
                             String.join(" → ", kombination));
            System.out.println("══════════════════════════════════════════════════");
            
            try {
                bot.analyseTriangularArbitrage(kombination[0], kombination[1], kombination[2]);
            } catch (Exception e) {
                System.err.printf("❌ Fehler bei Kombination %s: %s%n", 
                                 String.join(" → ", kombination), e.getMessage());
            }
            
            // Kurze Pause zwischen Anfragen
            if (i < hochvolumenKombinationen.length - 1) {
                try {
                    Thread.sleep(500); // 0.5 Sekunden Pause
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        System.out.println("✅ Alle erweiterten Kombinationen getestet");
        
        // Zusätzliche Analyse
        System.out.println("\n📊 === VOLUMEN-ANALYSE ===");
        System.out.println("💡 Empfohlene Strategien basierend auf Handelsvolumen:");
        System.out.println("   🥇 Tier 1: BTC/ETH/BNB/SOL - Höchste Liquidität");
        System.out.println("   🥈 Tier 2: ADA/XRP/MATIC/DOT - Hohe Liquidität");
        System.out.println("   🥉 Tier 3: AVAX/LINK/ATOM - Stabile Liquidität");
        System.out.println("\n⚠️ HINWEIS: Höheres Volumen = geringere Spreads = bessere Arbitrage-Chancen");
    }

    public static void currencyCombination() {
        System.out.println("🔄 === VERSCHIEDENE ARBITRAGE KOMBINATIONEN ===");
        
        TriangularArbitrageBot bot = new TriangularArbitrageBot(new BigDecimal("1000.00"));
        
        if (!bot.testVerbindung()) {
            System.err.println("❌ Keine API-Verbindung");
            return;
        }        
      
        String[][] kombinationen = {
            // Top 10 Kombinationen basierend auf den 30 stärksten Währungen nach Volumen
            {"BTCEUR", "ETHBTC", "ETHEUR"},     // BTC → ETH → EUR (Volumen: sehr hoch)
            {"BTCEUR", "BNBBTC", "BNBEUR"},     // BTC → BNB → EUR (Binance Coin, hohes Volumen)
            {"BTCEUR", "ADABTC", "ADAEUR"},     // BTC → ADA → EUR (Cardano, hohes Volumen)
            {"BTCEUR", "SOLBTC", "SOLEUR"},     // BTC → SOL → EUR (Solana, sehr hohes Volumen)
            {"BTCEUR", "XRPBTC", "XRPEUR"},     // BTC → XRP → EUR (Ripple, konstant hohes Volumen)
            {"BTCEUR", "DOTBTC", "DOTEUR"},     // BTC → DOT → EUR (Polkadot, stabiles Volumen)
            {"BTCEUR", "MATICBTC", "MATICEUR"}, // BTC → MATIC → EUR (Polygon, hohes DeFi-Volumen)
            {"BTCEUR", "AVAXBTC", "AVAXEUR"},   // BTC → AVAX → EUR (Avalanche, hohes Volumen)
            {"BTCEUR", "LINKBTC", "LINKEUR"},   // BTC → LINK → EUR (Chainlink, Oracle-Leader)
            {"BTCEUR", "ATOMBTC", "ATOMEUR"}    // BTC → ATOM → EUR (Cosmos, IBC-Ökosystem)
        };
        
        for (int i = 0; i < kombinationen.length; i++) {
            String[] kombination = kombinationen[i];
            
            System.out.printf("\n🔍 Kombination %d/%d:%n", i + 1, kombinationen.length);
            System.out.println("══════════════════════════════════════════════════");
            
            try {
                bot.analyseTriangularArbitrage(kombination[0], kombination[1], kombination[2]);
            } catch (Exception e) {
                System.err.printf("❌ Fehler bei Kombination %s: %s%n", 
                                 String.join(" → ", kombination), e.getMessage());
            }
            
            if (i < kombinationen.length - 1) {
                warteKurz();
            }
        }
        
        System.out.println("✅ Alle Kombinationen getestet");
    }
  
    public static void praktischesBeispiel() {
        System.out.println("🤖 === PRAKTISCHES TRADING BOT BEISPIEL ===");
        
        // Bot mit realistischem Startkapital
        TriangularArbitrageBot bot = new TriangularArbitrageBot(new BigDecimal("2500.00"));
        
        System.out.printf("💰 Trading Bot gestartet mit Startkapital: €%.2f%n", bot.getStartkapital());
        
        if (!bot.testVerbindung()) {
            System.err.println("❌ Trading Bot kann nicht starten - keine API-Verbindung");
            return;
        }
        
        System.out.println("\n🔍 Suche nach profitablen Arbitrage-Möglichkeiten...");
        
        // Hauptstrategie: BTC → ETH → EUR
        System.out.println("\n📈 Strategie 1: BTC → ETH → EUR");
        bot.analyseTriangularArbitrage("BTCEUR", "ETHBTC", "ETHEUR");
        
        // Alternative Strategie: BTC → ADA → EUR
        System.out.println("📈 Strategie 2: BTC → ADA → EUR");
        bot.analyseTriangularArbitrage("BTCEUR", "ADABTC", "ADAEUR");
        
        // Weitere Alternative: BTC → LTC → EUR
        System.out.println("📈 Strategie 3: BTC → LTC → EUR");
        bot.analyseTriangularArbitrage("BTCEUR", "LTCBTC", "LTCEUR");
        
        System.out.println("\n💡 HINWEIS: In einem echten Trading Bot würden hier automatische");
        System.out.println("   Trades ausgeführt werden, wenn profitable Möglichkeiten gefunden werden.");
        System.out.println("   Dieser Bot zeigt nur die Berechnungen!");
        
        System.out.println("\n🔚 Trading Bot Demo beendet");
    }
    
    public static void risikoManagementBeispiel() {
        System.out.println("⚖️ === RISIKO-MANAGEMENT BEISPIEL ===");
        
        // Verschiedene Risiko-Level mit unterschiedlichen Startkapitalien
        BigDecimal[] risikoKapital = {
            new BigDecimal("100.00"),   // Niedriges Risiko
            new BigDecimal("500.00"),   // Mittleres Risiko  
            new BigDecimal("2000.00")   // Höheres Risiko
        };
        
        String[] risikoLevel = {"NIEDRIG", "MITTEL", "HOCH"};
        
        for (int i = 0; i < risikoKapital.length; i++) {
            System.out.printf("\n⚡ Risiko-Level: %s (€%.2f)%n", risikoLevel[i], risikoKapital[i]);
            System.out.println("────────────────────────────────────────");
            
            TriangularArbitrageBot risikoBot = new TriangularArbitrageBot(risikoKapital[i]);
            
            // Beispiel-Analyse
            //risikoBot.beispielAufgabenstellung();
            
            // Empfehlung basierend auf Risiko-Level
            System.out.printf("💡 Empfehlung für %s Risiko:%n", risikoLevel[i]);
            switch (i) {
                case 0:
                    System.out.println("   → Konservative Strategie, kleine Beträge");
                    System.out.println("   → Nur bei hoher Gewinnwahrscheinlichkeit handeln");
                    break;
                case 1:
                    System.out.println("   → Ausgewogene Strategie, moderate Beträge");
                    System.out.println("   → Regelmäßige Überwachung von Arbitrage-Möglichkeiten");
                    break;
                case 2:
                    System.out.println("   → Aggressive Strategie, größere Beträge");
                    System.out.println("   → Schnelle Reaktion auf Marktchancen erforderlich");
                    break;
            }
        }
        
        System.out.println("\n WICHTIGER HINWEIS:");
        System.out.println("   Triangular Arbitrage ist nur bei sehr schneller Ausführung profitabel!");
        System.out.println("   Marktpreise ändern sich in Sekunden - dieser Bot ist nur zur Demonstration!");
    }
        
    private static void warteKurz() {
        try {
            Thread.sleep(1000); // 1 Sekunden warten
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
