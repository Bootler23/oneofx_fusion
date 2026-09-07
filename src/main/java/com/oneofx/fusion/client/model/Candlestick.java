package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Candlestick {
    private long timestamp;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;

    public Long getOpenTime() { return timestamp * 1000L; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getOpen() { return open; }
    public void setOpen(String open) { this.open = open; }
    public String getHigh() { return high; }
    public void setHigh(String high) { this.high = high; }
    public String getLow() { return low; }
    public void setLow(String low) { this.low = low; }
    public String getClose() { return close; }
    public void setClose(String close) { this.close = close; }
    public String getVolume() { return volume; }
    public void setVolume(String volume) { this.volume = volume; }
    public Long getCloseTime() { return getOpenTime(); }
}
