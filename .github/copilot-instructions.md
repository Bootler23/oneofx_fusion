 # GitHub Copilot Instructions für Trading-Bot-LTC-EUR

## Projektkontext

Dies ist ein **Java-basierter Krypto-Trading-Bot** für Binance, der automatisiert LTC/EUR und andere Währungspaare handelt.

### Technologie-Stack
- **Sprache:** Java 8+
- **Build-Tool:** Maven
- **API:** Binance REST & WebSocket API
- **Datenbank:** SQL (PostgreSQL/MySQL)
- **Framework:** Plain Java (kein Spring Boot aktuell)

---

## Code-Style & Konventionen

### Naming Conventions (WICHTIG)
```java
// ✅ RICHTIG - Nach Java-Konventionen
public class TradingService { }
public class OrderRepository { }
public class CurrencySettings { }

// ❌ FALSCH - Nicht verwenden
public class set { }
public class bnb { }
public class sleep { }
```

### Methodennamen
```java
// ✅ RICHTIG - camelCase
public void processCurrency(String currency) { }
public Order getBuyOrder(Long orderId) { }

// ❌ FALSCH - Snake_case vermeiden
public void get_Currency_Pair_Price() { }
```

### Konstanten & Enums
```java
// ✅ Status-Werte als Enums statt Magic Numbers
public enum OrderStatus {
    PENDING(0),
    FILLED(1),
    PARTIALLY_FILLED(5),
    CANCELLED(7);
}

// ✅ Konstanten für Konfiguration
public static final int UPDATE_CYCLE_COUNT = 41;
public static final long POLL_INTERVAL_MS = 1000;
```

---

## Architektur-Richtlinien

### Service-Layer Pattern
Trenne Business-Logik in dedizierte Services:
```java
TradingService      // Orchestrierung der Trading-Logik
OrderService        // Order-Management (Buy/Sell)
PriceService        // Preis-Updates & Monitoring
DatabaseService     // Datenbank-Operationen
IndicatorService    // Technische Indikatoren (RSI, MACD, etc.)
NotificationService // Alerts & Logging
```

### Repository Pattern für Datenbank
```java
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByCurrencyAndStatus(String currency, OrderStatus status);
}
```

### Keine God-Classes
- Klassen sollen **single responsibility** haben
- Main-Methode nur für Bootstrapping
- Keine 200+ Zeilen Methoden

---

## Fehlerbehandlung

### Spezifische Exceptions behandeln
```java
try {
    // Trading-Logik
} catch (BinanceApiException e) {
    logger.error("Binance API Error: Code={}, Message={}", 
                 e.getError().getCode(), e.getError().getMsg());
    handleApiError(e);
} catch (SQLException e) {
    logger.error("Database Error", e);
    handleDatabaseError(e);
} catch (IOException e) {
    logger.error("Network Error", e);
    handleNetworkError(e);
} catch (IllegalArgumentException e) {
    logger.error("Invalid Input: {}", e.getMessage());
    handleValidationError(e);
}
```

### Logging statt System.out
```java
// ✅ RICHTIG - SLF4J Logger
private static final Logger logger = LoggerFactory.getLogger(TradingService.class);
logger.info("Processing currency: {}", currency);
logger.warn("Low balance detected: {}", balance);
logger.error("Order failed", exception);

// ❌ FALSCH - Nicht verwenden
System.out.println("Processing currency: " + currency);
System.err.println("Error: " + error);
```

### Retry-Mechanismus mit Exponential Backoff
```java
// Verwende Resilience4j für Retries
@Retry(name = "binanceApi", fallbackMethod = "fallbackMethod")
public TickerPrice getPrice(String symbol) {
    return client.getPrice(symbol);
}
```

---

## Datenbank-Zugriffe

### Verwende PreparedStatements
```java
// ✅ RICHTIG - Schutz vor SQL-Injection
String sql = "SELECT * FROM orders WHERE status = ? AND currency = ?";
try (PreparedStatement stmt = connection.prepareStatement(sql)) {
    stmt.setInt(1, status.getCode());
    stmt.setString(2, currency);
    ResultSet rs = stmt.executeQuery();
}

// ❌ FALSCH - String-Konkatenation
String sql = "SELECT * FROM orders WHERE status = " + status + " AND currency = '" + currency + "'";
```

### Transaktionen für zusammenhängende Operationen
```java
connection.setAutoCommit(false);
try {
    // Mehrere DB-Operationen
    orderRepository.save(buyOrder);
    balanceRepository.updateBalance(accountId, newBalance);
    historyRepository.insert(transaction);
    
    connection.commit();
} catch (SQLException e) {
    connection.rollback();
    throw e;
}
```

