package com.oneofx.fusion.tradingbot.desktop;

import java.math.BigDecimal;
import java.util.Objects;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.TradingPair;

/** Prüft neue Handelspaare gegen den aktiven Bitpanda-Fusion-Katalog. */
public final class TradingPairValidator {

    public TradingPair validate(String input, FusionApiClient client) {
        Objects.requireNonNull(client, "client");
        String requested = CurrencySettings.normalizeCurrency(input);
        TradingPair pair = client.getTradingPair(requested);
        if (pair == null || pair.getPair() == null
                || !requested.equals(FusionSymbol.compactPair(pair.getPair()))) {
            throw new IllegalArgumentException(
                    "Bitpanda Fusion hat das angefragte Handelspaar nicht bestätigt.");
        }
        requirePositive(pair.getTickSize(), "Preispräzision");
        requirePositive(pair.getSizeIncrement(), "Mengenpräzision");
        requirePositive(pair.getMinOrderAmount(), "Mindestorder");
        return pair;
    }

    private static void requirePositive(String value, String name) {
        try {
            if (value == null || new BigDecimal(value).signum() <= 0) {
                throw new IllegalArgumentException(name + " fehlt für dieses Handelspaar.");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " ist für dieses Handelspaar ungültig.", ex);
        }
    }
}
