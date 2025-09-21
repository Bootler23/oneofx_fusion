package com.binance.api.tradingbot.Arbitrage;

import com.binance.api.tradingbot.Stream.MultiPriceFetcher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Triangular Arbitrage Bot - Erkennt Arbitrage-Möglichkeiten zwischen drei
 * Währungspaaren
 * auf derselben Börse (Binance)
 * 
 * Beispiel: BTC/EUR → ETH/BTC → ETH/EUR → zurück zu EUR
 * 
 * WICHTIG: Dieser Bot führt KEINE echten Trades aus, sondern zeigt nur
 * Berechnungen!
 */
public class TriangularArbitrageBot {

    private final MultiPriceFetcher priceFetcher;
    private BigDecimal startkapital;

    /**
     * Konstruktor für den Triangular Arbitrage Bot
     * 
     * @param startkapital Das Startkapital in EUR
     */
    public TriangularArbitrageBot(BigDecimal startkapital) {
        this.priceFetcher = new MultiPriceFetcher();
        this.startkapital = startkapital;
    }

    /**
     * Führt eine Triangular Arbitrage Analyse durch
     * 
     * @param pair1 Erstes Währungspaar (z.B. "BTCEUR")
     * @param pair2 Zweites Währungspaar (z.B. "ETHBTC")
     * @param pair3 Drittes Währungspaar (z.B. "ETHEUR")
     */
    public void analyseTriangularArbitrage(String pair1, String pair2, String pair3) {
        // System.out.println("🔍 === TRIANGULAR ARBITRAGE ANALYSE ===");
        // System.out.println("Startkapital: €" + startkapital);
        // System.out.println("Analysiere Paare: " + pair1 + " → " + pair2 + " → " +
        // pair3);
        // System.out.println("==========================================\n");

        // Preise von Binance API abrufen
        List<String> symbols = Arrays.asList(pair1, pair2, pair3);
        Map<String, BigDecimal> preise = priceFetcher.getMultiplePrices(symbols);

        if (preise.size() != 3) {
            System.err.println("❌ Nicht alle Preise verfügbar!");
            preise.forEach((symbol, preis) -> System.out.println("Verfügbar: " + symbol + " = " + preis));
            return;
        }

        // Preise anzeigen
        // System.out.println("Aktuelle Marktpreise:");
        preise.forEach((symbol, preis) -> {
            // System.out.printf("%-10s → %s%n", symbol, formatPreis(preis, symbol));

            //System.out.println(preis + " " + symbol);
        });

        // Triangular Arbitrage berechnen
        BigDecimal endkapital = berechneTriangularArbitrage(preise.get(pair1), preise.get(pair2), preise.get(pair3),
                pair1, pair2, pair3);

        // Ergebnis anzeigen
        BigDecimal gewinn = endkapital.subtract(startkapital);
        BigDecimal gewinnProzent = gewinn.divide(startkapital, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        // System.out.println("💰 === ERGEBNIS ===");
        // System.out.printf("Startkapital: €%.2f%n", startkapital);
        // System.out.printf("Endkapital: €%.2f%n", endkapital);
        //System.out.printf("Gewinn/Verlust: €%.2f (%.4f%%)%n", gewinn, gewinnProzent);

        BigDecimal mindestgewinn = new BigDecimal("0.1");
        if (gewinn.compareTo(mindestgewinn) >= 0) {
            System.out.println("ARBITRAGE-MÖGLICHKEIT GEFUNDEN!");
            System.out.printf("Gewinn/Verlust: €%.2f (%.4f%%)%n", gewinn, gewinnProzent);
        }
    }

    /**
     * Berechnet die Triangular Arbitrage für drei Währungspaare
     */
    private BigDecimal berechneTriangularArbitrage(BigDecimal preis1, BigDecimal preis2, BigDecimal preis3,
            String pair1, String pair2, String pair3) {

        BigDecimal aktuelleBetrag = startkapital;

        // System.out.println("🔄 Arbitrage-Schritte:");

        // Schritt 1: EUR → BTC (über pair1 = BTCEUR)
        if (pair1.endsWith("EUR")) {
            BigDecimal btcMenge = aktuelleBetrag.divide(preis1, 8, RoundingMode.HALF_UP);
            // System.out.printf("Schritt 1 - EUR → BTC: €%.2f ÷ €%.2f = %.8f BTC%n",
            // aktuelleBetrag, preis1, btcMenge);
            aktuelleBetrag = btcMenge;
        }

        // Schritt 2: BTC → ETH (über pair2 = ETHBTC)
        if (pair2.startsWith("ETH") && pair2.endsWith("BTC")) {
            BigDecimal ethMenge = aktuelleBetrag.divide(preis2, 8, RoundingMode.HALF_UP);
            // System.out.printf("Schritt 2 - BTC → ETH: %.8f BTC ÷ %.8f BTC/ETH = %.8f
            // ETH%n",
            // aktuelleBetrag, preis2, ethMenge);
            aktuelleBetrag = ethMenge;
        }

        // Schritt 3: ETH → EUR (über pair3 = ETHEUR)
        if (pair3.startsWith("ETH") && pair3.endsWith("EUR")) {
            BigDecimal endEur = aktuelleBetrag.multiply(preis3);
            // System.out.printf("Schritt 3 - ETH → EUR: %.8f ETH × €%.2f = €%.2f%n",
            // aktuelleBetrag, preis3, endEur);
            aktuelleBetrag = endEur;
        }

        return aktuelleBetrag;
    }

    /**
     * Führt das Beispiel aus der Aufgabenstellung durch
     */
    // public void beispielAufgabenstellung() {
    // System.out.println("📋 === BEISPIEL AUS AUFGABENSTELLUNG ===");
    // System.out.println("Simuliere mit fiktiven Preisen:");
    // System.out.println("BTC/EUR → 1 BTC = 50.000 €");
    // System.out.println("ETH/BTC → 1 ETH = 0,08 BTC");
    // System.out.println("ETH/EUR → 1 ETH = 4.100 €");
    // System.out.println("Startkapital: €1.000");
    // System.out.println("========================================\n");

    // // Fiktive Preise aus der Aufgabenstellung
    // BigDecimal btcEur = new BigDecimal("50000.00"); // 1 BTC = 50.000 €
    // BigDecimal ethBtc = new BigDecimal("0.08"); // 1 ETH = 0,08 BTC
    // BigDecimal ethEur = new BigDecimal("4100.00"); // 1 ETH = 4.100 €

    // BigDecimal startBetrag = new BigDecimal("1000.00");

    // System.out.println("🔄 Arbitrage-Schritte (Beispiel):");

    // // Schritt 1: Euro → BTC
    // BigDecimal btcMenge = startBetrag.divide(btcEur, 8, RoundingMode.HALF_UP);
    // System.out.printf("Schritt 1 - Euro → BTC: €%.2f ÷ €%.2f = %.8f BTC%n",
    // startBetrag, btcEur, btcMenge);

    // // Schritt 2: BTC → ETH
    // BigDecimal ethMenge = btcMenge.divide(ethBtc, 8, RoundingMode.HALF_UP);
    // System.out.printf("Schritt 2 - BTC → ETH: %.8f BTC ÷ %.8f BTC/ETH = %.8f
    // ETH%n",
    // btcMenge, ethBtc, ethMenge);

    // // Schritt 3: ETH → Euro
    // BigDecimal endEur = ethMenge.multiply(ethEur);
    // System.out.printf("Schritt 3 - ETH → Euro: %.8f ETH × €%.2f = €%.2f%n",
    // ethMenge, ethEur, endEur);

    // // Ergebnis
    // BigDecimal gewinn = endEur.subtract(startBetrag);
    // BigDecimal gewinnProzent = gewinn.divide(startBetrag, 4,
    // RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

    // System.out.println("\n💰 === ERGEBNIS (BEISPIEL) ===");
    // System.out.printf("Startkapital: €%.2f%n", startBetrag);
    // System.out.printf("Endkapital: €%.2f%n", endEur);
    // System.out.printf("Gewinn: €%.2f (%.2f%%)%n", gewinn, gewinnProzent);
    // System.out.println("✅ Profitable Arbitrage-Möglichkeit! 🚀");
    // System.out.println("==============================\n");
    // }

    /**
     * Analysiert mehrere Triangular Arbitrage Möglichkeiten
     */
    public void analyseAlleTriangularMoeglichkeiten() {
        System.out.println("🔍 === ALLE TRIANGULAR ARBITRAGE MÖGLICHKEITEN ===\n");

        // Verschiedene Triangular Arbitrage Kombinationen
        String[][] kombinationen = {
                { "BTCEUR", "ETHBTC", "ETHEUR" },
                { "BTCEUR", "ADABTC", "ADAEUR" },
                { "BTCEUR", "LTCBTC", "LTCEUR" },
                { "ETHEUR", "ADAETH", "ADAEUR" },
                { "ETHEUR", "LTCETH", "LTCEUR" }
        };

        for (String[] kombination : kombinationen) {
            try {
                analyseTriangularArbitrage(kombination[0], kombination[1], kombination[2]);
                Thread.sleep(1000); // 1 Sekunde Pause zwischen Analysen
            } catch (Exception e) {
                System.err.println("Fehler bei Kombination " + Arrays.toString(kombination) + ": " + e.getMessage());
            }
        }
    }

    public void kontinuierlicheUeberwachung(int anzahlUpdates) {
        System.out.println("=== KONTINUIERLICHE ARBITRAGE ÜBERWACHUNG ===");
        System.out.printf("Überwachung für %d Updates...%n%n", anzahlUpdates);

        for (int i = 1; i <= anzahlUpdates; i++) {
            System.out.printf("Update #%d/%d:%n", i, anzahlUpdates);
            System.out.println("──────────────────────────────────────────────────");

            // Hauptkombination überwachen
            analyseTriangularArbitrage("BTCEUR", "ETHBTC", "ETHEUR");

            if (i < anzahlUpdates) {
                System.out.println("Warte 1 Sekunden bis zum nächsten Update...\n");
                try {
                    Thread.sleep(1000); // 10 Sekunden warten
                } catch (InterruptedException e) {
                    System.err.println("Überwachung unterbrochen");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.println("✅ Kontinuierliche Überwachung beendet");
    }

    /**
     * Formatiert Preise basierend auf dem Währungspaar
     */
    private String formatPreis(BigDecimal preis, String symbol) {
        if (symbol.contains("EUR")) {
            return String.format("€%.2f", preis);
        } else if (symbol.contains("USDT") || symbol.contains("USD")) {
            return String.format("$%.4f", preis);
        } else if (symbol.contains("BTC")) {
            return String.format("₿%.8f", preis);
        } else {
            return String.format("%.6f", preis);
        }
    }

    /**
     * Setzt ein neues Startkapital
     */
    public void setStartkapital(BigDecimal neuesStartkapital) {
        this.startkapital = neuesStartkapital;
        System.out.printf("💰 Startkapital geändert auf: €%.2f%n", neuesStartkapital);
    }

    /**
     * Gibt das aktuelle Startkapital zurück
     */
    public BigDecimal getStartkapital() {
        return startkapital;
    }

    /**
     * Testet die Verbindung zur Binance API
     */
    public boolean testVerbindung() {
        System.out.println("🔗 Teste Verbindung zur Binance API...");
        boolean verbindungOk = priceFetcher.testConnection();

        if (verbindungOk) {
            System.out.println("✅ Verbindung erfolgreich - Bot bereit!");
        } else {
            System.out.println("❌ Verbindung fehlgeschlagen - Prüfen Sie Ihre Internetverbindung");
        }

        return verbindungOk;
    }
}
