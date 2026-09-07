package com.oneofx.fusion.tradingbot.Stream;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.TickerPrice;
import com.oneofx.fusion.tradingbot.Settings.FusionClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Compatibility replacement for the former Binance WebSocket ticker stream.
 * Fusion currently exposes market data through REST, so one bulk ticker request
 * refreshes all watched symbols every second.
 */
public final class CombinedTickerStream {
    private static final Logger logger = LoggerFactory.getLogger(CombinedTickerStream.class);
    private static final long POLL_INTERVAL_SECONDS = 1;

    private final FusionApiClient client = FusionClientProvider.getClient();
    private final Set<String> watchedSymbols = ConcurrentHashMap.newKeySet();
    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private volatile boolean active;
    private volatile ConnectionStatus connectionStatus = ConnectionStatus.STOPPED;
    private ScheduledExecutorService scheduler;

    public enum ConnectionStatus {
        STOPPED, CONNECTED, RECONNECTING, REST_FALLBACK
    }

    public synchronized void start(String[] symbols) {
        if (symbols == null || symbols.length == 0) {
            throw new IllegalArgumentException("Symbols-Array darf nicht leer sein");
        }
        addSymbols(symbols);
        if (active) return;
        active = true;
        connectionStatus = ConnectionStatus.REST_FALLBACK;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "FusionTickerPoller");
            thread.setDaemon(true);
            return thread;
        });
        refresh();
        scheduler.scheduleAtFixedRate(this::refresh, POLL_INTERVAL_SECONDS,
                POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("Fusion REST ticker polling für {} Symbole gestartet", watchedSymbols.size());
    }

    private void refresh() {
        if (!active) return;
        try {
            for (TickerPrice ticker : client.getAllPrices()) {
                if (ticker.getSymbol() == null || ticker.getPrice() == null) continue;
                String symbol = FusionSymbol.compactPair(ticker.getSymbol());
                if (watchedSymbols.contains(symbol)) {
                    prices.put(symbol, new BigDecimal(ticker.getPrice()));
                }
            }
            connectionStatus = ConnectionStatus.REST_FALLBACK;
        } catch (RuntimeException e) {
            connectionStatus = ConnectionStatus.RECONNECTING;
            logger.warn("Fusion-Ticker konnten nicht aktualisiert werden: {}", e.getMessage());
        }
    }

    public Double getPrice(String symbol) {
        BigDecimal price = prices.get(FusionSymbol.compactPair(symbol));
        return price == null ? null : price.doubleValue();
    }

    public boolean hasData(String symbol) {
        return prices.containsKey(FusionSymbol.compactPair(symbol));
    }

    public int getActiveSymbolCount() {
        return (int) watchedSymbols.stream().filter(prices::containsKey).count();
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void addSymbols(String[] newSymbols) {
        if (newSymbols == null) return;
        for (String symbol : newSymbols) {
            if (symbol != null && !symbol.isBlank()) watchedSymbols.add(FusionSymbol.compactPair(symbol));
        }
        if (active) refresh();
    }

    public synchronized void stop() {
        active = false;
        connectionStatus = ConnectionStatus.STOPPED;
        if (scheduler != null) scheduler.shutdownNow();
    }
}
