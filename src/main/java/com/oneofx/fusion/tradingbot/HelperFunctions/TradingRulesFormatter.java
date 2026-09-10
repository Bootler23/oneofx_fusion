package com.oneofx.fusion.tradingbot.HelperFunctions;

import com.oneofx.fusion.tradingbot.SQL_Database.TradingRulesSQL;
import com.oneofx.fusion.tradingbot.domain.TradingRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.oneofx.fusion.client.model.OrderType;

/**
 * Formatter-Klasse für die korrekte Formatierung von Preisen und Quantities
 * basierend auf Bitpanda-Fusion-Trading-Regeln.
 * 
 * Diese Klasse ersetzt die hardcodierten Switch-Cases in RoundCurrency.java
 * durch dynamische, von Binance abgerufene Formatierungs-Regeln.
 * 
 * Verwendung:
 * - formatPrice(symbol, price) → Formatiert Preis nach PRICE_FILTER
 * - formatQuantity(symbol, quantity) → Formatiert Quantity nach LOT_SIZE
 * - formatOrderPrice(symbol, price) → String-Formatierung für Orders
 * - formatOrderQuantity(symbol, quantity) → String-Formatierung für Orders
 * 
 * Alle Methoden prüfen automatisch die Trading-Regeln aus der Datenbank
 * und verwenden Fallback-Werte falls keine Regeln gefunden werden.
 * 
 * @author Trading Bot
 * @version 1.0
 * @since 2026-02-26
 */
public class TradingRulesFormatter {

    // ========== Fallback-Werte (wenn keine Regeln in DB) ==========
    
    private static final int DEFAULT_PRICE_DECIMALS = 2;
    private static final int DEFAULT_QUANTITY_DECIMALS = 3;

    // ========== Preis-Formatierung ==========

    /**
     * Formatiert einen Preis nach den Trading-Regeln des Symbols.
     * 
     * Rundet auf die korrekte Anzahl von Dezimalstellen gemäß tickSize.
     * 
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @param price Der zu formatierende Preis
     * @return Formatierter Preis als BigDecimal
     */
    public static BigDecimal formatPrice(String symbol, BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }

        TradingRules rules = getTradingRules(symbol);
        if (rules == null) {
            return formatPrice(price, DEFAULT_PRICE_DECIMALS);
        }

