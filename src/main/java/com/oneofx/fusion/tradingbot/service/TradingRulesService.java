package com.oneofx.fusion.tradingbot.service;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.TradingPair;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import com.oneofx.fusion.tradingbot.domain.TradingRules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads price, quantity and order-size constraints from Bitpanda Fusion. */
public final class TradingRulesService {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2_000;
    private static volatile TradingRulesService instance;

    private final FusionApiClient client;

    private TradingRulesService() {
        this.client = FusionClientProvider.getClient();
    }

    public static TradingRulesService getInstance() {
        TradingRulesService current = instance;
        if (current == null) {
            synchronized (TradingRulesService.class) {
                current = instance;
                if (current == null) {
                    current = new TradingRulesService();
                    instance = current;
                }
            }
        }
        return current;
    }

    public TradingRules getTradingRules(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol darf nicht leer sein");
        }
        try {
            return map(client.getTradingPair(symbol));
        } catch (FusionApiException e) {
            System.err.println("Fehler beim Abrufen der Fusion-Trading-Regeln für " + symbol + ": " + e.getMessage());
            return null;
        }
    }

    public int updateTradingRules(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return 0;

        List<TradingPair> available = fetchTradingPairs();
        Map<String, TradingPair> bySymbol = available.stream().collect(Collectors.toMap(
                pair -> FusionSymbol.compactPair(pair.getPair()),
                Function.identity(),
                (left, right) -> left));

        int success = 0;
        for (String symbol : symbols) {
            TradingPair pair = bySymbol.get(FusionSymbol.compactPair(symbol));
            if (pair != null && map(pair) != null) success++;
        }
        System.out.println("Fusion-Trading-Regeln abgerufen: " + success + "/" + symbols.size() + " Symbole");
        return success;
    }

    private List<TradingPair> fetchTradingPairs() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return client.getTradingPairs();
            } catch (FusionApiException e) {
                if (attempt == MAX_RETRIES) throw e;
                System.err.println("Fusion-Paare konnten nicht geladen werden, Versuch "
                        + attempt + "/" + MAX_RETRIES + ": " + e.getMessage());
                sleep(RETRY_DELAY_MS);
            }
        }
        return List.of();
    }

    private static TradingRules map(TradingPair pair) {
        if (pair == null) return null;
        TradingRules rules = new TradingRules(FusionSymbol.compactPair(pair.getPair()));
        rules.setTickSize(decimal(pair.getTickSize()));
        rules.setStepSize(decimal(pair.getSizeIncrement()));
        rules.setMinQty(decimal(pair.getSizeIncrement()));
        rules.setAmountIncrement(decimal(pair.getAmountIncrement()));
        rules.setMaxOrderSize(decimal(pair.getMaxOrderSize()));
        rules.setMinOrderAmount(decimal(pair.getMinOrderAmount()));
        rules.setMaxOrderAmount(decimal(pair.getMaxOrderAmount()));
        return rules;
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
