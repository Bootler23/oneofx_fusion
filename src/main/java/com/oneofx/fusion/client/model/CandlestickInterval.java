package com.oneofx.fusion.client.model;

public enum CandlestickInterval {
    ONE_MINUTE("1m"), FIVE_MINUTES("5m"), TEN_MINUTES("10m"),
    FIFTEEN_MINUTES("15m"), HALF_HOURLY("30m"), HOURLY("1h"),
    FOUR_HOURLY("4h"), DAILY("1d");

    private final String intervalId;

    CandlestickInterval(String intervalId) {
        this.intervalId = intervalId;
    }

    public String getIntervalId() {
        return intervalId;
    }
}
