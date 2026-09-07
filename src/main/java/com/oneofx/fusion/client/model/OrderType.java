package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderType {
    LIMIT("Limit"), MARKET("Market"), STOP_LIMIT("StopLimit"), STOP_MARKET("StopMarket");

    private final String apiValue;

    OrderType(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator
    public static OrderType fromApiValue(String value) {
        for (OrderType type : values()) {
            if (type.apiValue.equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("Unknown Fusion order type: " + value);
    }
}
