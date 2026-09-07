package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OrderStatus {
    NEW, FILLED, FILLED_AND_CANCELED, PARTIALLY_FILLED, CANCELED, DONE_FOR_DAY, REJECTED, UNKNOWN;

    @JsonCreator
    public static OrderStatus fromApiValue(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }

    public boolean isOpen() {
        return this == NEW || this == PARTIALLY_FILLED;
    }
}