### Ressourcen schließen (try-with-resources)
```java
// ✅ RICHTIG
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    // Verwendung
} // Automatisches Schließen
```

---

## Trading-Logik

### Konfigurierbare Parameter
```java
// ✅ Aus Configuration laden
public class TradingConfig {
    private double minBuyAmount;      // Aus application.properties
    private double maxBuyAmount;
    private double takeProfitPercent;
    private double stopLossPercent;
    private List<String> tradingPairs;
}

// ❌ Nicht hardcoden
if (amount > 100.0) { } // Warum 100?
```

### State-Machine für Trading-Status
```java
public enum TradingState {
    INITIALIZING,
    MONITORING,
    BUYING,
    WAITING_FOR_BUY_FILL,
    SELLING,
    WAITING_FOR_SELL_FILL,
    ERROR_RECOVERY
}

// Klare State-Transitions mit Logging
public void transitionTo(TradingState newState) {
    logger.info("State transition: {} -> {}", currentState, newState);
    this.currentState = newState;
}
```

### Trading-Strategie dokumentieren
```java
/**
 * Implementiert eine Mean-Reversion-Strategie mit technischen Indikatoren.
 * 
 * BUY-SIGNAL wird generiert wenn:
 * - RSI < 30 (überverkauft)
 * - Preis unterhalb des unteren Bollinger-Bandes
 * - MACD zeigt bullischen Crossover
 * - Genug EUR-Balance verfügbar
 * 
 * SELL-SIGNAL wird generiert wenn:
 * - Gewinnziel erreicht (konfigurierbar, z.B. 2%)
 * - Stop-Loss ausgelöst (konfigurierbar, z.B. -1%)
 * - RSI > 70 (überkauft)
 * 
 * @param currency Das zu handelnde Währungspaar (z.B. "LTCEUR")
 * @param client Der Binance API Client
 * @return true wenn Trade ausgeführt wurde, false sonst
 */
```

---

## Performance & Ressourcen-Management

### WebSockets statt Polling
```java
// ✅ RICHTIG - Event-driven mit WebSocket
BinanceApiWebSocketClient wsClient = factory.newWebSocketClient();
wsClient.onAggTradeEvent("ltceur", response -> {
    handlePriceUpdate(response.getPrice());
});

// ❌ FALSCH - Polling mit Sleep
while (true) {
    sleep(1000);
    double price = client.getPrice("LTCEUR");
}
```

### Parallele Verarbeitung für mehrere Währungen
```java
// ✅ RICHTIG - Parallel mit ExecutorService
ExecutorService executor = Executors.newFixedThreadPool(currencies.size());
List<CompletableFuture<Void>> futures = currencies.stream()
    .map(currency -> CompletableFuture.runAsync(
        () -> processCurrency(currency), executor))
    .collect(Collectors.toList());
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

// ❌ FALSCH - Sequentiell
for (String currency : currencies) {
    processCurrency(currency); // Blockiert
}
```

### Connection-Pooling für Datenbank
```java
// ✅ Verwende HikariCP
HikariConfig config = new HikariConfig();
config.setJdbcUrl(dbUrl);
config.setMaximumPoolSize(10);
config.setConnectionTimeout(30000);
HikariDataSource dataSource = new HikariDataSource(config);
```

### Listen-Management in Schleifen
```java
// ✅ RICHTIG - Liste leeren oder neu erstellen
while (true) {
    List<Long> orderIds = new ArrayList<>();
    orderRepository.getOrderIds(orderIds);
    processOrders(orderIds);
}

// ❌ FALSCH - Unbegrenztes Wachstum
List<Long> orderIds = new ArrayList<>();
while (true) {
    orderRepository.getOrderIds(orderIds); // Wächst unbegrenzt!
}
```

---

## Sicherheit

### API-Keys nie im Code
```java
// ✅ RICHTIG - Environment Variables
String apiKey = System.getenv("BINANCE_API_KEY");
String secretKey = System.getenv("BINANCE_SECRET_KEY");

// ❌ FALSCH - Hardcoded
String apiKey = "abc123..."; // NIEMALS!
```

### Input-Validierung
```java
public void validateCurrency(String currency) {
    if (currency == null || !currency.matches("[A-Z]{3,6}EUR")) {
        throw new IllegalArgumentException("Invalid currency format: " + currency);
    }
}

public void validateAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Amount must be positive");
    }
    if (amount.compareTo(MAX_ORDER_SIZE) > 0) {
        throw new IllegalArgumentException("Amount exceeds maximum order size");
    }
}
```

