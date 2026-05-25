package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.PositionDAO;
import java.util.List;

public class MergePosition {

    private static final PositionDAO positionDAO = new PositionDAO();

    public static void mergeTwoPositions(String currency, List<Double> LivePrice) {

        List<String> dataRecords = positionDAO.getTwoPositions(currency);

        double currentPrice = LivePrice.get(0);
        double price1 = 0.0;
        double price2 = 0.0;
        int foundCount = 0;

        // Finde die 2 höchsten Preise, die 7% vom aktuellen Preis entfernt sind
        for (String record : dataRecords) { 

            String[] part = record.split(",");

            double buyPrice = Double.valueOf(part[3]);
            
            if (currentPrice < (buyPrice * 0.93)) {
                if (buyPrice > price1) {
                    price2 = price1;
                    price1 = buyPrice;
                    foundCount = foundCount + 1;
                } else if (buyPrice > price2) {
                    price2 = buyPrice;
                    foundCount = foundCount + 1;
                }
            }
        }

        if (foundCount >= 2) {
            System.out.println("Gefundene 2 Positionen für Merge:");
            System.out.println("Position 1 - Price: " + price1);
            System.out.println("Position 2 - Price: " + price2);
            foundTwoPositions(price1, price2);
        } else {
            System.out.println("Weniger als 2 Positionen gefunden, die 7% vom Preis (" + currentPrice + ") entfernt sind.");
        }

    }

    private static void foundTwoPositions(double value1, double value2) {
        // Implementiere die Logik, um die beiden Positionen zu finden
    }
}
