package com.oneofx.fusion.tradingbot.grid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.oneofx.fusion.tradingbot.desktop.CurrencySettings;

/** Calculates a non-trading preview of every capital-funded grid order. */
public final class GridPreviewService {
    public static final int MAX_PREVIEW_ORDERS = 10_000;
    private static final int MAX_SCAN_LEVELS = 100_000;

    public Preview calculate(CurrencySettings settings, double anchorPrice, Rules rules) {
        if (settings == null) throw new IllegalArgumentException("Einstellungen fehlen.");
        if (!Double.isFinite(anchorPrice) || anchorPrice <= 0.0) {
            throw new IllegalArgumentException("Der Vorschaupreis muss größer als 0 sein.");
        }
        long fundedOrders = (long) Math.floor(
                settings.maxBuyAmount() / settings.buyAmount() + 1.0e-12);
        int target = (int) Math.min(fundedOrders, MAX_PREVIEW_ORDERS);
        List<String> warnings = new ArrayList<>();
        if (fundedOrders == 0) {
            warnings.add("Die Kapitalgrenze reicht für keine vollständige Order.");
        }
        if (fundedOrders > MAX_PREVIEW_ORDERS) {
            warnings.add("Vorschau auf " + MAX_PREVIEW_ORDERS
                    + " Orders begrenzt; geplant wären " + fundedOrders + ".");
        }
        if (rules == null || !rules.usable()) {
            warnings.add("Handelsregeln fehlen; Preise und Mengen sind nicht prüfbar.");
        } else if (settings.buyAmount() < rules.minOrderAmount()) {
            warnings.add("Der Orderbetrag liegt unter der Mindestorder von "
                    + plain(rules.minOrderAmount()) + ".");
        }

        List<Level> levels = new ArrayList<>(target);
        BigDecimal previousPrice = null;
        int invalidOrders = 0;
        for (long level = 1; levels.size() < target && level <= MAX_SCAN_LEVELS; level++) {
            double rawPrice = GridCalculator.level(anchorPrice, level,
                    settings.gridSettings());
            if (!Double.isFinite(rawPrice) || rawPrice <= 0.0) {
                warnings.add("Das arithmetische Grid erreicht bei Stufe " + level
                        + " einen Preis von 0.");
                break;
            }
            BigDecimal price = quantize(BigDecimal.valueOf(rawPrice),
                    rules == null ? null : rules.tickSize(), RoundingMode.HALF_UP);
            if (price.signum() <= 0 || price.equals(previousPrice)) continue;
            previousPrice = price;

            BigDecimal amount = BigDecimal.valueOf(settings.buyAmount());
            BigDecimal quantity = amount.divide(price, 16, RoundingMode.DOWN);
            quantity = quantize(quantity, rules == null ? null : rules.sizeIncrement(),
                    RoundingMode.DOWN);
            BigDecimal notional = price.multiply(quantity);
            boolean valid = rules != null && rules.usable() && quantity.signum() > 0
                    && notional.compareTo(BigDecimal.valueOf(rules.minOrderAmount())) >= 0
                    && (rules.maxOrderAmount() <= 0.0
                            || notional.compareTo(BigDecimal.valueOf(
                                    rules.maxOrderAmount())) <= 0);
            String status = rules == null || !rules.usable()
                    ? "Regeln fehlen"
                    : valid ? "Gültig" : "Nicht handelbar";
            if (!valid) invalidOrders++;
            levels.add(new Level(levels.size() + 1, price, quantity, notional, status));
        }
        if (invalidOrders > 0 && rules != null && rules.usable()) {
            warnings.add(invalidOrders + " Orderstufen verletzen nach der Mengenrundung "
                    + "die Mindest- oder Höchstorder.");
        }
        if (levels.size() < target && levels.size() > 0
                && levels.size() < fundedOrders) {
            warnings.add("Nur " + levels.size()
                    + " unterschiedliche positive Preisstufen sind darstellbar.");
        }

        double requiredCapital = levels.size() * settings.buyAmount();
        double unusedCapital = Math.max(0.0, settings.maxBuyAmount() - requiredCapital);
        double bottom = levels.isEmpty() ? anchorPrice
                : levels.get(levels.size() - 1).price().doubleValue();
        double coveragePercent = Math.max(0.0, (anchorPrice - bottom) / anchorPrice * 100.0);
        return new Preview(anchorPrice, fundedOrders, levels, requiredCapital,
                unusedCapital, bottom, coveragePercent, List.copyOf(warnings));
    }

    private static BigDecimal quantize(BigDecimal value, BigDecimal increment,
            RoundingMode mode) {
        if (increment == null || increment.signum() <= 0) return value.stripTrailingZeros();
        return value.divide(increment, 0, mode).multiply(increment)
                .setScale(Math.max(0, increment.scale()), RoundingMode.UNNECESSARY);
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public record Rules(BigDecimal tickSize, BigDecimal sizeIncrement,
            double minOrderAmount, double maxOrderAmount) {
        public boolean usable() {
            return tickSize != null && tickSize.signum() > 0
                    && sizeIncrement != null && sizeIncrement.signum() > 0
                    && minOrderAmount > 0.0;
        }
    }

    public record Level(int number, BigDecimal price, BigDecimal quantity,
            BigDecimal notional, String status) { }

    public record Preview(double anchorPrice, long fundedOrderCount,
            List<Level> levels, double requiredCapital, double unusedCapital,
            double bottomPrice, double coveragePercent, List<String> warnings) { }
}
