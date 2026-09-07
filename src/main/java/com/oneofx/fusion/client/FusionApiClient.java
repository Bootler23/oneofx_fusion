package com.oneofx.fusion.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneofx.fusion.client.model.Account;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;
import com.oneofx.fusion.client.model.FusionSymbol;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.model.Order;
import com.oneofx.fusion.client.model.OrderBook;
import com.oneofx.fusion.client.model.PagedResponse;
import com.oneofx.fusion.client.model.TickerPrice;
import com.oneofx.fusion.client.model.TickerStatistics;
import com.oneofx.fusion.client.model.Trade;
import com.oneofx.fusion.client.model.TradingPair;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synchronous client for the Bitpanda Fusion REST API.
 *
 * Authentication follows the official API documentation: every request carries
 * the Fusion API key in the {@code x-api-key} header. Trading pairs are
 * normalized from the former Binance notation (for example {@code LTCEUR}) to
 * Fusion notation ({@code LTC-EUR}) at the API boundary.
 */
public final class FusionApiClient {
    public static final String DEFAULT_BASE_URL = "https://api.fusion.bitpanda.com";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final int MAX_CANDLE_LIMIT = 1440;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final FusionRateLimiter rateLimiter = new FusionRateLimiter();

    public FusionApiClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public FusionApiClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("BITPANDA_FUSION_API_KEY must not be empty");
        }
        this.apiKey = apiKey.trim();
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void ping() {
        get("/v1/time", Map.of(), new TypeReference<Map<String, Object>>() {});
    }

    public List<TickerPrice> getAllPrices() {
        return get("/v1/tickers", Map.of(), new TypeReference<List<TickerPrice>>() {});
    }

    public TickerPrice getPrice(String pair) {
        List<TickerPrice> prices = get("/v1/tickers",
                Map.of("pair", FusionSymbol.normalizePair(pair)),
                new TypeReference<List<TickerPrice>>() {});
        if (prices.isEmpty()) {
            throw new FusionApiException("No ticker returned for " + pair, 404);
        }
        return prices.get(0);
    }

    public List<TickerStatistics> getAll24HrPriceStatistics() {
        return get("/v1/tickers", Map.of(), new TypeReference<List<TickerStatistics>>() {});
    }

    public TickerStatistics get24HrPriceStatistics(String pair) {
        List<TickerStatistics> tickers = get("/v1/tickers",
                Map.of("pair", FusionSymbol.normalizePair(pair)),
                new TypeReference<List<TickerStatistics>>() {});
        if (tickers.isEmpty()) {
            throw new FusionApiException("No ticker statistics returned for " + pair, 404);
        }
        return tickers.get(0);
    }

    public List<TradingPair> getTradingPairs() {
        return get("/v1/pairs", Map.of(), new TypeReference<List<TradingPair>>() {});
    }

    public TradingPair getTradingPair(String pair) {
        List<TradingPair> pairs = get("/v1/pairs",
                Map.of("pair", FusionSymbol.normalizePair(pair)),
                new TypeReference<List<TradingPair>>() {});
        if (pairs.isEmpty()) {
            throw new FusionApiException("No trading rules returned for " + pair, 404);
        }
        return pairs.get(0);
    }

    public OrderBook getOrderBook(String pair, Integer depth) {
        Map<String, String> query = new LinkedHashMap<>();
        if (depth != null) query.put("depth", String.valueOf(depth));
        return get("/v1/orderbook/" + FusionSymbol.normalizePair(pair), query,
                new TypeReference<OrderBook>() {});
    }

    public List<Candlestick> getCandlestickBars(String pair, CandlestickInterval interval) {
        return getCandlestickBars(pair, interval, null, null, null);
    }

    public List<Candlestick> getCandlestickBars(String pair, CandlestickInterval interval,
                                                 Integer limit, Long startTime, Long endTime) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("interval", Objects.requireNonNull(interval, "interval").getIntervalId());
        if (endTime != null) query.put("to", String.valueOf(toEpochSeconds(endTime)));
        if (limit != null) {
            if (limit < 1 || limit > MAX_CANDLE_LIMIT) {
                throw new IllegalArgumentException("Fusion candle limit must be between 1 and " + MAX_CANDLE_LIMIT);
            }
            // Bitpanda Fusion ignoriert "from", sobald "limit" gesetzt ist.
            query.put("limit", String.valueOf(limit));
        } else if (startTime != null) {
            query.put("from", String.valueOf(toEpochSeconds(startTime)));
        }
        return get("/v1/candles/" + FusionSymbol.normalizePair(pair), query,
                new TypeReference<List<Candlestick>>() {});
    }

    public Account getAccount() {
        return new Account(get("/v1/account/balances", Map.of(),
                new TypeReference<List<com.oneofx.fusion.client.model.AssetBalance>>() {}));
    }

    public NewOrderResponse newOrder(NewOrder order) {
        Objects.requireNonNull(order, "order").validate();
        return send("POST", "/v1/account/orders", Map.of(), order,
                new TypeReference<NewOrderResponse>() {});
    }

    public Order getOrderStatus(String orderId) {
        return get("/v1/account/orders/" + pathSegment(orderId), Map.of(),
                new TypeReference<Order>() {});
    }

    public Order cancelOrder(String orderId) {
        return send("DELETE", "/v1/account/orders/" + pathSegment(orderId), Map.of(), null,
                new TypeReference<Order>() {});
    }

    public List<Order> getOpenOrders(String pair) {
        List<Order> orders = getOrders(pair, "open");
        return orders.stream().filter(order -> order.getStatus() != null && order.getStatus().isOpen()).toList();
    }

    public List<Order> getOrders(String pair, String status) {
        Map<String, String> query = new LinkedHashMap<>();
        if (pair != null && !pair.isBlank()) query.put("pair", FusionSymbol.normalizePair(pair));
        if (status != null && !status.isBlank()) query.put("status", status);
        return getAllPages("/v1/account/orders", query, new TypeReference<PagedResponse<Order>>() {});
    }

    public List<Trade> getMyTrades(String pair) {
        Map<String, String> query = new LinkedHashMap<>();
        if (pair != null && !pair.isBlank()) query.put("pair", FusionSymbol.normalizePair(pair));
        return getAllPages("/v1/account/trades", query, new TypeReference<PagedResponse<Trade>>() {});
    }

    public List<Trade> getTradesForOrder(String pair, String orderId) {
        Map<String, String> query = new LinkedHashMap<>();
        if (pair != null && !pair.isBlank()) query.put("pair", FusionSymbol.normalizePair(pair));
        query.put("orderId", orderId);
        return getAllPages("/v1/account/trades", query, new TypeReference<PagedResponse<Trade>>() {});
    }

    private <T> List<T> getAllPages(String path, Map<String, String> initialQuery,
                                    TypeReference<PagedResponse<T>> responseType) {
        List<T> result = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> query = new LinkedHashMap<>(initialQuery);
            query.put("limit", String.valueOf(PAGE_SIZE));
            if (cursor != null && !cursor.isBlank()) query.put("cursor", cursor);
            PagedResponse<T> response = get(path, query, responseType);
            result.addAll(response.getData());
            if (response.getMeta() == null || !response.getMeta().isHasNextPage()) return result;
            cursor = response.getMeta().getNextCursor();
            if (cursor == null || cursor.isBlank()) return result;
        }
        throw new FusionApiException("Pagination exceeded " + MAX_PAGES + " pages for " + path);
    }

    private <T> T get(String path, Map<String, String> query, TypeReference<T> responseType) {
        return send("GET", path, query, null, responseType);
    }

    private <T> T send(String method, String path, Map<String, String> query, Object body,
                       TypeReference<T> responseType) {
        rateLimiter.acquire(method, path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(path, query))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .header("User-Agent", "oneofx-fusion/1.0");

        try {
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FusionApiException(errorMessage(method, path, response), response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) return null;
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FusionApiException("Fusion API request interrupted", e);
        } catch (JsonProcessingException e) {
            throw new FusionApiException("Could not process Fusion API JSON for " + path, e);
        } catch (IOException e) {
            throw new FusionApiException("Fusion API request failed for " + path, e);
        }
    }

    private URI buildUri(String path, Map<String, String> query) {
        StringBuilder value = new StringBuilder(baseUrl).append(path);
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry.getValue() == null) continue;
            value.append(first ? '?' : '&');
            first = false;
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return URI.create(value.toString());
    }

    private static String errorMessage(String method, String path, HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ").trim();
        if (body.length() > 500) body = body.substring(0, 500) + "...";
        return "Fusion API " + method + " " + path + " returned HTTP " + response.statusCode()
                + (body.isEmpty() ? "" : ": " + body);
    }

    private static long toEpochSeconds(long timestamp) {
        return timestamp > 10_000_000_000L ? timestamp / 1000L : timestamp;
    }

    private static String pathSegment(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ID must not be blank");
        return encode(value.trim());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }
}
