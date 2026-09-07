package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBook {
    private String pair;
    private String timestamp;
    private List<OrderBookEntry> bids;
    private List<OrderBookEntry> asks;

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public List<OrderBookEntry> getBids() { return bids; }
    public void setBids(List<OrderBookEntry> bids) { this.bids = bids; }
    public List<OrderBookEntry> getAsks() { return asks; }
    public void setAsks(List<OrderBookEntry> asks) { this.asks = asks; }
}
