package com.oneofx.fusion.client.model;

import java.math.BigDecimal;

public class NewOrder {
    private String pair;
    private OrderSide side;
    private OrderType type;
    private TimeInForce timeInForce;
    private String quantity;
    private String amount;
    private String limitPrice;
    private String triggerPrice;
    private String endTime;

    public NewOrder(String pair, OrderSide side, OrderType type, TimeInForce timeInForce,
                    String quantity, String limitPrice) {
        this.pair = FusionSymbol.normalizePair(pair);
        this.side = side;
        this.type = type;
        this.timeInForce = timeInForce;
        this.quantity = numericString(quantity);
        this.limitPrice = numericString(limitPrice);
    }

    public static NewOrder marketBuy(String pair, String quantity) {
        return new NewOrder(pair, OrderSide.BUY, OrderType.MARKET, TimeInForce.IOC, quantity, null);
    }

    public static NewOrder marketSell(String pair, String quantity) {
        // FOK prevents an asynchronously processed market sell from leaving an
        // untracked remainder of the original position.
        return new NewOrder(pair, OrderSide.SELL, OrderType.MARKET, TimeInForce.FOK, quantity, null);
    }

    public NewOrder triggerPrice(String triggerPrice) {
        this.triggerPrice = numericString(triggerPrice);
        return this;
    }

    public NewOrder amount(String amount) {
        this.amount = numericString(amount);
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
    public String getQuantity() { return quantity; }
    public String getAmount() { return amount; }
    public String getLimitPrice() { return limitPrice; }
    public String getTriggerPrice() { return triggerPrice; }
    public String getEndTime() { return endTime; }

    public void validate() {
        if (pair == null || side == null || type == null) {
            throw new IllegalArgumentException("Fusion order requires pair, side and type");
        }
        if ((quantity == null) == (amount == null)) {
            throw new IllegalArgumentException("Fusion order requires exactly one of quantity or amount");
        }
        if (quantity != null && new BigDecimal(quantity).signum() <= 0
                || amount != null && new BigDecimal(amount).signum() <= 0) {
            throw new IllegalArgumentException("Fusion order size must be greater than zero");
        }
        if ((type == OrderType.LIMIT || type == OrderType.STOP_LIMIT) && limitPrice == null) {
            throw new IllegalArgumentException(type + " requires limitPrice");
        }
        if ((type == OrderType.STOP_LIMIT || type == OrderType.STOP_MARKET) && triggerPrice == null) {
            throw new IllegalArgumentException(type + " requires triggerPrice");
        }
        if (timeInForce == TimeInForce.GTD && (endTime == null || endTime.isBlank())) {
            throw new IllegalArgumentException("GTD requires endTime");
        }
    }

    private static String numericString(String value) {
        if (value == null) return null;
        new BigDecimal(value);
        return value;
    }
}
