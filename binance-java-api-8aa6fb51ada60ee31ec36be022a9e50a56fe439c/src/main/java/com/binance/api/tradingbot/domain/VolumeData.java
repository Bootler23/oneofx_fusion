package com.binance.api.tradingbot.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class VolumeData {

    private String symbol;                    // Währungspaar (z.B. "LTCEUR")
    private BigDecimal volumeBase;            // Volumen in Basis-Währung (z.B. LTC)
    private BigDecimal volumeQuote;           // Volumen in Quote-Währung (z.B. EUR) - berechnet
    private BigDecimal lastPrice;             // Aktueller Preis
    private BigDecimal priceChange;           // Absolute Preisänderung in 24h
    private BigDecimal priceChangePercent;    // Prozentuale Preisänderung in 24h
    private BigDecimal highPrice;             // Höchstpreis in 24h
    private BigDecimal lowPrice;              // Tiefstpreis in 24h
    private BigDecimal weightedAvgPrice;      // Gewichteter Durchschnittspreis
    private long tradeCount;                  // Anzahl der Trades in 24h
    private LocalDateTime openTime;           // Beginn des 24h-Fensters
    private LocalDateTime closeTime;          // Ende des 24h-Fensters
    private LocalDateTime fetchedAt;          // Zeitpunkt der Abfrage

        public VolumeData() {
        this.volumeBase = BigDecimal.ZERO;
        this.volumeQuote = BigDecimal.ZERO;
        this.lastPrice = BigDecimal.ZERO;
        this.priceChange = BigDecimal.ZERO;
        this.priceChangePercent = BigDecimal.ZERO;
        this.tradeCount = 0;
        this.fetchedAt = LocalDateTime.now();
    }
   
    public VolumeData(String symbol, BigDecimal volumeBase, BigDecimal lastPrice) {
        this.symbol = symbol;
        this.volumeBase = volumeBase;
        this.lastPrice = lastPrice;
        // EUR-Volumen berechnen: volumeBase * lastPrice
        this.volumeQuote = volumeBase.multiply(lastPrice);
        this.fetchedAt = LocalDateTime.now();
    }
    
    public void calculateQuoteVolume() {
        if (volumeBase != null && lastPrice != null) {
            this.volumeQuote = volumeBase.multiply(lastPrice);
        } else {
            this.volumeQuote = BigDecimal.ZERO;
        }
    }
   
    public boolean hasMinimumVolume(BigDecimal minVolume) {
        if (volumeQuote == null || minVolume == null) {
            return false;
        }
        return volumeQuote.compareTo(minVolume) >= 0;
    }

    /**
     * Gibt an, ob die Preisänderung positiv ist (bullish).
     * 
     * @return true wenn Preisänderung > 0
     */
    public boolean isPriceIncreasing() {
        return priceChange != null && priceChange.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Gibt an, ob das Volumen als gültig betrachtet werden kann.
     * 
     * @return true wenn Volumen > 0 und Preis > 0
     */
    public boolean isValid() {
        return volumeBase != null && volumeBase.compareTo(BigDecimal.ZERO) > 0
                && lastPrice != null && lastPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    // ========== Getter und Setter ==========

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getVolumeBase() {
        return volumeBase;
    }

    public void setVolumeBase(BigDecimal volumeBase) {
        this.volumeBase = volumeBase;
        calculateQuoteVolume();
    }

    public BigDecimal getVolumeQuote() {
        return volumeQuote;
    }

    public void setVolumeQuote(BigDecimal volumeQuote) {
        this.volumeQuote = volumeQuote;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
        calculateQuoteVolume();
    }

    public BigDecimal getPriceChange() {
        return priceChange;
    }

    public void setPriceChange(BigDecimal priceChange) {
        this.priceChange = priceChange;
    }

    public BigDecimal getPriceChangePercent() {
        return priceChangePercent;
    }

    public void setPriceChangePercent(BigDecimal priceChangePercent) {
        this.priceChangePercent = priceChangePercent;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getWeightedAvgPrice() {
        return weightedAvgPrice;
    }

    public void setWeightedAvgPrice(BigDecimal weightedAvgPrice) {
        this.weightedAvgPrice = weightedAvgPrice;
    }

    public long getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(long tradeCount) {
        this.tradeCount = tradeCount;
    }

    public LocalDateTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long openTimeMillis) {
        this.openTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(openTimeMillis), ZoneId.systemDefault());
    }

    public void setOpenTime(LocalDateTime openTime) {
        this.openTime = openTime;
    }

    public LocalDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(long closeTimeMillis) {
        this.closeTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(closeTimeMillis), ZoneId.systemDefault());
    }

    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    // ========== toString für Debugging ==========

    @Override
    public String toString() {
        return "VolumeData{" +
                "symbol='" + symbol + '\'' +
                ", volumeBase=" + volumeBase +
                ", volumeQuote=" + volumeQuote +
                ", lastPrice=" + lastPrice +
                ", priceChangePercent=" + priceChangePercent +
                ", tradeCount=" + tradeCount +
                ", fetchedAt=" + fetchedAt +
                '}';
    }
}
