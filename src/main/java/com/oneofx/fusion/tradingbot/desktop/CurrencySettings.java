package com.oneofx.fusion.tradingbot.desktop;

import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridSettings;

/** Bearbeitbare Strategie- und Risikowerte eines Handelspaars. */
public record CurrencySettings(
        String currency,
        boolean buyEnabled,
        double buyAmount,
        double maxBuyAmount,
        GridMode gridMode,
        double gridSpacing,
        double stopLoss,
        boolean trailingStopEnabled,
        double trailingStopActivation,
        double trailingStopDecline) {

    public CurrencySettings {
        currency = normalizeCurrency(currency);
        requireFinitePositive(buyAmount, "Kaufbetrag");
        requireFinitePositive(maxBuyAmount, "Kapitalgrenze");
        if (maxBuyAmount < buyAmount) {
            throw new IllegalArgumentException(
                    "Die Kapitalgrenze darf nicht kleiner als der Kaufbetrag sein.");
        }
        new GridSettings(gridMode, gridSpacing);
        requirePercent(stopLoss, "Stop-Loss", true);
        requirePercent(trailingStopActivation, "TSL-Aktivierung", true);
        requirePercent(trailingStopDecline, "TSL-Abstand", !trailingStopEnabled);
    }

    public static CurrencySettings defaults(String currency) {
        return new CurrencySettings(currency, false, 10.0, 500.0,
                GridMode.GEOMETRIC, 1.0 / 23.0,
                2.0, true, 2.5, 0.8);
    }

    public GridSettings gridSettings() {
        return new GridSettings(gridMode, gridSpacing);
    }

    public static String normalizeCurrency(String value) {
        if (value == null) throw new IllegalArgumentException("Das Handelspaar fehlt.");
        String normalized = value.trim().toUpperCase()
                .replace("-", "")
                .replace("/", "");
        if (!normalized.matches("[A-Z0-9]{5,20}")) {
            throw new IllegalArgumentException(
                    "Ungültiges Handelspaar. Beispiel: BTCEUR oder BTC-EUR.");
        }
        return normalized;
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " muss größer als 0 sein.");
        }
    }

    private static void requirePercent(double value, String name, boolean zeroAllowed) {
        double minimum = zeroAllowed ? 0.0 : Double.MIN_VALUE;
        if (!Double.isFinite(value) || value < minimum || value >= 100.0) {
            throw new IllegalArgumentException(name + " muss "
                    + (zeroAllowed ? "zwischen 0" : "größer als 0")
                    + " und kleiner als 100 Prozent sein.");
        }
    }
}
