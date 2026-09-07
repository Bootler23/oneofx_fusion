package com.oneofx.fusion.tradingbot.HelperFunctions;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.OrderBook;
import com.oneofx.fusion.client.model.OrderBookEntry;

public class Slippage {

    /**
     * Prüft ob ein Verkauf profitabel ist (nach Orderbuch-Slippage).
     * 
     * @param currency         Das Währungspaar (z.B. "LTCEUR")
     * @param client           Binance Client
     * @param quantity         Zu verkaufende Menge
     * @param buyPrice         Dein Kaufpreis
     * @param minProfitPercent Mindestgewinn in % (z.B. 0.5)
     * @return true wenn Verkauf profitabel, false wenn nicht
     */
    public static boolean isProfitableAfterSlippage(String currency, FusionApiClient client,
            double quantity, double buyPrice, double minProfitPercent) {

        try {
            OrderBook orderBook = client.getOrderBook(currency, 20);

            // Berechne tatsächlichen Verkaufspreis aus Orderbuch
            double totalValue = 0;
            double filledQty = 0;
            double remainingQty = quantity;

            for (OrderBookEntry bid : orderBook.getBids()) {
                double bidPrice = Double.parseDouble(bid.getPrice());
                double bidQty = Double.parseDouble(bid.getQty());

                double fillQty = Math.min(remainingQty, bidQty);
                totalValue += fillQty * bidPrice;
                filledQty += fillQty;
                remainingQty -= fillQty;

                if (remainingQty <= 0)
                    break;
            }

            // Nicht genug Volumen im Orderbuch?
            if (remainingQty > 0) {
                System.err.println("⚠ Nicht genug Volumen! Nur " + filledQty + " von " + quantity + " verfügbar.");
                return false;
            }

            // Erwarteter Durchschnittspreis
            double expectedPrice = totalValue / filledQty;

            // Gewinn berechnen
            double profitPercent = ((expectedPrice - buyPrice) / buyPrice) * 100;

            // System.out.println("═══════════════════════════════════════");
            // System.out.println("ORDERBUCH-CHECK");
            // System.out.println("═══════════════════════════════════════");
            // System.out.println("BuyPrice:        " + String.format("%.2f", buyPrice) + " EUR");
            // System.out.println("Erwarteter Preis:" + String.format("%.2f", expectedPrice) + " EUR");
            // System.out.println("Erwarteter Gewinn: " + String.format("%.2f", profitPercent) + "%");
            // System.out.println("Mindest-Gewinn:    " + minProfitPercent + "%");
            // System.out.println("═══════════════════════════════════════");

            if (profitPercent < minProfitPercent) {
                System.err.println("✗ Nach Slippage nur " + String.format("%.2f", profitPercent) +
                        "% Gewinn → ABBRUCH!");
                return false;
            }

            System.out.println("✓ Verkauf profitabel! " + String.format("%.2f", profitPercent) + "% >= " + minProfitPercent + "%");
            return true;

        } catch (Exception e) {
            System.err.println("Orderbuch-Check fehlgeschlagen: " + e.getMessage());
            return false; // Im Zweifel NICHT verkaufen
        }
    }
}
