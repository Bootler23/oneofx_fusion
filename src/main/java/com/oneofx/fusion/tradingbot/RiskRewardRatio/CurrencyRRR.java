package com.oneofx.fusion.tradingbot.RiskRewardRatio;

import com.oneofx.fusion.tradingbot.HelperFunctions.round;

/**
 * Repräsentiert die Risk-Reward-Ratio (RRR) Metriken für eine spezifische Währung.
 * 
 * Diese Klasse speichert aggregierte Trading-Performance-Daten der letzten 30 Tage:
 * - Gewichtete Gewinne (Profit * SellAmount)
 * - Gewichtete Verluste (|Profit| * SellAmount)
 * - Anzahl profitabler und verlustbringender Trades
 * 
 * Verwendung: Wird von PerformanceSQL.getWeightedRRRLast30Days() erstellt und
 * für dynamische Stop-Loss Berechnungen sowie Performance-Analysen genutzt.
 * 
 * @author Trading Bot System
 * @version 1.0
 * @since 2026-03-03
 */
public class CurrencyRRR {
    
    private final String currency;
    private final double weightedWins;
    private final double weightedLosses;
    private final int countPositive;
    private final int countNegative;
    
    /**
     * Erstellt ein neues CurrencyRRR Objekt mit Performance-Metriken.
     * 
     * @param currency Das Währungspaar (z.B. "LTCEUR", "BNBEUR")
     * @param weightedWins Summe der gewichteten Gewinne (SUM(Profit * SellAmount) für Profit > 0)
     * @param weightedLosses Summe der gewichteten Verluste (ABS(SUM(Profit * SellAmount)) für Profit < 0)
     * @param countPositive Anzahl profitabler Trades (Profit > 0)
     * @param countNegative Anzahl verlustbringender Trades (Profit < 0)
     */
    public CurrencyRRR(String currency, double weightedWins, double weightedLosses, 
                       int countPositive, int countNegative) {
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("Currency darf nicht null oder leer sein");
        }
        if (weightedWins < 0 || weightedLosses < 0) {
            throw new IllegalArgumentException("Gewichtete Werte müssen positiv sein");
        }
        if (countPositive < 0 || countNegative < 0) {
            throw new IllegalArgumentException("Anzahl Trades muss positiv sein");
        }
        
