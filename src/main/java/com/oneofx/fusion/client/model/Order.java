package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {
    private String id;
    private String pair;
    private OrderSide side;
    private OrderType type;
    private OrderStatus status;
    private String pricedIn;
    private String quantity;
    private String amount;
    private String limitPrice;
    private String triggerPrice;
    private String filledQuantity;
    private String filledAmount;
    private String filledPercentage;
    private String filledAveragePrice;
    private Fee fee;
    private TimeInForce timeInForce;
    private String updatedAt;
    private String createdAt;
    private String executedAt;
    private String endTime;

    public String getOrderId() { return id; }
    @JsonProperty("id") public void setOrderId(String id) { this.id = id; }
    public String getSymbol() { return pair; }
    @JsonProperty("pair") public void setSymbol(String pair) { this.pair = pair; }
    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getPricedIn() { return pricedIn; }
    public void setPricedIn(String pricedIn) { this.pricedIn = pricedIn; }
    public String getOrigQty() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getPrice() { return nonNull(filledAveragePrice, limitPrice, "0"); }
    public void setLimitPrice(String limitPrice) { this.limitPrice = limitPrice; }
    public String getStopPrice() { return nonNull(triggerPrice, "0"); }
    public void setTriggerPrice(String triggerPrice) { this.triggerPrice = triggerPrice; }
    public String getExecutedQty() { return nonNull(filledQuantity, "0"); }
    public void setFilledQuantity(String filledQuantity) { this.filledQuantity = filledQuantity; }
    public String getCummulativeQuoteQty() { return nonNull(filledAmount, "0"); }
    public void setFilledAmount(String filledAmount) { this.filledAmount = filledAmount; }
    public String getFilledPercentage() { return filledPercentage; }
    public void setFilledPercentage(String filledPercentage) { this.filledPercentage = filledPercentage; }
    public String getFilledAveragePrice() { return filledAveragePrice; }
    public void setFilledAveragePrice(String filledAveragePrice) { this.filledAveragePrice = filledAveragePrice; }
    public Fee getFee() { return fee; }
    public void setFee(Fee fee) { this.fee = fee; }
    public TimeInForce getTimeInForce() { return timeInForce; }
    public void setTimeInForce(TimeInForce timeInForce) { this.timeInForce = timeInForce; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getExecutedAt() { return executedAt; }
    public void setExecutedAt(String executedAt) { this.executedAt = executedAt; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    private static String nonNull(String... values) {
        for (String value : values) if (value != null) return value;
        return null;
    }
}
