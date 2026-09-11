package com.oneofx.fusion.tradingbot.BuyOrderProcess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.tradingbot.grid.GridMode;
import com.oneofx.fusion.tradingbot.grid.GridSettings;
import com.oneofx.fusion.tradingbot.strategy.EntrySpacingMode;
import com.oneofx.fusion.tradingbot.strategy.StrategyEvaluation;
import com.sun.net.httpserver.HttpServer;

public class BuyOrderPocessTest {

    private static final String DATABASE_PROPERTY = "ONEOFX_TRADING_DB";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger nextOrderId = new AtomicInteger();
    private final List<JsonNode> submittedOrders = new ArrayList<>();

    private HttpServer server;
    private String previousDatabaseProperty;
    private String jdbcUrl;

    @Before
    public void setUp() throws Exception {
        previousDatabaseProperty = System.getProperty(DATABASE_PROPERTY);
        File database = temporaryFolder.newFile("buy-grid.db");
        System.setProperty(DATABASE_PROPERTY, database.getAbsolutePath());
        jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
        createDatabase();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/account/orders", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "");
                return;
            }

            JsonNode submitted = objectMapper.readTree(exchange.getRequestBody());
            synchronized (submittedOrders) {
                submittedOrders.add(submitted);
            }
            respond(exchange, 202,
                    "{\"id\":\"buy-" + nextOrderId.incrementAndGet() + "\"}");
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (previousDatabaseProperty == null) {
            System.clearProperty(DATABASE_PROPERTY);
        } else {
            System.setProperty(DATABASE_PROPERTY, previousDatabaseProperty);
        }
    }

    @Test
    public void createsExactlyTwoDifferentLimitBuysBelowTickerAndNoThirdOrder() throws Exception {
        FusionApiClient client = new FusionApiClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort());

        BuyOrderPocess.setBuyOrder("BTC-EUR", client, List.of(100.0));

        assertEquals(2, submittedOrders.size());
        JsonNode firstOrder = submittedOrders.get(0);
        JsonNode secondOrder = submittedOrders.get(1);
        assertLimitBuyBelow(firstOrder, 100.0);
        assertLimitBuyBelow(secondOrder, 100.0);
        assertNotEquals(firstOrder.get("limitPrice").asText(),
                secondOrder.get("limitPrice").asText());
        assertEquals(2, selectInt(
                "SELECT COUNT(*) FROM positions WHERE currency = 'BTC-EUR' AND Status = 0"));

        BuyOrderPocess.setBuyOrder("BTC-EUR", client, List.of(100.0));

        assertEquals("Eine dritte Pending-Order darf nicht gesendet werden",
                2, submittedOrders.size());
    }

    @Test
    public void gridDistanceJumpsFromOldAthAndStopsAtCancellationThreshold() {
        int steps = CheckOrderStatus.gridStepsBetweenLiveAndOrder(
                "BTC-EUR", 120_000.0, 23, 67_450.69, 60_000.0);

        assertEquals("Mehr als zwei Stufen muessen nicht vollstaendig ausgezaehlt werden",
                3, steps);
    }

    @Test
    public void gridDistanceKeepsTheFirstLimitLevelBelowMarket() {
        double livePrice = 67_450.69;
        double firstGridLevel = livePrice * (1.0 - 1.0 / 2300.0);

        int steps = CheckOrderStatus.gridStepsBetweenLiveAndOrder(
                "BTC-EUR", livePrice, 23, livePrice, firstGridLevel);

        assertEquals(0, steps);
    }

    @Test
    public void gridDistanceSupportsArithmeticPrices() {
        int steps = CheckOrderStatus.gridStepsBetweenLiveAndOrder(
                "BTC-EUR", 100.0,
                new GridSettings(GridMode.ARITHMETIC, 2.5), 100.0, 90.0);

        assertEquals(3, steps);
    }

    @Test
    public void doesNotCreateReplacementOutsideCancellationWindow() throws Exception {
        executeUpdate("INSERT INTO positions "
                + "(currency, BuyOrderId, OrderPrice, Status, statusCode) VALUES "
                + "('BTC-EUR', 'filled-1', 99.00, 1, 'FILLED CHECKED'),"
                + "('BTC-EUR', 'filled-2', 98.01, 1, 'FILLED CHECKED'),"
                + "('BTC-EUR', 'pending-1', 97.03, 0, 'NEW')");
        FusionApiClient client = new FusionApiClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort());

        BuyOrderPocess.setBuyOrder("BTC-EUR", client, List.of(100.0));

        assertEquals("Keine Order ausserhalb der Cancel-Grenze senden",
                0, submittedOrders.size());
        assertEquals(1, selectInt(
                "SELECT COUNT(*) FROM positions WHERE currency = 'BTC-EUR' AND Status = 0"));
        assertEquals(3, CheckOrderStatus.gridStepsBetweenLiveAndOrder(
                "BTC-EUR", 100.0, 1, 100.0, 96.06));
    }

    @Test
    public void doesNotExceedConfiguredCurrencyBudget() throws Exception {
        executeUpdate("UPDATE currency SET maxBuyAmount = 15 WHERE currency = 'BTC-EUR'");
        FusionApiClient client = new FusionApiClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort());

        BuyOrderPocess.setBuyOrder("BTC-EUR", client, List.of(100.0));

        assertEquals(1, submittedOrders.size());
        assertEquals(1, selectInt(
                "SELECT COUNT(*) FROM positions WHERE currency = 'BTC-EUR' AND Status = 0"));
    }

    @Test
    public void strategySpacingPreventsNearbyRepeatedBuy() throws Exception {
        FusionApiClient client = new FusionApiClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort());
        StrategyEvaluation signal = new StrategyEvaluation(1, "Abstand", true, false,
                false, 1, 1, EntrySpacingMode.PERCENT, 3.0, List.of());

        BuyOrderPocess.setBuyOrder("BTC-EUR", client, List.of(100.0), signal);

        assertEquals("Die zweite Grid-Stufe liegt noch innerhalb des Strategie-Abstands",
                1, submittedOrders.size());
    }

    private static void assertLimitBuyBelow(JsonNode order, double tickerPrice) {
        assertEquals("Buy", order.get("side").asText());
        assertEquals("Limit", order.get("type").asText());
        assertEquals("GTC", order.get("timeInForce").asText());
        assertTrue(Double.parseDouble(order.get("limitPrice").asText()) < tickerPrice);
        assertFalse(order.has("triggerPrice"));
    }

    private void createDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE currency (currency TEXT PRIMARY KEY, "
                    + "alltimehigh REAL, grid INTEGER, gridMode TEXT, gridSpacing REAL, "
                    + "buyAmount REAL, maxBuyAmount REAL)");
            statement.executeUpdate("INSERT INTO currency "
                    + "(currency, alltimehigh, grid, gridMode, gridSpacing, "
                    + "buyAmount, maxBuyAmount) "
                    + "VALUES ('BTC-EUR', 100.0, 1, 'GEOMETRIC', 1.0, 10.0, 500.0)");
            statement.executeUpdate("CREATE TABLE positions (currency TEXT, "
                    + "BuyOrderId TEXT PRIMARY KEY, OrderPrice REAL, Status INTEGER, statusCode TEXT, "
                    + "quantity REAL, BuyAmount REAL, BuyPrice REAL)");
            statement.executeUpdate("CREATE TABLE tradingRules (currency TEXT PRIMARY KEY, "
                    + "tickSize TEXT, stepSize TEXT, minQty TEXT, amountIncrement TEXT, "
                    + "maxOrderSize TEXT, minOrderAmount TEXT, maxOrderAmount TEXT)");
            statement.executeUpdate("INSERT INTO tradingRules VALUES "
                    + "('BTC-EUR', '0.01', '0.001', '0.001', '0.01', "
                    + "'1000', '5', '1000000')");
        }
    }

    private int selectInt(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private void executeUpdate(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
            int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
