package com.oneofx.fusion.client.model;

import java.util.Locale;

public final class FusionSymbol {
    private FusionSymbol() {}

    public static String normalizePair(String pair) {
        if (pair == null || pair.isBlank()) {
            throw new IllegalArgumentException("Trading pair must not be blank");
        }
        String normalized = pair.trim().toUpperCase(Locale.ROOT).replace('_', '-');
        if (!normalized.contains("-")) {
            for (String quote : new String[] {"USDT", "USDC", "EUR", "USD", "CHF", "GBP", "BTC"}) {
                if (normalized.endsWith(quote) && normalized.length() > quote.length()) {
                    return normalized.substring(0, normalized.length() - quote.length()) + "-" + quote;
                }
            }
        }
        if (!normalized.matches("[A-Z0-9]{1,10}-[A-Z0-9]{1,10}")) {
            throw new IllegalArgumentException("Invalid Fusion trading pair: " + pair);
        }
        return normalized;
    }

    public static String baseAsset(String pair) {
        return normalizePair(pair).split("-", 2)[0];
    }

    public static String quoteAsset(String pair) {
        return normalizePair(pair).split("-", 2)[1];
    }
}
