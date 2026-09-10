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
        String normalized = normalize(value);
        for (OrderType type : values()) {
            if (normalize(type.apiValue).equals(normalized)
                    || normalize(type.name()).equals(normalized)) return type;
        }
        throw new IllegalArgumentException("Unknown Fusion order type: " + value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "")
                .replace(" ", "").trim().toUpperCase(java.util.Locale.ROOT);
    }
}
