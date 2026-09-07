package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderSide {
    BUY("Buy"), SELL("Sell");

    private final String apiValue;

    OrderSide(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator
    public static OrderSide fromApiValue(String value) {
        for (OrderSide side : values()) {
            if (side.apiValue.equalsIgnoreCase(value)) return side;
        }
        throw new IllegalArgumentException("Unknown Fusion order side: " + value);
    }
}
