package com.oneofx.fusion.tradingbot.domain;

import java.math.BigDecimal;

/**
 * Domain-Klasse für Trading-Regeln eines Währungspaares.
 *
 * Trading constraints returned by Bitpanda Fusion's /v1/pairs endpoint.
 */
public class TradingRules {

    private String symbol;          // Währungspaar z.B. "LTCEUR" (DB: currency)
    private BigDecimal tickSize;    // Preis-Schrittgröße  z.B. 0.01  → 2 Dezimalstellen
    private BigDecimal stepSize;    // Mengen-Schrittgröße z.B. 0.001 → 3 Dezimalstellen
    private BigDecimal minQty;      // Mindest-Quantity    z.B. 0.001
    private BigDecimal amountIncrement;
    private BigDecimal maxOrderSize;
    private BigDecimal minOrderAmount;
    private BigDecimal maxOrderAmount;

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
        if (quantity == null || quantity.signum() <= 0) return false;
        if (stepSize == null || stepSize.signum() <= 0) return false;
        if (minQty == null || minQty.signum() <= 0) return false;
        if (!isIncrementAligned(quantity, stepSize)) return false;
        if (minQty != null && quantity.compareTo(minQty) < 0) return false;
        if (maxOrderSize != null && maxOrderSize.signum() > 0 && quantity.compareTo(maxOrderSize) > 0) return false;
        return true;
    }

    public boolean isOrderValid(BigDecimal price, BigDecimal quantity) {
        if (!isQuantityValid(quantity) || price == null || price.signum() <= 0) return false;
        if (tickSize == null || tickSize.signum() <= 0 || !isIncrementAligned(price, tickSize)) return false;
        BigDecimal amount = price.multiply(quantity);
        if (minOrderAmount != null && amount.compareTo(minOrderAmount) < 0) return false;
        return maxOrderAmount == null || maxOrderAmount.signum() <= 0 || amount.compareTo(maxOrderAmount) <= 0;
    }

    private static boolean isIncrementAligned(BigDecimal value, BigDecimal increment) {
        return value.remainder(increment).compareTo(BigDecimal.ZERO) == 0;
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
    public BigDecimal getAmountIncrement() { return amountIncrement; }
    public void setAmountIncrement(BigDecimal amountIncrement) { this.amountIncrement = amountIncrement; }
    public BigDecimal getMaxOrderSize() { return maxOrderSize; }
    public void setMaxOrderSize(BigDecimal maxOrderSize) { this.maxOrderSize = maxOrderSize; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public BigDecimal getMaxOrderAmount() { return maxOrderAmount; }
    public void setMaxOrderAmount(BigDecimal maxOrderAmount) { this.maxOrderAmount = maxOrderAmount; }

    public int getPriceDecimals() { return priceDecimals; }
    public int getQuantityDecimals() { return quantityDecimals; }

    @Override
    public String toString() {
        return "TradingRules{" +
                "symbol='" + symbol + '\'' +
                ", tickSize=" + tickSize + " (" + priceDecimals + " Dez.)" +
                ", stepSize=" + stepSize + " (" + quantityDecimals + " Dez.)" +
                ", minQty=" + minQty +
                ", minOrderAmount=" + minOrderAmount +
                ", maxOrderAmount=" + maxOrderAmount +
                '}';
    }
}
