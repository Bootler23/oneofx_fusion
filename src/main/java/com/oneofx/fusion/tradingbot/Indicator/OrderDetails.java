package com.oneofx.fusion.tradingbot.Indicator;

public class OrderDetails {
    private String buyOrderId;
    private String quantity;
    private String currency;

    public OrderDetails(String buyOrderId, String quantity, String currency) {
        this.buyOrderId = buyOrderId;
        this.quantity = quantity;
        this.currency = currency;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getQuantity() {
        return quantity;
    }

    public String getCurrency() {
        return currency;
    }
}