### Rate-Limiting
```java
// ✅ Implementiere Rate-Limiter
RateLimiter rateLimiter = RateLimiter.create(10.0); // 10 Requests/Sekunde
rateLimiter.acquire();
TickerPrice price = client.getPrice(symbol);
```

---

## Testing

### Unit-Tests mit Mockito
```java
@Test
public void testBuyOrderPlacement() {
    // Arrange
    BinanceApiRestClient mockClient = mock(BinanceApiRestClient.class);
    when(mockClient.newOrder(any())).thenReturn(mockOrderResponse);
    
    OrderService service = new OrderService(mockClient);
    
    // Act
    Order result = service.placeBuyOrder("LTCEUR", new BigDecimal("100.0"));
    
    // Assert
    assertEquals(OrderStatus.PENDING, result.getStatus());
    verify(mockClient, times(1)).newOrder(any());
}
```

### Testnet-Modus für Integration-Tests
```java
// application-test.properties
binance.api.url=https://testnet.binance.vision
binance.testnet=true
```

### Keine Tests mit echtem Geld
```java
// ✅ RICHTIG - Dry-Run-Modus
if (config.isDryRun()) {
    logger.info("DRY RUN: Would place order: {}", order);
    return simulateOrderResponse(order);
} else {
    return client.newOrder(order);
}
```

---

## Monitoring & Observability

### Metriken sammeln
```java
// ✅ Micrometer-Metriken
Counter.builder("trading.orders.executed")
    .tag("currency", currency)
    .tag("side", "BUY")
    .register(meterRegistry)
    .increment();

Timer.builder("trading.operation.duration")
    .tag("operation", "processCurrency")
    .register(meterRegistry)
    .record(() -> processCurrency(currency));
```

### Health-Checks
```java
public HealthStatus checkHealth() {
    boolean dbHealthy = checkDatabaseConnection();
    boolean apiHealthy = checkBinanceApiConnection();
    boolean hasRecentTrade = lastTradeTime.isAfter(LocalDateTime.now().minusMinutes(30));
    
    if (dbHealthy && apiHealthy && hasRecentTrade) {
        return HealthStatus.UP;
    }
    return HealthStatus.DOWN;
}
```

### Alerts bei kritischen Events
```java
if (consecutiveErrors > 10) {
    alertService.sendCritical("More than 10 consecutive errors detected!");
}

if (portfolioLoss.compareTo(maxLossThreshold) > 0) {
    alertService.sendCritical("Portfolio loss exceeds threshold: " + portfolioLoss);
}
```

---

## Dokumentation

### JavaDoc für Public APIs
```java
/**
 * Platziert eine Buy-Order für das angegebene Währungspaar.
 * 
 * Diese Methode validiert zuerst die verfügbare Balance, berechnet dann
 * die optimale Order-Größe basierend auf der konfigurierten Strategie
 * und platziert die Order über die Binance API.
 * 
 * @param currency Das Währungspaar (z.B. "LTCEUR")
 * @param amount Der zu investierende Betrag in EUR
 * @param client Der authentifizierte Binance API Client
 * @return Die platzierte Order mit Order-ID und Status
 * @throws BinanceApiException wenn die Order-Platzierung fehlschlägt
 * @throws InsufficientBalanceException wenn nicht genug Balance vorhanden ist
 * @throws IllegalArgumentException wenn Parameter ungültig sind
 */
public Order placeBuyOrder(String currency, BigDecimal amount, BinanceApiRestClient client) 
    throws BinanceApiException, InsufficientBalanceException {
    // Implementation
}
```

### Inline-Kommentare für komplexe Logik
```java
// Berechne dynamischen Sell-Prozentsatz basierend auf Positions-Anzahl
// Strategie: Weniger Positionen = höherer Prozentsatz pro Position
// 1 Position -> 5%, 10 Positionen -> 0.5%
double basePercentage = 5.0;
double scalingFactor = Math.max(1, activePositions.size());
double sellPercentage = Math.max(0.5, basePercentage / scalingFactor);
```

---

## Spezifische Projekt-Regeln

### Status-Codes
Verwende **immer** die definierten Enums statt Magic Numbers:
```java
// ✅ RICHTIG
if (order.getStatus() == OrderStatus.FILLED) { }
orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING)

// ❌ FALSCH
if (order.getStatus() == 1) { }  // Was bedeutet 1?
WHERE status = 5  // Was bedeutet 5?
```

### Währungspaare
```java
// Unterstützte Paare
private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
    "LTCEUR",
    "BNBEUR",
    "BTCEUR"
);
```

