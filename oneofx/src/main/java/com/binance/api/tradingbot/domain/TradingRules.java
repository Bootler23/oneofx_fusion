package com.binance.api.tradingbot.domain;

import java.math.BigDecimal;

/**
 * Domain-Klasse für Trading-Regeln eines Währungspaares.
 *
 * Enthält die 3 relevanten Binance-Filter-Werte:
 * - tickSize  → Dezimalstellen für den Preis    (DB: tickSize)
 * - stepSize  → Dezimalstellen für die Quantity (DB: stepSize)
 * - minQty    → Mindest-Quantity pro Order      (DB: minQty)
 */
public class TradingRules {

    private String symbol;          // Währungspaar z.B. "LTCEUR" (DB: currency)
    private BigDecimal tickSize;    // Preis-Schrittgröße  z.B. 0.01  → 2 Dezimalstellen
    private BigDecimal stepSize;    // Mengen-Schrittgröße z.B. 0.001 → 3 Dezimalstellen
    private BigDecimal minQty;      // Mindest-Quantity    z.B. 0.001

    // Berechnete Werte (nicht in DB gespeichert)
    private int priceDecimals;
    private int quantityDecimals;

    // ========== Konstruktoren ==========

    public TradingRules() {
        this.priceDecimals = 2;
        this.quantityDecimals = 3;
    }

    public TradingRules(String symbol) {
        this();
        this.symbol = symbol;
    }

    // ========== Berechnungsmethode ==========

    /**
     * Berechnet Dezimalstellen aus einer Schrittgröße.
     * Beispiel: 0.01 → 2, 0.001 → 3, 1 → 0
     */
    public static int calculateDecimalPlaces(BigDecimal stepValue) {
        if (stepValue == null || stepValue.compareTo(BigDecimal.ZERO) <= 0) {
            return 2;
        }
        String plain = stepValue.stripTrailingZeros().toPlainString();
        int dotIndex = plain.indexOf('.');
        return dotIndex < 0 ? 0 : plain.length() - dotIndex - 1;
    }

    /**
     * Prüft ob eine Quantity die Mindestmenge erfüllt.
     */
    public boolean isQuantityValid(BigDecimal quantity) {
        if (quantity == null) return false;
        if (minQty != null && quantity.compareTo(minQty) < 0) return false;
        return true;
    }

    // ========== Getter & Setter ==========

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getTickSize() { return tickSize; }
    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
        if (tickSize != null) this.priceDecimals = calculateDecimalPlaces(tickSize);
    }

    public BigDecimal getStepSize() { return stepSize; }
    public void setStepSize(BigDecimal stepSize) {
        this.stepSize = stepSize;
        if (stepSize != null) this.quantityDecimals = calculateDecimalPlaces(stepSize);
    }

    public BigDecimal getMinQty() { return minQty; }
    public void setMinQty(BigDecimal minQty) { this.minQty = minQty; }

    public int getPriceDecimals() { return priceDecimals; }
    public int getQuantityDecimals() { return quantityDecimals; }

    @Override
    public String toString() {
        return "TradingRules{" +
                "symbol='" + symbol + '\'' +
                ", tickSize=" + tickSize + " (" + priceDecimals + " Dez.)" +
                ", stepSize=" + stepSize + " (" + quantityDecimals + " Dez.)" +
                ", minQty=" + minQty +
                '}';
    }
}