        this.currency = currency;
        this.weightedWins = weightedWins;
        this.weightedLosses = weightedLosses;
        this.countPositive = countPositive;
        this.countNegative = countNegative;
    }
    
    // ==================== Getter-Methoden ====================
    
    public String getCurrency() {
        return currency;
    }
    
    public double getWeightedWins() {
        return weightedWins;
    }
    
    public double getWeightedLosses() {
        return weightedLosses;
    }
    
    public int getCountPositive() {
        return countPositive;
    }
    
    public int getCountNegative() {
        return countNegative;
    }
    
    // ==================== Berechnungs-Methoden ====================
    
    /**
     * Berechnet die Risk-Reward-Ratio (RRR).
     * 
     * Die RRR zeigt das Verhältnis von durchschnittlichem Gewinn zu durchschnittlichem Verlust.
     * - RRR > 1.0: Durchschnittlicher Gewinn ist höher als durchschnittlicher Verlust
     * - RRR = 1.0: Durchschnittlicher Gewinn entspricht durchschnittlichem Verlust
     * - RRR < 1.0: Durchschnittlicher Gewinn ist niedriger als durchschnittlichem Verlust
     * 
     * Beispiel: RRR = 2.5 bedeutet, dass der durchschnittliche Gewinn 2.5x höher ist
     * als der durchschnittliche Verlust.
     * 
     * @return RRR als weightedWins / weightedLosses, oder weightedWins wenn keine Verluste
     */
    public double calculateRRR() {
        if (weightedLosses == 0) {
            return weightedWins; // Keine Verluste -> Alle Gewinne
        }
        return round.three(weightedWins / weightedLosses);
    }
    
    /**
     * Berechnet die Win-Rate (Gewinn-Prozentsatz).
     * 
     * Die Win-Rate zeigt den Anteil erfolgreicher Trades:
     * - 75% Win-Rate: 3 von 4 Trades sind profitabel
     * - 50% Win-Rate: Jeder zweite Trade ist profitabel
     * - 25% Win-Rate: Nur 1 von 4 Trades ist profitabel
     * 
     * WICHTIG: Eine niedrige Win-Rate kann durch eine hohe RRR kompensiert werden.
     * Beispiel: 40% Win-Rate mit RRR 3.0 ist profitabel
     * (0.4 * 3 - 0.6 * 1 = 1.2 - 0.6 = 0.6 Gewinn pro Trade)
     * 
     * @return Win-Rate als Dezimalzahl (z.B. 0.75 für 75%)
     */
    public double calculateWinRate() {
        int totalTrades = countPositive + countNegative;
        if (totalTrades == 0) {
            return 0.0;
        }
        return round.three((double) countPositive / totalTrades);
    }

    public double calculateWinPercent() {
        return round.two(calculateWinRate() * 100.0);
    }

    public double calculateLossPercent() {
        int totalTrades = countPositive + countNegative;
        if (totalTrades == 0) {
            return 0.0;
        }
        return round.two(100.0 - calculateWinPercent());
    }

    public double calculateWeightedPositivePercent() {
        double totalWeighted = weightedWins + weightedLosses;
        if (totalWeighted == 0) {
            return 0.0;
        }
        return round.two((weightedWins / totalWeighted) * 100.0);
    }

    public double calculateWeightedNegativePercent() {
        double totalWeighted = weightedWins + weightedLosses;
        if (totalWeighted == 0) {
            return 0.0;
        }
        return round.two((weightedLosses / totalWeighted) * 100.0);
    }
    
    /**
     * Berechnet den durchschnittlichen gewichteten Gewinn pro profitablem Trade.
     * 
     * Dies zeigt, wie viel im Durchschnitt bei einem gewinnbringenden Trade verdient wird.
     * 
     * @return Durchschnittlicher Gewinn oder 0.0 wenn keine positiven Trades
     */
    public double getAverageWeightedWin() {
        if (countPositive == 0) {
            return 0.0;
        }
        return round.three(weightedWins / countPositive);
    }
    
    /**
     * Berechnet den durchschnittlichen gewichteten Verlust pro verlustbringendem Trade.
     * 
     * Dies zeigt, wie viel im Durchschnitt bei einem verlustbringenden Trade verloren wird.
     * 
     * @return Durchschnittlicher Verlust (positiver Wert) oder 0.0 wenn keine negativen Trades
     */
    public double getAverageWeightedLoss() {
        if (countNegative == 0) {
            return 0.0;
        }
        return round.three(weightedLosses / countNegative);
    }
    
    /**
     * Gibt den Netto-Profit zurück (Gewinne - Verluste).
     * 
     * Dies ist der Gesamtprofit des Währungspaares in den letzten 30 Tagen:
     * - Positiver Wert: Netto-Gewinn
     * - Negativer Wert: Netto-Verlust
     * - Null: Break-even
     * 
     * @return Netto-Profit als Dezimalzahl
     */
    public double getNetProfit() {
        return round.two(weightedWins - weightedLosses);
    }
    
    /**
     * Berechnet den Total Buffer (entspricht Net Profit).
     * 
     * Dieser Wert wird für die dynamische Stop-Loss Berechnung verwendet.
     * Ein positiver Buffer erlaubt weiteren Stop-Loss, ein negativer Buffer
     * erzwingt engeren Stop-Loss zum Schutz des Kapitals.
     * 
     * @return Total Buffer in Prozent
     */
    public double getTotalBuffer() {
        return getNetProfit();
    }
    
    /**
     * Berechnet die Gesamtanzahl aller Trades (Gewinne + Verluste).
     * 
     * @return Gesamtanzahl der Trades
     */
    public int getTotalTradeCount() {
        return countPositive + countNegative;
    }
    
    /**
     * Berechnet den Profit Factor (Bruttoverhältnis Gewinn/Verlust).
     * 
     * Der Profit Factor ist ähnlich zur RRR, aber bezieht sich auf die Bruttosummen:
     * - Profit Factor > 1.0: Profitables System
     * - Profit Factor = 1.0: Break-even
     * - Profit Factor < 1.0: Verlustbringendes System
     * 
     * Beispiel: Profit Factor = 1.5 bedeutet, dass für jeden verlorenen EUR
     * 1.5 EUR gewonnen werden.
     * 
     * @return Profit Factor oder 0.0 wenn keine Verluste
     */
    public double calculateProfitFactor() {
        if (weightedLosses == 0) {
            return weightedWins > 0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        return round.three(weightedWins / weightedLosses);
    }
    
    /**
     * Berechnet die erforderliche Win-Rate für Break-even.
     * 
     * Diese Methode zeigt, welche Win-Rate mindestens notwendig ist, um bei
     * der aktuellen durchschnittlichen Gewinn/Verlust-Ratio profitabel zu sein.
     * 
     * Formel: Required Win Rate = AvgLoss / (AvgWin + AvgLoss)
     * 
     * Beispiel: Bei durchschnittlichem Gewinn von 2% und Verlust von 1%
     * Required Win Rate = 1 / (2 + 1) = 33.33%
     * 
     * @return Erforderliche Win-Rate in Prozent (0-100)
     */
    public double calculateRequiredWinRate() {
        double avgWin = getAverageWeightedWin();
        double avgLoss = getAverageWeightedLoss();
        return RRR.calculateRequiredWinRate(avgWin, avgLoss);
    }
    
    /**
     * Prüft, ob die Trading-Performance profitabel ist.
     * 
     * @return true wenn Net Profit > 0, sonst false
     */
    public boolean isProfitable() {
        return getNetProfit() > 0;
    }
    
    /**
     * Prüft, ob die aktuelle Win-Rate über der erforderlichen Win-Rate liegt.
     * 
     * Dies zeigt, ob die Strategie nachhaltig profitabel ist.
     * 
     * @return true wenn aktuelle Win-Rate >= erforderliche Win-Rate
     */
    public boolean hasHealthyWinRate() {
        double currentWinRate = calculateWinRate() * 100; // In Prozent
        double requiredWinRate = calculateRequiredWinRate();
        return currentWinRate >= requiredWinRate;
    }
    
    // ==================== Utility-Methoden ====================
    
    /**
     * Gibt eine detaillierte String-Repräsentation des CurrencyRRR Objekts zurück.
     * 
     * Format: CurrencyRRR[currency=LTCEUR, RRR=2.50, WinRate=65.00%, ProfitFactor=1.85, 
     *         NetProfit=125.50, Trades=20 (13W/7L), AvgWin=15.25, AvgLoss=6.10]
     * 
     * @return Formatierte String-Darstellung
     */
    @Override
    public String toString() {
        return String.format(
            "CurrencyRRR[currency=%s, RRR=%.2f, WinRate=%.2f%%, ProfitFactor=%.2f, NetProfit=%.2f, " +
            "Trades=%d (%dW/%dL), AvgWin=%.2f, AvgLoss=%.2f]",
            currency, 
            calculateRRR(), 
            calculateWinRate() * 100, 
            calculateProfitFactor(),
            getNetProfit(),
            getTotalTradeCount(),
            countPositive, 
            countNegative,
            getAverageWeightedWin(),
            getAverageWeightedLoss()
        );
    }
    
    /**
     * Gibt eine kompakte String-Repräsentation für Logging zurück.
     * 
     * @return Kompakte Darstellung (z.B. "LTCEUR: +125.50 (RRR=2.50, WR=65%)")
     */
    public String toCompactString() {
        return String.format(
            "%s: %+.2f (RRR=%.2f, WR=%.0f%%)",
            currency,
            getNetProfit(),
            calculateRRR(),
            calculateWinRate() * 100
        );
    }

    public String toWinLossString() {
        return String.format(
            "%s: Win=%.0f%% Loss=%.0f%%",
            currency,
            calculateWinPercent(),
            calculateLossPercent()
        );
    }

    public String toWeightedBreakdownString() {
        return String.format(
            "( +%.2f | -%.2f )",
            weightedWins,
            weightedLosses
        );
    }
}
