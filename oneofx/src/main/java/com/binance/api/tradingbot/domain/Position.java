package com.binance.api.tradingbot.domain;

/**
 * Domain-Objekt für die positions-Tabelle.
 * Erstellt über Builder-Pattern: new Position.Builder(currency, buyOrderId).status(0).build()
 */
public class Position {

    private final String currency;
    private final String buyOrderId;
    private Double orderPrice;
    private Double origPrice;
    private Double quantity;
    private Double buyAmount;
    private Double buyPrice;
    private String buyDate;
    private String buyTime;
    private Integer status;
    private String statusCode;
    private Double peakPrice;
    private String tsl;
    private Double profit;

    private Position(Builder builder) {
        this.currency = builder.currency;
        this.buyOrderId = builder.buyOrderId;
        this.orderPrice = builder.orderPrice;
        this.origPrice = builder.origPrice;
        this.quantity = builder.quantity;
        this.buyAmount = builder.buyAmount;
        this.buyPrice = builder.buyPrice;
        this.buyDate = builder.buyDate;
        this.buyTime = builder.buyTime;
        this.status = builder.status;
        this.statusCode = builder.statusCode;
        this.peakPrice = builder.peakPrice;
        this.tsl = builder.tsl;
        this.profit = builder.profit;
    }

    public String getCurrency() { return currency; }
    public String getBuyOrderId() { return buyOrderId; }
    public Double getOrderPrice() { return orderPrice; }
    public Double getOrigPrice() { return origPrice; }
    public Double getQuantity() { return quantity; }
    public Double getBuyAmount() { return buyAmount; }
    public Double getBuyPrice() { return buyPrice; }
    public String getBuyDate() { return buyDate; }
    public String getBuyTime() { return buyTime; }
    public Integer getStatus() { return status; }
    public String getStatusCode() { return statusCode; }
    public Double getPeakPrice() { return peakPrice; }
    public String getTsl() { return tsl; }
    public Double getProfit() { return profit; }

    public static class Builder {
        private final String currency;
        private final String buyOrderId;
        private Double orderPrice;
        private Double origPrice;
        private Double quantity;
        private Double buyAmount;
        private Double buyPrice;
        private String buyDate;
        private String buyTime;
        private Integer status;
        private String statusCode;
        private Double peakPrice;
        private String tsl;
        private Double profit;

        public Builder(String currency, String buyOrderId) {
            this.currency = currency;
            this.buyOrderId = buyOrderId;
        }

        public Builder orderPrice(Double orderPrice) { this.orderPrice = orderPrice; return this; }
        public Builder origPrice(Double origPrice) { this.origPrice = origPrice; return this; }
        public Builder quantity(Double quantity) { this.quantity = quantity; return this; }
        public Builder buyAmount(Double buyAmount) { this.buyAmount = buyAmount; return this; }
        public Builder buyPrice(Double buyPrice) { this.buyPrice = buyPrice; return this; }
        public Builder buyDate(String buyDate) { this.buyDate = buyDate; return this; }
        public Builder buyTime(String buyTime) { this.buyTime = buyTime; return this; }
        public Builder status(Integer status) { this.status = status; return this; }
        public Builder statusCode(String statusCode) { this.statusCode = statusCode; return this; }
        public Builder peakPrice(Double peakPrice) { this.peakPrice = peakPrice; return this; }
        public Builder tsl(String tsl) { this.tsl = tsl; return this; }
        public Builder profit(Double profit) { this.profit = profit; return this; }

        public Position build() {
            return new Position(this);
        }
    }
}
