package com.binance.api.tradingbot.service;

import java.util.List;
import java.util.ArrayList;
import com.binance.api.tradingbot.SQL_Database.POSSQL;

public class PortfolioMonitor {
       
    public static double showPortfolioStatus(String currency, double currentPrice) {
        
        List<String> positions = new ArrayList<>();
        POSSQL.getDataRecords_WhereStatusOneOrSeven(currency, positions);
        
        if (positions.isEmpty()) {
            System.out.println("[PORTFOLIO] Keine offenen Positionen fuer " + currency);
            return 0.0;
        }
        
        double totalPnLPercent = 0.0;
        int positionCount = 0;
        
        for (String dataRecord : positions) {
            String[] parts = dataRecord.split(", ");
            
            // parts[5] ist der BuyPrice laut Logik
            if (parts.length < 6) {
                continue;
            }
            
            String buyPriceString = parts[5];
            double buyPrice = Double.valueOf(buyPriceString);
            
            if (buyPrice <= 0) {
                continue;
            }
            
            // PnL pro Position in Prozent berechnen
            double pnlPercent = ((currentPrice - buyPrice) / buyPrice) * 100;
            totalPnLPercent += pnlPercent;
            positionCount++;
        }
        
        double avgPnLPercent = positionCount > 0 ? totalPnLPercent / positionCount : 0.0;
        
        System.out.println("[PORTFOLIO] " + currency + ": " + positionCount + " Positionen | Avg PnL: " 
                + String.format("%.2f", avgPnLPercent) + "%");  
           
        return avgPnLPercent;
    }       
   
    public static void showDetailedPortfolioStatus(String currency, double currentPrice) {
        
        List<String> positions = new ArrayList<>();
        POSSQL.getDataRecords_WhereStatusOneOrSeven(currency, positions);
        
        if (positions.isEmpty()) {
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
        System.out.println("Preis-Range: EUR " + String.format("%.2f", lowestBuyPrice) 
                + " - EUR " + String.format("%.2f", highestBuyPrice));
        System.out.println("========================");
    }
}
