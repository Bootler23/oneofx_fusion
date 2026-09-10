package com.oneofx.fusion.tradingbot.desktop;

/** Bot-weite Ausfuehrungsregeln; Config Pools koennen sie je Paar ersetzen. */
public record BotBaseConfig(long ownerId, String buyOrderType, String sellOrderType,
        int maxBuyOrderMinutes, int maxSellOrderMinutes, int cooldownMinutes,
        double takeProfitPercent, boolean trailingStopBuyEnabled,
        double trailingStopBuyActivationPercent, double trailingStopBuyReboundPercent,
        boolean onlySellWithProfit, int closeAfterMinutes, boolean dcaEnabled,
        int dcaMaxOrders, double dcaTriggerPercent, double dcaSizeMultiplier) {

    public BotBaseConfig {
        buyOrderType = orderType(buyOrderType, "LIMIT", "MARKET", "STOP_LIMIT");
        sellOrderType = orderType(sellOrderType, "MARKET", "LIMIT", "STOP_MARKET");
        nonNegative(maxBuyOrderMinutes, "Maximale Kauforder-Laufzeit");
        nonNegative(maxSellOrderMinutes, "Maximale Verkaufsorder-Laufzeit");
        nonNegative(cooldownMinutes, "Cooldown");
        percent(takeProfitPercent, "Take Profit");
        percent(trailingStopBuyActivationPercent, "Trailing-Stop-Buy-Aktivierung");
        percent(trailingStopBuyReboundPercent, "Trailing-Stop-Buy-Rebound");
        nonNegative(closeAfterMinutes, "Zeitgesteuertes Schliessen");
        if (dcaMaxOrders < 0 || dcaMaxOrders > 1000) {
            throw new IllegalArgumentException("DCA-Nachkaeufe muessen zwischen 0 und 1000 liegen.");
        }
        percent(dcaTriggerPercent, "DCA-Trigger");
        if (!Double.isFinite(dcaSizeMultiplier) || dcaSizeMultiplier < 1 || dcaSizeMultiplier > 100) {
            throw new IllegalArgumentException("DCA-Multiplikator muss zwischen 1 und 100 liegen.");
        }
    }

    public static BotBaseConfig defaults(long ownerId) {
        return new BotBaseConfig(ownerId, "LIMIT", "MARKET", 0, 0, 60,
                3.0, false, 1.0, 0.3, false, 0, false, 3, 2.0, 1.0);
    }

    private static String orderType(String value, String... allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        for (String candidate : allowed) if (candidate.equals(normalized)) return normalized;
        throw new IllegalArgumentException("Nicht unterstuetzter Ordertyp: " + value);
    }

    private static void nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " darf nicht negativ sein.");
    }

    private static void percent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value >= 100) {
            throw new IllegalArgumentException(name + " muss zwischen 0 und kleiner 100 Prozent liegen.");
        }
    }
}
