package com.oneofx.fusion.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneofx.fusion.client.model.CandlestickInterval;
import com.oneofx.fusion.client.model.AssetBalance;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.model.Order;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TickerPrice;
import com.oneofx.fusion.client.model.TimeInForce;
import com.oneofx.fusion.client.model.Trade;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class FusionApiClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    public void tickerUsesFusionPairAndApiKey() {
        server.createContext("/v1/tickers", exchange -> {
            lastExchange.set(exchange);
            respond(exchange, 200, "[{\"pair\":\"BTC-EUR\",\"price\":\"50000.12\"}]");
        });

        TickerPrice ticker = new FusionApiClient("test-key", baseUrl).getPrice("btceur");

        assertEquals("BTC-EUR", ticker.getSymbol());
        assertEquals("50000.12", ticker.getPrice());
        assertEquals("pair=BTC-EUR", lastExchange.get().getRequestURI().getRawQuery());
        assertEquals("test-key", lastExchange.get().getRequestHeaders().getFirst("x-api-key"));
    }

    @Test
    public void stopLimitOrderUsesFusionJsonAndUuid() throws Exception {
        server.createContext("/v1/account/orders", exchange -> {
            lastExchange.set(exchange);
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 202, "{\"id\":\"c618d38d-29a3-4dc6-a16d-05e2659157e7\"}");
        });
        NewOrder order = new NewOrder("ETH-EUR", OrderSide.BUY, OrderType.STOP_LIMIT,
                TimeInForce.GTC, "0.25", "2500.00").triggerPrice("2450.00");

        NewOrderResponse response = new FusionApiClient("test-key", baseUrl).newOrder(order);
        JsonNode json = new ObjectMapper().readTree(lastBody.get());

        assertEquals("POST", lastExchange.get().getRequestMethod());
        assertEquals("c618d38d-29a3-4dc6-a16d-05e2659157e7", response.getOrderId());
        assertEquals("ETH-EUR", json.get("pair").asText());
        assertEquals("Buy", json.get("side").asText());
        assertEquals("StopLimit", json.get("type").asText());
        assertEquals("GTC", json.get("timeInForce").asText());
        assertEquals("0.25", json.get("quantity").asText());
        assertEquals("2500.00", json.get("limitPrice").asText());
        assertEquals("2450.00", json.get("triggerPrice").asText());
        assertFalse(json.has("amount"));
        assertFalse(json.has("endTime"));
    }

    @Test
    public void limitBuyBelowMarketHasNoTriggerPrice() throws Exception {
        server.createContext("/v1/account/orders", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 202, "{\"id\":\"limit-buy-1\"}");
        });
        NewOrder order = new NewOrder("ETH-EUR", OrderSide.BUY, OrderType.LIMIT,
                TimeInForce.GTC, "0.25", "2400.00");

        new FusionApiClient("test-key", baseUrl).newOrder(order);
        JsonNode json = new ObjectMapper().readTree(lastBody.get());

        assertEquals("Limit", json.get("type").asText());
        assertEquals("2400.00", json.get("limitPrice").asText());
        assertFalse(json.has("triggerPrice"));
    }

    @Test
    public void orderKeepsLimitPriceSeparateFromPartialFillAverage() throws Exception {
        Order order = new ObjectMapper().readValue(
                "{\"id\":\"partial-1\",\"limitPrice\":\"2400.00\","
                        + "\"filledAveragePrice\":\"2399.50\"}",
                Order.class);

        assertEquals("2399.50", order.getPrice());
        assertEquals("2400.00", order.getLimitPrice());
    }

    @Test
    public void missingOptionalNumericResponseFieldsDefaultToZero() throws Exception {
        Trade trade = new ObjectMapper().readValue("{}", Trade.class);
        AssetBalance balance = new ObjectMapper().readValue("{}", AssetBalance.class);
        TickerPrice ticker = new ObjectMapper().readValue("{}", TickerPrice.class);

        assertEquals("0", trade.getQty());
        assertEquals("0", trade.getPrice());
        assertEquals("0", trade.getQuoteQty());
        assertEquals("0", balance.getFree());
        assertEquals("0", balance.getLocked());
        assertEquals("0", ticker.getPrice());
    }

    @Test
    public void tradeAmountIsMappedToExecutedQuantity() {
        server.createContext("/v1/account/trades", exchange -> {
            lastExchange.set(exchange);
            respond(exchange, 200, "{\"data\":[{"
                    + "\"id\":\"trade-1\",\"orderId\":\"order-1\",\"pair\":\"BTC-EUR\","
                    + "\"side\":\"Buy\",\"price\":\"67451.90\",\"amount\":\"0.00051874\","
                    + "\"fee\":{\"amount\":\"0.087475\",\"symbol\":\"EUR\"},"
                    + "\"total\":\"34.99\"}],"
                    + "\"meta\":{\"limit\":100,\"hasNextPage\":false,\"nextCursor\":null}}");
        });

        List<Trade> trades = new FusionApiClient("test-key", baseUrl)
                .getTradesForOrder("BTCEUR", "order-1");

        assertEquals(1, trades.size());
        assertEquals("0.00051874", trades.get(0).getQty());
        assertEquals("34.99", trades.get(0).getQuoteQty());
        assertEquals("0.087475", trades.get(0).getCommission());
        assertEquals("EUR", trades.get(0).getCommissionAsset());
        assertEquals("pair=BTC-EUR&orderId=order-1&limit=100",
                lastExchange.get().getRequestURI().getRawQuery());
    }

    @Test
    public void invalidOrderIsRejectedBeforeNetworkCall() {
        NewOrder missingTrigger = new NewOrder("ETH-EUR", OrderSide.BUY, OrderType.STOP_LIMIT,
                TimeInForce.GTC, "0.25", "2500.00");

        assertThrows(IllegalArgumentException.class,
                () -> new FusionApiClient("test-key", baseUrl).newOrder(missingTrigger));
    }

    @Test
    public void candlesUseFusionMaximumAndOmitFromWhenLimitIsSet() {
        server.createContext("/v1/candles/BTC-EUR", exchange -> {
            lastExchange.set(exchange);
            respond(exchange, 200, "[]");
        });

        new FusionApiClient("test-key", baseUrl).getCandlestickBars(
                "btceur", CandlestickInterval.HOURLY, 1440, 1_700_000_000_000L, 1_710_000_000_000L);

        String query = lastExchange.get().getRequestURI().getRawQuery();
        assertEquals("interval=1h&to=1710000000&limit=1440", query);
    }

    @Test
    public void candlesRejectLimitsOutsideFusionRange() {
        FusionApiClient client = new FusionApiClient("test-key", baseUrl);

        assertThrows(IllegalArgumentException.class,
                () -> client.getCandlestickBars("BTC-EUR", CandlestickInterval.HOURLY, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.getCandlestickBars("BTC-EUR", CandlestickInterval.HOURLY, 1441, null, null));
    }

    @Test
    public void httpErrorsExposeStatusCode() {
        server.createContext("/v1/time", exchange -> respond(exchange, 429, "{\"error\":\"rate limit\"}"));

        FusionApiException error = assertThrows(FusionApiException.class,
                () -> new FusionApiClient("test-key", baseUrl).ping());

        assertEquals(429, error.getStatusCode());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
