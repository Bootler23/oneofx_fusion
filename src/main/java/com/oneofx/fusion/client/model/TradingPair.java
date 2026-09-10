package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TradingPair {
    private String pair;
    private String baseAsset;
    private String quoteAsset;
    private String tickSize;
    private String sizeIncrement;
    private String amountIncrement;
    private String maxOrderSize;
    private String minOrderAmount;
    private String maxOrderAmount;
    private List<OrderType> supportedOrderTypes;

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }
    public String getBaseAsset() { return baseAsset; }
    public void setBaseAsset(String baseAsset) { this.baseAsset = baseAsset; }
    public String getQuoteAsset() { return quoteAsset; }
    public void setQuoteAsset(String quoteAsset) { this.quoteAsset = quoteAsset; }
    public String getTickSize() { return tickSize; }
    public void setTickSize(String tickSize) { this.tickSize = tickSize; }
    public String getSizeIncrement() { return sizeIncrement; }
    public void setSizeIncrement(String sizeIncrement) { this.sizeIncrement = sizeIncrement; }
    public String getAmountIncrement() { return amountIncrement; }
    public void setAmountIncrement(String amountIncrement) { this.amountIncrement = amountIncrement; }
    public String getMaxOrderSize() { return maxOrderSize; }
    public void setMaxOrderSize(String maxOrderSize) { this.maxOrderSize = maxOrderSize; }
    public String getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(String minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public String getMaxOrderAmount() { return maxOrderAmount; }
    public void setMaxOrderAmount(String maxOrderAmount) { this.maxOrderAmount = maxOrderAmount; }
    public List<OrderType> getSupportedOrderTypes() { return supportedOrderTypes; }
    public void setSupportedOrderTypes(List<OrderType> value) { supportedOrderTypes = value; }
    @JsonProperty("orderTypes")
    public void setOrderTypes(List<OrderType> value) { supportedOrderTypes = value; }
}
