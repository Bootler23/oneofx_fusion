package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.tradingbot.Indicator.BollingerBands;
import com.binance.api.tradingbot.Indicator.BollingerBands.BollingerBandsResult;

public class BollingerBandsPrint {    
   
    public static void printBollingerBands(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            BollingerBandsResult bb = BollingerBands.getBollingerBands(client, symbol, interval);
            
            System.out.println("========================================");
            System.out.println("Bollinger Bands - " + symbol);
            System.out.println("Zeiteinheit: " + interval);
            System.out.println("========================================");
            System.out.println("Upper Band:    " + bb.getUpperBand());
            System.out.println("Middle Band:   " + bb.getMiddleBand());
            System.out.println("Lower Band:    " + bb.getLowerBand());
            System.out.println("----------------------------------------");
            System.out.println("Current Price: " + bb.getCurrentPrice());
            System.out.println("Bandwidth:     " + String.format("%.4f", bb.getBandwidth()));
            System.out.println("%B:            " + String.format("%.2f", bb.getPercentB()));
            System.out.println("========================================");
            
            // Signale
            if (bb.isPriceBelowLowerBand()) {
                System.out.println(">> KAUFSIGNAL: Preis UNTER unterem Band");
            } else if (bb.isPriceAboveUpperBand()) {
                System.out.println(">> VERKAUFSSIGNAL: Preis ÜBER oberem Band");
            } else if (bb.isPriceNearLowerBand()) {
                System.out.println(">> Info: Preis nähert sich unterem Band");
            } else if (bb.isPriceNearUpperBand()) {
                System.out.println(">> Info: Preis nähert sich oberem Band");
            } else {
                System.out.println(">> Preis im Normalbereich");
            }
            
            if (bb.isSqueeze()) {
                System.out.println(">> SQUEEZE: Niedrige Volatilität!");
            }
            
            System.out.println("========================================\n");
            
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Bollinger-Bänder: " + e.getMessage());
            e.printStackTrace();
        }
    }    
    
    public static void printBollingerBandsCompact(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            BollingerBandsResult bb = BollingerBands.getBollingerBands(client, symbol, interval);
            
            System.out.printf("[BB %s] U:%.4f M:%.4f L:%.4f | Price:%.4f | %%B:%.2f | BW:%.4f%n",
                            symbol,
                            bb.getUpperBand(),
                            bb.getMiddleBand(),
                            bb.getLowerBand(),
                            bb.getCurrentPrice(),
                            bb.getPercentB(),
                            bb.getBandwidth());
            
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Bollinger-Bänder: " + e.getMessage());
        }
    }    
   
    public static double[] getBollingerBandsValues(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
        try {
            BollingerBandsResult bb = BollingerBands.getBollingerBands(client, symbol, interval);
            return new double[] {
                bb.getUpperBand(),
                bb.getMiddleBand(),
                bb.getLowerBand()
            };
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Bollinger-Bänder: " + e.getMessage());
            return new double[] {0, 0, 0};
        }
    }
}
