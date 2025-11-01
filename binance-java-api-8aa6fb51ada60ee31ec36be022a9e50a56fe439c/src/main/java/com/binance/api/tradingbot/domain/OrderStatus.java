package com.binance.api.tradingbot.domain;

public enum OrderStatus {

    NEW(0),

    FILLED(1),

    FILLED_CHECKED(2),

    PARTIALLY_FILLED(5),

    CANCELLED(7);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
   
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unbekannter OrderStatus-Code: " + code);
    }
  
    public boolean isFilled() {
        return this == FILLED;
    }
   
    public boolean isOpen() {
        return this == NEW || this == PARTIALLY_FILLED;
    }
    
    public boolean isClosed() {
        return this == FILLED || this == CANCELLED;
    }
}
