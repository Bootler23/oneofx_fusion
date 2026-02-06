package com.binance.api.tradingbot.HelperFunctions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zinseszinsrechner für Trading-Berechnungen.
 * 
 * Berechnet den benötigten täglichen Zinssatz, um von einem Startkapital
 * zu einem Zielkapital in einer bestimmten Anzahl von Tagen zu gelangen.
 */
public class CompoundInterestCalculator {

    private static final Logger logger = LoggerFactory.getLogger(CompoundInterestCalculator.class);

    /**
     * Berechnet den benötigten täglichen Zinssatz für Zinseszins.
     * 
     * Formel: r = (Endkapital / Startkapital)^(1/Tage) - 1
     * 
     * Beispiel: Von 1000 EUR auf 1500 EUR in 30 Tagen
     * Ergebnis: ~1.37% pro Tag
     * 
     * @param startAmount Startsumme in EUR (muss > 0 sein)
     * @param endAmount   Zu erreichender Betrag in EUR (muss > startAmount sein)
     * @param days        Anzahl der Tage (Start bis Ende, muss > 0 sein)
     * @return Täglicher Zinssatz in Prozent
     * @throws IllegalArgumentException wenn Parameter ungültig sind
     */
    public static double calculateRequiredDailyRate(double startAmount, double endAmount, int days) {
        validateInputs(startAmount, endAmount, days);

        // Berechne täglichen Zinssatz: (EndBetrag/StartBetrag)^(1/Tage) - 1
        double dailyRate = Math.pow(endAmount / startAmount, 1.0 / days) - 1;

        // Rückgabe in Prozent
        return round.eight(dailyRate * 100);
    }

    /**
     * Berechnet den annualisierten Zinssatz (auf Jahresbasis).
     * 
     * Konvertiert den täglichen Zinssatz in einen Jahreszinssatz unter
     * Berücksichtigung des Zinseszinseffekts.
     * 
     * @param startAmount Startsumme in EUR
     * @param endAmount   Zu erreichender Betrag in EUR
     * @param days        Anzahl der Tage
     * @return Jährlicher Zinssatz in Prozent (annualisiert)
     * @throws IllegalArgumentException wenn Parameter ungültig sind
     */
    public static double calculateAnnualizedRate(double startAmount, double endAmount, int days) {
        double dailyRate = calculateRequiredDailyRate(startAmount, endAmount, days) / 100;
        double annualRate = Math.pow(1 + dailyRate, 365) - 1;
        return round.two(annualRate * 100);
    }

    /**
     * Berechnet das Endkapital bei gegebenem Startkapital, Zinssatz und Tagen.
     * 
     * Formel: Endkapital = Startkapital * (1 + Zinssatz)^Tage
     * 
     * @param startAmount       Startsumme in EUR
     * @param dailyRatePercent  Täglicher Zinssatz in Prozent (z.B. 1.5 für 1.5%)
     * @param days              Anzahl der Tage
     * @return Endkapital nach angegebenen Tagen
     * @throws IllegalArgumentException wenn Parameter ungültig sind
     */
    public static double calculateFinalAmount(double startAmount, double dailyRatePercent, int days) {
        if (startAmount <= 0 || days <= 0) {
            throw new IllegalArgumentException("Startbetrag und Tage müssen größer als 0 sein!");
        }

        double dailyRate = dailyRatePercent / 100.0;
        double finalAmount = startAmount * Math.pow(1 + dailyRate, days);

        return round.two(finalAmount);
    }

    /**
     * Gibt eine vollständige Zinseszins-Analyse in die Konsole aus.
     * 
     * @param startAmount Startsumme in EUR
     * @param endAmount   Zu erreichender Betrag in EUR
     * @param days        Anzahl der Tage
     */
    public static void printCompoundInterestAnalysis(double startAmount, double endAmount, int days) {
        try {
            validateInputs(startAmount, endAmount, days);

            double dailyRate = calculateRequiredDailyRate(startAmount, endAmount, days);
            double annualRate = calculateAnnualizedRate(startAmount, endAmount, days);
            double totalGain = endAmount - startAmount;
            double totalGainPercent = ((endAmount / startAmount) - 1) * 100;

            System.out.println("\n=== Zinseszins-Rechner ===");
            System.out.println("Startkapital: " + round.two(startAmount) + " EUR");
            System.out.println("Zielkapital: " + round.two(endAmount) + " EUR");
            System.out.println("Zeitraum: " + days + " Tage");
            System.out.println("Gewinn: " + round.two(totalGain) + " EUR (" + round.two(totalGainPercent) + "%)");
            System.out.println("\nBenötigter Zinssatz:");
            System.out.println("  → Pro Tag: " + dailyRate + "%");
            System.out.println("  → Pro Jahr (annualisiert): " + annualRate + "%");
            System.out.println("========================\n");

            logger.info("Zinseszins-Analyse: {}€ → {}€ in {} Tagen ({}% täglich)",
                    round.two(startAmount), round.two(endAmount), days, dailyRate);

        } catch (IllegalArgumentException e) {
            logger.error("Ungültige Eingabe für Zinseszins-Analyse: {}", e.getMessage());
            System.err.println("Fehler: " + e.getMessage());
        }
    }

    /**
     * Validiert die Eingabeparameter.
     * 
     * @param startAmount Startsumme
     * @param endAmount   Endsumme
     * @param days        Anzahl Tage
     * @throws IllegalArgumentException wenn Parameter ungültig sind
     */
    private static void validateInputs(double startAmount, double endAmount, int days) {
        if (startAmount <= 0) {
            throw new IllegalArgumentException("Startbetrag muss größer als 0 sein! Aktuell: " + startAmount);
        }

        if (endAmount <= 0) {
            throw new IllegalArgumentException("Endbetrag muss größer als 0 sein! Aktuell: " + endAmount);
        }

        if (days <= 0) {
            throw new IllegalArgumentException("Anzahl der Tage muss größer als 0 sein! Aktuell: " + days);
        }

        if (endAmount <= startAmount) {
            throw new IllegalArgumentException(
                    "Endbetrag (" + endAmount + ") muss größer als Startbetrag (" + startAmount + ") sein!");
        }
    }

    /**
     * Berechnet die Anzahl der Tage, die benötigt werden, um das Zielkapital zu erreichen.
     * 
     * Formel: Tage = log(Endkapital / Startkapital) / log(1 + Zinssatz)
     * 
     * @param startAmount       Startsumme in EUR
     * @param endAmount         Zu erreichender Betrag in EUR
     * @param dailyRatePercent  Täglicher Zinssatz in Prozent
     * @return Anzahl der benötigten Tage
     * @throws IllegalArgumentException wenn Parameter ungültig sind
     */
    public static int calculateRequiredDays(double startAmount, double endAmount, double dailyRatePercent) {
        if (startAmount <= 0 || endAmount <= startAmount) {
            throw new IllegalArgumentException("Startbetrag muss > 0 und Endbetrag > Startbetrag sein!");
        }

        if (dailyRatePercent <= 0) {
            throw new IllegalArgumentException("Zinssatz muss größer als 0 sein!");
        }

        double dailyRate = dailyRatePercent / 100.0;
        double days = Math.log(endAmount / startAmount) / Math.log(1 + dailyRate);

        return (int) Math.ceil(days);
    }
}
