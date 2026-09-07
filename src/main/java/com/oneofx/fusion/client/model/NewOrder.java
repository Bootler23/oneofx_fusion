package com.oneofx.fusion.client.model;

import java.math.BigDecimal;

public class NewOrder {
    private String pair;
    private OrderSide side;
    private OrderType type;
    private TimeInForce timeInForce;
    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal limitPrice;
    private BigDecimal triggerPrice;
    private String endTime;

    public NewOrder(String pair, OrderSide side, OrderType type, TimeInForce timeInForce,
                    String quantity, String limitPrice) {
        this.pair = FusionSymbol.normalizePair(pair);
        this.side = side;
        this.type = type;
        this.timeInForce = timeInForce;
        this.quantity = decimal(quantity);
        this.limitPrice = decimal(limitPrice);
    }

    public static NewOrder marketBuy(String pair, String quantity) {
        return new NewOrder(pair, OrderSide.BUY, OrderType.MARKET, TimeInForce.IOC, quantity, null);
    }

    public static NewOrder marketSell(String pair, String quantity) {
        return new NewOrder(pair, OrderSide.SELL, OrderType.MARKET, TimeInForce.IOC, quantity, null);
    }

    public NewOrder triggerPrice(String triggerPrice) {
        this.triggerPrice = decimal(triggerPrice);
        return this;
    }

    public NewOrder amount(String amount) {
        this.amount = decimal(amount);
        this.quantity = null;
        return this;
    }

    public NewOrder endTime(String endTime) {
        this.endTime = endTime;
        return this;
    }

    public String getPair() { return pair; }
    public OrderSide getSide() { return side; }
    public OrderType getType() { return type; }
    public TimeInForce getTimeInForce() { return timeInForce; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public BigDecimal getTriggerPrice() { return triggerPrice; }
    public String getEndTime() { return endTime; }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
