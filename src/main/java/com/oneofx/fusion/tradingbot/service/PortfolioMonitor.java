package com.oneofx.fusion.tradingbot.service;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oneofx.fusion.tradingbot.HelperFunctions.empty;
import com.oneofx.fusion.tradingbot.SQL_Database.PositionDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SETSQL;
import com.oneofx.fusion.tradingbot.Stream.PricePoller;

public class PortfolioMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioMonitor.class);
    private static final PositionDAO positionDAO = new PositionDAO();

    // Gecachte Gesamt-Werte – werden alle 60s aktualisiert, jede Sekunde angezeigt
    private static volatile int    cachedTotalPositions  = 0;
    private static volatile double cachedTotalPnLEur     = 0.0;
    private static volatile double cachedTotalAvgPnL     = 0.0;
    private static volatile String cachedCurrencyList    = "";

    /**
     * Gibt die gecachte Portfolio-Gesamtzusammenfassung auf einer einzigen Zeile aus.
     * Kein DB-Zugriff – sofort verfuegbar, fuer die sekuendliche Anzeige.
     */
    public static void printCachedTotalSummary() {
        String sign  = cachedTotalPnLEur >= 0 ? "+" : "";
        String pnlColor = cachedTotalPnLEur >= 0 ? "" : "";
        System.out.print("\r[GESAMT] " + cachedCurrencyList
                + " | Positionen: " + cachedTotalPositions
                + " | Avg PnL: " + String.format("%+.2f", cachedTotalAvgPnL) + "%"
                + " | PnL: " + sign + String.format("%.2f", cachedTotalPnLEur) + " EUR   ");
    }

    /**
     * Zeigt den Portfolio-Status fuer alle uebergebenen Waehrungspaare in einem Block an.
     * Der aktuelle Preis wird fuer jedes Paar direkt aus dem PricePoller gelesen.
     *
     * @param currencies Alle aktiven Waehrungspaare (z.B. ["LTCEUR", "ETHEUR", "BNBEUR"])
     * @param stream     Laufender PricePoller mit gecachten REST-Preisen
     */
    public static void showAllPortfolioStatus(String[] currencies, PricePoller stream) {
        int    totalPositions = 0;
        double totalPnLEur    = 0.0;
        double totalPnLPct    = 0.0;
        int    currencyCount  = 0;
        StringBuilder currencyList = new StringBuilder();

        System.out.println();
        System.out.println("===== PORTFOLIO STATUS =====" );
        for (String currency : currencies) {
            Double price = stream.getPrice(currency);
            if (price == null || price == 0.0) {
                logger.warn("[PORTFOLIO] Kein Live-Preis fuer {} verfuegbar - uebersprungen", currency);
                continue;
            }
            double[] result = calculateAndPrintPortfolio(currency, price);
            // result: [positionCount, totalPnLEur, avgPnLPercent]
            totalPositions += (int) result[0];
            totalPnLEur    += result[1];
            totalPnLPct    += result[2];
            currencyCount++;
            if (currencyList.length() > 0) currencyList.append("/");
            currencyList.append(currency.replace("EUR", ""));
        }

        double overallAvgPnL = currencyCount > 0 ? totalPnLPct / currencyCount : 0.0;
        System.out.println("----------------------------");
        System.out.println(String.format("[GESAMT]  Positionen: %d | Avg PnL: %+.2f%% | PnL: %+.2f EUR",
                totalPositions, overallAvgPnL, totalPnLEur));
        System.out.println("============================");

        // Cache fuer sekuendliche Anzeige aktualisieren
        cachedTotalPositions = totalPositions;
        cachedTotalPnLEur    = totalPnLEur;
        cachedTotalAvgPnL    = overallAvgPnL;
        cachedCurrencyList   = currencyList.toString();
    }

    /**
     * Zeigt den Portfolio-Status fuer ein einzelnes Waehrungspaar (Kompatibilitaets-Methode).
     *
     * @param currency     Das Waehrungspaar (z.B. "LTCEUR")
     * @param currentPrice Aktueller Marktpreis
     * @return Durchschnittlicher PnL in Prozent ueber alle offenen Positionen
     */
    public static double showPortfolioStatus(String currency, double currentPrice) {
        empty.Line();
        return calculateAndPrintPortfolio(currency, currentPrice)[2];
    }

    /**
     * Kernlogik: Liest Positionen aus der DB, berechnet PnL und gibt eine Zeile aus.
     *
     * @param currency     Das Waehrungspaar
     * @param currentPrice Aktueller Marktpreis
     * @return double[] { positionCount, totalPnLEur, avgPnLPercent }
     */
    private static double[] calculateAndPrintPortfolio(String currency, double currentPrice) {

        List<String> positions = positionDAO.getDataRecordsWhereStatusOneOrSeven(currency);

        if (positions.isEmpty()) {
            System.out.println("[PORTFOLIO] " + currency + ": Keine offenen Positionen");
            return new double[]{0, 0.0, 0.0};
        }

        double totalPnLPercent = 0.0;
        double totalPnLEur = 0.0;
        int positionCount = 0;

        for (String dataRecord : positions) {
            String[] parts = dataRecord.split(", ");

            // parts[3]=Qty, parts[4]=BuyAmount(EUR), parts[5]=BuyPrice
            if (parts.length < 6) {
                continue;
            }

            double buyPrice = Double.parseDouble(parts[5]);

            if (buyPrice <= 0) {
                continue;
            }

            // PnL in Prozent
            double pnlPercent = ((currentPrice - buyPrice) / buyPrice) * 100;
            totalPnLPercent += pnlPercent;

            // PnL in EUR: Qty x (aktuellerPreis - Kaufpreis)
            try {
                double qty = Double.parseDouble(parts[3]);
                totalPnLEur += qty * (currentPrice - buyPrice);
            } catch (NumberFormatException ignored) {}

            positionCount++;
        }

        double avgPnLPercent = positionCount > 0 ? totalPnLPercent / positionCount : 0.0;

        if (avgPnLPercent > SETSQL.getPnL_Reverense()) {
            SETSQL.setPnL_Reverense(avgPnLPercent);
        }

        System.out.println("[PORTFOLIO] " + currency + ": " + positionCount + " Positionen | Avg PnL: "
                + String.format("%+.2f", avgPnLPercent) + "% | PnL: "
                + String.format("%+.2f", totalPnLEur) + " EUR");

        return new double[]{positionCount, totalPnLEur, avgPnLPercent};
    }

    public static void showDetailedPortfolioStatus(String currency, double currentPrice) {
        
        List<String> positions = positionDAO.getDataRecordsWhereStatusOneOrSeven(currency);
        
        if (positions.isEmpty()) {
            empty.Line();
            System.out.println("[PORTFOLIO] Keine offenen Positionen fuer " + currency);
            return;
        }
        
        double totalPnLPercent = 0.0;
        int positionCount = 0;
        int profitablePositions = 0;
        int losingPositions = 0;
        double lowestBuyPrice = Double.MAX_VALUE;
        double highestBuyPrice = 0.0;
        
        for (String dataRecord : positions) {
            String[] parts = dataRecord.split(", ");
            
            if (parts.length < 6) {
                continue;
            }
            
            String buyPriceString = parts[5];
            double buyPrice = Double.valueOf(buyPriceString);
            
            if (buyPrice <= 0) {
                continue;
            }
            
            // Tracking
            if (buyPrice < lowestBuyPrice) lowestBuyPrice = buyPrice;
            if (buyPrice > highestBuyPrice) highestBuyPrice = buyPrice;
            
            // PnL pro Position
            double pnlPercent = ((currentPrice - buyPrice) / buyPrice) * 100;
            totalPnLPercent += pnlPercent;
            
            if (pnlPercent > 0) {
                profitablePositions++;
            } else {
                losingPositions++;
            }
            
            positionCount++;
        }
        
        double avgPnLPercent = positionCount > 0 ? totalPnLPercent / positionCount : 0.0;
        
        System.out.println("=== PORTFOLIO STATUS ===");
        System.out.println("Currency: " + currency);
        System.out.println("Aktueller Preis: EUR " + String.format("%.2f", currentPrice));
        System.out.println("Positionen: " + positionCount);
        System.out.println("  Im Gewinn: " + profitablePositions);
        System.out.println("  Im Verlust: " + losingPositions);
        System.out.println("Avg PnL: " + String.format("%.2f", avgPnLPercent) + "%");
        System.out.println("Preis-Range: EUR " + String.format("%.2f", lowestBuyPrice) + " - EUR " + String.format("%.2f", highestBuyPrice));
        System.out.println("========================");
    }
}
