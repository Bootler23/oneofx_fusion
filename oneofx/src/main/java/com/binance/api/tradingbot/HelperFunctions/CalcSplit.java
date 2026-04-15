package com.binance.api.tradingbot.HelperFunctions;

import com.binance.api.tradingbot.SQL_Database.SETSQL;

/**
 * Utility-Klasse für ROI-Berechnungen und Split-Verwaltung.
 */
public class CalcSplit {

    // Konfigurierbare Konstanten für die positionsabhängige ROI-Berechnung
    private static final double MAX_ROI_PERCENT = 23.0;
    private static final double TARGET_POSITION_COUNT = 100.0;

    /**
     * Berechnet den ROI-Split-Betrag basierend auf der Anzahl offener Positionen.
     * 
     * Die Strategie implementiert eine lineare Skalierung:
     * - Bei 0 Positionen: 0% vom Gewinn wird abgespalten (volle Reinvestition)
     * - Bei 100 Positionen: 23% vom Gewinn wird abgespalten (reduzierte Reinvestition)
     * - Linearer Zusammenhang zwischen 0 und 100 Positionen
     * - Ab 100 Positionen: konstant 23%
     * 
     * Beispiele:
     * - 50 Positionen = 20% Split
     * - 100 Positionen = 40% Split
     * - 150 Positionen = 60% Split
     * - 200+ Positionen = 80% Split
     * 
     * @param currency Das Währungspaar (reserviert für zukünftige währungsspezifische Logik)
     * @param GewinnAfterTax Der Gewinn nach Steuern in EUR
     * @return Der abzuspaltende Betrag in EUR (SplitValue)
     */
    public static double calcROI(String currency, double GewinnAfterTax) {
        
        // Bei 0 oder negativem Gewinn → kein Split
        if (GewinnAfterTax <= 0) {
            return 0.0;
        }
        
        int positionCount = SETSQL.getCount();
        
        // Bei 0 Positionen → 0% Split (volle Reinvestition)
        if (positionCount == 0) {
            return 0.0;
        }
        
        // Berechne linearen ROI-Prozentsatz
        // Schritt 1: Verhältnis der aktuellen Positionen zum Zielwert (0.0 bis 1.0+)
        double positionRatio = positionCount / TARGET_POSITION_COUNT;
        
        // Schritt 2: Skaliere auf maximalen ROI-Prozentsatz (0% bis 80%)
        double calculatedRoiPercent = positionRatio * MAX_ROI_PERCENT;
        
        // Schritt 3: Begrenze auf Maximum (falls mehr als 200 Positionen)
        double roiPercent = Math.min(MAX_ROI_PERCENT, calculatedRoiPercent);
        
        return GewinnAfterTax * (roiPercent / 100.0);   
    }
}
