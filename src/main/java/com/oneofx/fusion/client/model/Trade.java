package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {
    private String id;
    private String orderId;
    private String pair;
    private String side;
    private String price;

    private String quantity;

    private Fee fee;

    @JsonAlias({"total", "totalAmount"})
    private String total;

    private String executedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSymbol() { return pair; }
    public void setPair(String pair) { this.pair = pair; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getPrice() { return nonNull(price, "0"); }
    public void setPrice(String price) { this.price = price; }
    public String getQty() { return nonNull(quantity, "0"); }
    @JsonProperty("amount")
    @JsonAlias("quantity")
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getCommission() { return fee == null || fee.getAmount() == null ? "0" : fee.getAmount(); }
    public String getCommissionAsset() { return fee == null ? null : fee.getCurrency(); }
    public Fee getFee() { return fee; }
    public void setFee(Fee fee) { this.fee = fee; }
    public String getQuoteQty() { return nonNull(total, "0"); }
    public void setTotal(String total) { this.total = total; }
    public String getExecutedAt() { return executedAt; }
    public void setExecutedAt(String executedAt) { this.executedAt = executedAt; }

    private static String nonNull(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
