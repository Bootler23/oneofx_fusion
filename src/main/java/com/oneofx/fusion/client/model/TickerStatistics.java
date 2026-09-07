package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TickerStatistics {
    private String symbol;
    private String price;
    private String high;
    private String low;
    private String volume;

    public String getSymbol() { return symbol; }
    @JsonProperty("pair") public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getLastPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    public String getHighPrice() { return high; }
    public void setHigh(String high) { this.high = high; }
    public String getLowPrice() { return low; }
    public void setLow(String low) { this.low = low; }
    public String getVolume() { return volume; }
    public void setVolume(String volume) { this.volume = volume; }
    public String getPriceChange() { return null; }
    public String getPriceChangePercent() { return null; }
    public String getWeightedAvgPrice() { return price; }
    public long getCount() { return 0; }
    public long getOpenTime() { return 0; }
    public long getCloseTime() { return 0; }
}