### Datenbank-Tabellen
```
ATH  - All Time High tracking
HIST - Trade History
POS  - Open Positions
SET  - System Settings
WPD  - Portfolio/Performance Data
```

### Time-based statt Count-based
```java
// ✅ RICHTIG - Zeit-basiert
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(
    this::performPeriodicUpdate,
    0, 5, TimeUnit.MINUTES
);

// ❌ FALSCH - Count-basiert
if (count == 41) {  // Warum 41? Wie lange dauert das?
    performPeriodicUpdate();
}
```

---

## Was NICHT zu tun ist

❌ **Keine verschachtelten `while(true)` Schleifen**
❌ **Keine hardcodierten API-Keys oder Secrets**
❌ **Keine Magic Numbers ohne Konstanten**
❌ **Keine SQL-Injection-anfälligen String-Konkatenationen**
❌ **Keine unbehandelten Exceptions**
❌ **Keine `System.out.println()` für Logging**
❌ **Keine Klassen mit mehr als 300 Zeilen**
❌ **Keine Methoden mit mehr als 50 Zeilen**
❌ **Keine Tests mit echtem Geld auf Production-API**
❌ **Keine Listen die unbegrenzt wachsen**

---

## Prioritäten bei Code-Generierung

1. **Sicherheit** - Validierung, keine Secrets im Code
2. **Fehlerbehandlung** - Robuste Exception-Handling
3. **Lesbarkeit** - Klare Namen, Dokumentation
4. **Performance** - Effiziente Algorithmen, keine Memory-Leaks
5. **Testbarkeit** - Dependency Injection, Interfaces

---

## Beispiel einer gut strukturierten Klasse

```java
package com.binance.api.tradingbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service für Order-Management und Ausführung.
 * Verantwortlich für Buy/Sell-Orders und Status-Tracking.
 */
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal MIN_ORDER_SIZE = new BigDecimal("10.0");
    
    private final BinanceApiRestClient apiClient;
    private final OrderRepository orderRepository;
    private final BalanceService balanceService;
    private final NotificationService notificationService;
    
    public OrderService(BinanceApiRestClient apiClient,
                       OrderRepository orderRepository,
                       BalanceService balanceService,
                       NotificationService notificationService) {
        this.apiClient = apiClient;
        this.orderRepository = orderRepository;
        this.balanceService = balanceService;
        this.notificationService = notificationService;
    }
    
    /**
     * Platziert eine Buy-Order nach Validierung der Bedingungen.
     * 
     * @param currency Währungspaar (z.B. "LTCEUR")
     * @param amount Betrag in EUR
     * @return Die erstellte Order
     * @throws BinanceApiException bei API-Fehlern
     * @throws InsufficientBalanceException bei zu geringer Balance
     */
    public Order placeBuyOrder(String currency, BigDecimal amount) 
            throws BinanceApiException, InsufficientBalanceException {
        
        logger.info("Attempting to place buy order: {} {}", amount, currency);
        
        // Validierung
        validateCurrency(currency);
        validateAmount(amount);
        
        // Balance-Check
        BigDecimal availableBalance = balanceService.getAvailableBalance("EUR");
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                "Insufficient balance. Required: " + amount + ", Available: " + availableBalance
            );
        }
        
        try {
            // Order erstellen und platzieren
            NewOrder newOrder = createBuyOrder(currency, amount);
            NewOrderResponse response = apiClient.newOrder(newOrder);
            
            // In Datenbank speichern
            Order order = mapToOrder(response);
            orderRepository.save(order);
            
            logger.info("Buy order placed successfully: OrderId={}", order.getId());
            notificationService.sendOrderNotification(order);
            
            return order;
            
        } catch (BinanceApiException e) {
            logger.error("Failed to place buy order for {}: {}", currency, e.getMessage());
            throw e;
        }
    }
    
    private void validateCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3,6}EUR")) {
            throw new IllegalArgumentException("Invalid currency format: " + currency);
        }
    }
    
    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(MIN_ORDER_SIZE) < 0) {
            throw new IllegalArgumentException(
                "Amount too small. Minimum: " + MIN_ORDER_SIZE
            );
        }
    }
    
    // Weitere Methoden...
}
```

---

## Zusätzliche Ressourcen

- [Binance API Dokumentation](https://binance-docs.github.io/apidocs/)
- [Java Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)
- [Effective Java (Joshua Bloch)](https://www.oreilly.com/library/view/effective-java/9780134686097/)
- [Clean Code (Robert C. Martin)](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)

---

**Version:** 1.0  
**Letzte Aktualisierung:** 23. Oktober 2025  
**Projekt:** trading-bot-ltc-eur
