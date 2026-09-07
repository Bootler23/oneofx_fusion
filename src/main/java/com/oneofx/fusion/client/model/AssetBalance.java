package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AssetBalance {
    private String symbol;
    private String available;
    private String locked;

    public String getAsset() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getFree() { return available; }
    public void setAvailable(String available) { this.available = available; }
    public String getLocked() { return locked; }
    public void setLocked(String locked) { this.locked = locked; }
}
