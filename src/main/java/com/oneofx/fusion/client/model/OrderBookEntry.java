package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookEntry {
    private String price;
    private String quantity;
    private String totalQuantity;

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    public String getQty() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(String totalQuantity) { this.totalQuantity = totalQuantity; }
}
