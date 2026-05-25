package com.binance.api.tradingbot.domain;

/**
 * Domain-Objekt für die HIST-Tabelle.
 * Erstellt über Builder-Pattern: new HistoryPosition.Builder(currency, buyOrderId).buyPrice(p).build()
 */
public class HistoryPosition {

    private final String currency;
    private final String buyOrderId;
    private Double quantity;
    private Double buyAmount;
    private Double origPrice;
    private Double buyPrice;
    private Double sellPrice;
    private String sellOrderId;
    private Double sellAmount;
    private String buyDate;
    private String buyTime;
    private String sellDate;
    private String sellTime;
    private Double fee;
    private Double tax;
    private Double gewinn;
    private Double gewinnAfterTax;
    private Double lossAfterTax;
    private Double profit;
    private Double split;
    private Integer status;
    private String statusCode;
    private Double buyFee;
    private Double sellFee;
    private Double balanceAtBuy;
    private Double assetAtBuy;
    private Double balanceToAssetAtBuy;
    private Integer posCount;
    private Double x;

    private HistoryPosition(Builder builder) {
        this.currency = builder.currency;
        this.buyOrderId = builder.buyOrderId;
        this.quantity = builder.quantity;
        this.buyAmount = builder.buyAmount;
        this.origPrice = builder.origPrice;
        this.buyPrice = builder.buyPrice;
        this.sellPrice = builder.sellPrice;
        this.sellOrderId = builder.sellOrderId;
        this.sellAmount = builder.sellAmount;
        this.buyDate = builder.buyDate;
        this.buyTime = builder.buyTime;
        this.sellDate = builder.sellDate;
        this.sellTime = builder.sellTime;
        this.fee = builder.fee;
        this.tax = builder.tax;
        this.gewinn = builder.gewinn;
        this.gewinnAfterTax = builder.gewinnAfterTax;
        this.lossAfterTax = builder.lossAfterTax;
        this.profit = builder.profit;
        this.split = builder.split;
        this.status = builder.status;
        this.statusCode = builder.statusCode;
        this.buyFee = builder.buyFee;
        this.sellFee = builder.sellFee;
        this.balanceAtBuy = builder.balanceAtBuy;
        this.assetAtBuy = builder.assetAtBuy;
        this.balanceToAssetAtBuy = builder.balanceToAssetAtBuy;
        this.posCount = builder.posCount;
        this.x = builder.x;
    }

    public String getCurrency() { return currency; }
    public String getBuyOrderId() { return buyOrderId; }
    public Double getQuantity() { return quantity; }
    public Double getBuyAmount() { return buyAmount; }
    public Double getOrigPrice() { return origPrice; }
    public Double getBuyPrice() { return buyPrice; }
    public Double getSellPrice() { return sellPrice; }
    public String getSellOrderId() { return sellOrderId; }
    public Double getSellAmount() { return sellAmount; }
    public String getBuyDate() { return buyDate; }
    public String getBuyTime() { return buyTime; }
    public String getSellDate() { return sellDate; }
    public String getSellTime() { return sellTime; }
    public Double getFee() { return fee; }
    public Double getTax() { return tax; }
    public Double getGewinn() { return gewinn; }
    public Double getGewinnAfterTax() { return gewinnAfterTax; }
    public Double getLossAfterTax() { return lossAfterTax; }
    public Double getProfit() { return profit; }
    public Double getSplit() { return split; }
    public Integer getStatus() { return status; }
    public String getStatusCode() { return statusCode; }
    public Double getBuyFee() { return buyFee; }
    public Double getSellFee() { return sellFee; }
    public Double getBalanceAtBuy() { return balanceAtBuy; }
    public Double getAssetAtBuy() { return assetAtBuy; }
    public Double getBalanceToAssetAtBuy() { return balanceToAssetAtBuy; }
    public Integer getPosCount() { return posCount; }
    public Double getX() { return x; }

    public static class Builder {
        private final String currency;
        private final String buyOrderId;
        private Double quantity;
        private Double buyAmount;
        private Double origPrice;
        private Double buyPrice;
        private Double sellPrice;
        private String sellOrderId;
        private Double sellAmount;
        private String buyDate;
        private String buyTime;
        private String sellDate;
        private String sellTime;
        private Double fee;
        private Double tax;
        private Double gewinn;
        private Double gewinnAfterTax;
        private Double lossAfterTax;
        private Double profit;
        private Double split;
        private Integer status;
        private String statusCode;
        private Double buyFee;
        private Double sellFee;
        private Double balanceAtBuy;
        private Double assetAtBuy;
        private Double balanceToAssetAtBuy;
        private Integer posCount;
        private Double x;

        public Builder(String currency, String buyOrderId) {
            this.currency = currency;
            this.buyOrderId = buyOrderId;
        }

        public Builder quantity(Double quantity) { this.quantity = quantity; return this; }
        public Builder buyAmount(Double buyAmount) { this.buyAmount = buyAmount; return this; }
        public Builder origPrice(Double origPrice) { this.origPrice = origPrice; return this; }
        public Builder buyPrice(Double buyPrice) { this.buyPrice = buyPrice; return this; }
        public Builder sellPrice(Double sellPrice) { this.sellPrice = sellPrice; return this; }
        public Builder sellOrderId(String sellOrderId) { this.sellOrderId = sellOrderId; return this; }
        public Builder sellAmount(Double sellAmount) { this.sellAmount = sellAmount; return this; }
        public Builder buyDate(String buyDate) { this.buyDate = buyDate; return this; }
        public Builder buyTime(String buyTime) { this.buyTime = buyTime; return this; }
        public Builder sellDate(String sellDate) { this.sellDate = sellDate; return this; }
        public Builder sellTime(String sellTime) { this.sellTime = sellTime; return this; }
        public Builder fee(Double fee) { this.fee = fee; return this; }
        public Builder tax(Double tax) { this.tax = tax; return this; }
        public Builder gewinn(Double gewinn) { this.gewinn = gewinn; return this; }
        public Builder gewinnAfterTax(Double gewinnAfterTax) { this.gewinnAfterTax = gewinnAfterTax; return this; }
        public Builder lossAfterTax(Double lossAfterTax) { this.lossAfterTax = lossAfterTax; return this; }
        public Builder profit(Double profit) { this.profit = profit; return this; }
        public Builder split(Double split) { this.split = split; return this; }
        public Builder status(Integer status) { this.status = status; return this; }
        public Builder statusCode(String statusCode) { this.statusCode = statusCode; return this; }
        public Builder buyFee(Double buyFee) { this.buyFee = buyFee; return this; }
        public Builder sellFee(Double sellFee) { this.sellFee = sellFee; return this; }
        public Builder balanceAtBuy(Double balanceAtBuy) { this.balanceAtBuy = balanceAtBuy; return this; }
        public Builder assetAtBuy(Double assetAtBuy) { this.assetAtBuy = assetAtBuy; return this; }
        public Builder balanceToAssetAtBuy(Double balanceToAssetAtBuy) { this.balanceToAssetAtBuy = balanceToAssetAtBuy; return this; }
        public Builder posCount(Integer posCount) { this.posCount = posCount; return this; }
        public Builder x(Double x) { this.x = x; return this; }

        public HistoryPosition build() {
            return new HistoryPosition(this);
        }
    }
}
