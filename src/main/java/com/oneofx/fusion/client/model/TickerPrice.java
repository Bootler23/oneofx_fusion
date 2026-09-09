package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TickerPrice {
    private String pair;
    private String price;

    public String getSymbol() { return pair; }
    @JsonProperty("pair") public void setSymbol(String pair) { this.pair = pair; }
    public String getPrice() { return price == null || price.isBlank() ? "0" : price; }
    public void setPrice(String price) { this.price = price; }
}
