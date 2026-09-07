package com.oneofx.fusion.tradingbot.domain;

public class PerformanceData {

    private String date;              // Format: "YYYY-MM-DD"
    private String currency;          // z.B. "LTCEUR"
    private int winTrades;            // Anzahl Gewinn-Trades
    private int lossTrades;           // Anzahl Verlust-Trades
    private double winRate;           // Win-Rate in Prozent (0-100)
    private double totalWinAmount;    // Kumulierte Gewinne in Prozent
    private double totalLossAmount;   // Kumulierte Verluste in Prozent
    private double totalBuffer;       // TotalWinAmount - TotalLossAmount
    private double dynamicStopLoss;   // Aktueller Stop-Loss in Prozent
    private int openPositions;        // Anzahl offener Positionen
    private String mode;              // "AGGRESSIVE", "STANDARD", "DEFENSIVE", "EMERGENCY"
    private String notes;             // Optionale Notizen
    private String lastUpdated;       // Timestamp der letzten Aktualisierung

    // Konstruktoren
    public PerformanceData() {
        // Default-Werte
        this.winTrades = 0;
        this.lossTrades = 0;
        this.winRate = 0.0;
        this.totalWinAmount = 0.0;
        this.totalLossAmount = 0.0;
        this.totalBuffer = 0.0;
        this.dynamicStopLoss = -2.0;
        this.openPositions = 0;
        this.mode = "STANDARD";
    }

    // Getter und Setter
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getWinTrades() {
        return winTrades;
    }

    public void setWinTrades(int winTrades) {
        this.winTrades = winTrades;
    }

    public int getLossTrades() {
        return lossTrades;
    }

    public void setLossTrades(int lossTrades) {
        this.lossTrades = lossTrades;
    }

    public double getWinRate() {
        return winRate;
    }

    public void setWinRate(double winRate) {
        this.winRate = winRate;
    }

    public double getTotalWinAmount() {
        return totalWinAmount;
    }

    public void setTotalWinAmount(double totalWinAmount) {
        this.totalWinAmount = totalWinAmount;
    }

    public double getTotalLossAmount() {
        return totalLossAmount;
    }

    public void setTotalLossAmount(double totalLossAmount) {
        this.totalLossAmount = totalLossAmount;
    }

    public double getTotalBuffer() {
        return totalBuffer;
    }

    public void setTotalBuffer(double totalBuffer) {
        this.totalBuffer = totalBuffer;
    }

    public double getDynamicStopLoss() {
        return dynamicStopLoss;
    }

    public void setDynamicStopLoss(double dynamicStopLoss) {
        this.dynamicStopLoss = dynamicStopLoss;
    }

    public int getOpenPositions() {
        return openPositions;
    }

    public void setOpenPositions(int openPositions) {
        this.openPositions = openPositions;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
   
    public int getTotalTrades() {
        return winTrades + lossTrades;
    }

    @Override
    public String toString() {
        return String.format(
            "PerformanceData[date=%s, currency=%s, wins=%d, losses=%d, winRate=%.2f%%, " +
            "buffer=%.2f%%, stopLoss=%.2f%%, mode=%s]",
            date, currency, winTrades, lossTrades, winRate, totalBuffer, dynamicStopLoss, mode
        );
    }
}