        return quantize(price, rules.getTickSize(), RoundingMode.HALF_UP);
    }

    /**
     * Formatiert einen Preis als double.
     * 
     * @param symbol Währungspaar
     * @param price Der zu formatierende Preis (double)
     * @return Formatierter Preis als double
     */
    public static double formatPrice(String symbol, double price) {
        BigDecimal formatted = formatPrice(symbol, BigDecimal.valueOf(price));
        return formatted.doubleValue();
    }

    /**
     * Formatiert einen Preis als String für Binance Orders.
     * 
     * Verwendet Punkt als Dezimaltrennzeichen (US-Format).
     * 
     * @param symbol Währungspaar
     * @param price Der zu formatierende Preis
     * @return Formatierter Preis als String (z.B. "123.45")
     */
    public static String formatOrderPrice(String symbol, double price) {
        return formatPrice(symbol, BigDecimal.valueOf(price)).toPlainString();
    }

    /**
     * Formatiert einen Preis als String (BigDecimal).
     */
    public static String formatOrderPrice(String symbol, BigDecimal price) {
        if (price == null) {
            return "0.00";
        }
        return formatOrderPrice(symbol, price.doubleValue());
    }

    // ========== Quantity-Formatierung ==========

    /**
     * Formatiert eine Quantity nach den Trading-Regeln des Symbols.
     * 
     * Rundet auf die korrekte Anzahl von Dezimalstellen gemäß stepSize.
     * 
     * @param symbol Währungspaar (z.B. "LTCEUR")
     * @param quantity Die zu formatierende Quantity
     * @return Formatierte Quantity als BigDecimal
     */
    public static BigDecimal formatQuantity(String symbol, BigDecimal quantity) {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }

        TradingRules rules = getTradingRules(symbol);
        if (rules == null) {
            return formatPrice(quantity, DEFAULT_QUANTITY_DECIMALS);
        }

        return quantize(quantity, rules.getStepSize(), RoundingMode.DOWN);
    }

    /**
     * Formatiert eine Quantity als double.
     * 
     * @param symbol Währungspaar
     * @param quantity Die zu formatierende Quantity (double)
     * @return Formatierte Quantity als double
     */
    public static double formatQuantity(String symbol, double quantity) {
        BigDecimal formatted = formatQuantity(symbol, BigDecimal.valueOf(quantity));
        return formatted.doubleValue();
    }

    /**
     * Formatiert eine Quantity als String für Binance Orders.
     * 
     * Verwendet Punkt als Dezimaltrennzeichen (US-Format).
     * 
     * @param symbol Währungspaar
     * @param quantity Die zu formatierende Quantity
     * @return Formatierte Quantity als String (z.B. "1.234")
     */
    public static String formatOrderQuantity(String symbol, double quantity) {
        return formatQuantity(symbol, BigDecimal.valueOf(quantity)).toPlainString();
    }

    /**
     * Formatiert eine Quantity als String (BigDecimal).
     */
    public static String formatOrderQuantity(String symbol, BigDecimal quantity) {
        if (quantity == null) {
            return "0.000";
        }
        return formatOrderQuantity(symbol, quantity.doubleValue());
    }

    // ========== Berechnung mit Formatierung ==========

    /**
     * Berechnet die Quantity aus Betrag und Preis und formatiert sie korrekt.
     * 
     * Formel: quantity = amount / price
     * 
     * @param symbol Währungspaar
     * @param buyAmount Kaufbetrag in Quote-Währung (z.B. EUR)
     * @param currentPrice Aktueller Preis
     * @return Formatierte Quantity als String für Order
     */
    public static String calculateAndFormatQuantity(String symbol, double buyAmount, double currentPrice) {
        if (currentPrice <= 0) {
            return "0.000";
        }

        double quantity = buyAmount / currentPrice;
        return formatOrderQuantity(symbol, quantity);
    }

    /**
     * Berechnet die Quantity aus Betrag und Preis (BigDecimal).
     */
    public static String calculateAndFormatQuantity(String symbol, BigDecimal buyAmount, BigDecimal currentPrice) {
        if (buyAmount == null || currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.000";
        }

        BigDecimal quantity = buyAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
        return formatOrderQuantity(symbol, quantity);
    }

    // ========== Validierungs-Methoden ==========

    /**
     * Validiert, ob eine Quantity die Mindestmenge erfüllt.
     */
    public static boolean isQuantityValid(String symbol, BigDecimal quantity) {
        TradingRules rules = getTradingRules(symbol);
        return rules != null && rules.isQuantityValid(quantity);
    }

    /**
     * Validiert Quantity sowie minimalen und maximalen Orderbetrag von Fusion.
     */
    public static boolean isOrderValid(String symbol, BigDecimal price, BigDecimal quantity) {
        TradingRules rules = getTradingRules(symbol);
        return rules != null && rules.isOrderValid(price, quantity);
    }

    // ========== Hilfsmethoden ==========

    /**
     * Ruft Trading-Regeln aus der Datenbank ab.
     * 
     * @param symbol Währungspaar
     * @return TradingRules oder null wenn nicht gefunden
     */
    private static TradingRules getTradingRules(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        TradingRules rules = TradingRulesSQL.getTradingRules(symbol);
        
        return rules;
    }

    /** Formatiert den Fallback-Wert mit einer festen Anzahl Dezimalstellen. */
    private static BigDecimal formatPrice(BigDecimal value, int decimals) {
        return value.setScale(decimals, RoundingMode.HALF_UP);
    }

    public static boolean supportsOrderType(String symbol, OrderType type) {
        TradingRules rules=getTradingRules(symbol);
        return rules!=null && type!=null && rules.supports(type);
    }

    private static BigDecimal quantize(BigDecimal value, BigDecimal increment, RoundingMode mode) {
        if (increment == null || increment.signum() <= 0) return value;
        BigDecimal steps = value.divide(increment, 0, mode);
        return steps.multiply(increment).setScale(increment.scale(), RoundingMode.UNNECESSARY);
    }

    // ========== Info-Methoden ==========

    /**
     * Gibt die Anzahl der Preis-Dezimalstellen für ein Symbol zurück.
     */
    public static int getPriceDecimals(String symbol) {
        TradingRules rules = getTradingRules(symbol);
        return rules != null ? rules.getPriceDecimals() : DEFAULT_PRICE_DECIMALS;
    }

    /**
     * Gibt die Anzahl der Quantity-Dezimalstellen für ein Symbol zurück.
     */
    public static int getQuantityDecimals(String symbol) {
        TradingRules rules = getTradingRules(symbol);
        return rules != null ? rules.getQuantityDecimals() : DEFAULT_QUANTITY_DECIMALS;
    }

    /**
     * Gibt Debug-Informationen über die Formatierung aus.
     */
    public static void printFormattingInfo(String symbol) {
        TradingRules rules = getTradingRules(symbol);
        if (rules == null) {
            System.out.println("Keine Trading-Regeln für " + symbol + " gefunden");
            return;
        }
        System.out.println("=== Trading-Regeln für " + symbol + " ===");
        System.out.println("  tickSize : " + rules.getTickSize() + " (" + rules.getPriceDecimals() + " Dezimalstellen)");
        System.out.println("  stepSize : " + rules.getStepSize() + " (" + rules.getQuantityDecimals() + " Dezimalstellen)");
        System.out.println("  minQty   : " + rules.getMinQty());
    }
}
