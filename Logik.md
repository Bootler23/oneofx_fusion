# LTC/EUR Trading Bot - Logik und Systemdokumentation

Ein automatisierter Kryptowährungs-Trading-Bot für das LTC/EUR-Handelspaar auf der Binance-Exchange. Dieser Bot implementiert eine Grid-Trading-Strategie mit fortschrittlichem Risikomanagement und umfassender Datenanalyse.

## 📋 Inhaltsverzeichnis

- [Überblick](#überblick)
- [Hauptfunktionen](#hauptfunktionen)
- [Systemarchitektur](#systemarchitektur)
- [Technische Indikatoren](#technische-indikatoren)
- [Datenbank-Schema](#datenbank-schema)
- [Installation](#installation)
- [Konfiguration](#konfiguration)
- [Trading-Strategie](#trading-strategie)
- [Risikomanagement](#risikomanagement)
- [Module-Übersicht](#module-übersicht)
- [Nutzung](#nutzung)
- [Monitoring](#monitoring)

## 🚀 Überblick

Dieser Trading Bot ist ein vollautomatisches System, das kontinuierlich den LTC/EUR-Markt überwacht und basierend auf einer Grid-Trading-Strategie Kauf- und Verkaufsorder platziert. Das System verfolgt ein systematisches Risikomanagement und dokumentiert alle Trades in lokalen SQLite-Datenbanken für umfassende Analyse und Reporting.

### Kernfeatures:
- **Automatisierte Grid-Trading-Strategie** mit dynamischen Kauf-/Verkaufspunkten
- **Kontinuierliches Marktmonitoring** mit Echtzeit-Preisüberwachung
- **Intelligentes Risikomanagement** mit Stop-Loss und Take-Profit-Mechanismen
- **Umfassende Datenanalyse** mit MACD-Indikator und Markttrend-Erkennung
- **Vollständige Trade-Dokumentation** in lokalen SQLite-Datenbanken
- **Automatische Verlustbegrenzung** durch täglichen Verkauf der Position mit größtem Verlust

## 🛠 Hauptfunktionen

### Trading-Engine
- **Grid-basierte Order-Platzierung**: Automatische Kauf-Orders bei fallenden Kursen
- **Dynamische Profit-Ziele**: Verkauf bei +1.31% Gewinn (konfigurierbar)
- **All-Time-High Tracking**: Überwachung und Anpassung an neue Höchststände
- **Order-Status-Monitoring**: Kontinuierliche Überwachung aller offenen Orders

### Risikomanagement
- **Tägliche Verlustbegrenzung**: Automatischer Verkauf der Position mit größtem Verlust
- **Balance-Überwachung**: Kontinuierliche Überwachung des EUR- und BNB-Guthabens
- **Fee-Management**: Automatische Berücksichtigung von Trading-Gebühren
- **Steuerberechnung**: Integrierte Gewinn-/Verlustberechnung nach Steuern

### Technische Analyse
- **MACD-Indikator**: Momentum-Analyse für Markttrend-Erkennung
- **EMA-Berechnungen**: Exponential Moving Averages für Trend-Analyse
- **Candlestick-Analyse**: Historische Kursdatenanalyse
- **Price Action Monitoring**: Kontinuierliche Preisbewegungsanalyse

## 🏗 Systemarchitektur

### Komponenten-Diagramm
```
┌─────────────────────────────────────────────────────────────────┐
│                     LTC/EUR Trading Bot                        │
├─────────────────────────────────────────────────────────────────┤
│  TradingMain (LTC_EUR_Live)                                     │
│  ├── Market Monitoring                                          │
│  ├── Order Management                                           │
│  └── Risk Management                                            │
├─────────────────────────────────────────────────────────────────┤
│  Core Modules:                                                  │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │ BuyOrderProcess │ │ SellOrderProcess│ │   Indicators    │   │
│  │ - Ticker        │ │ - SellOrders    │ │ - MACD          │   │
│  │ - BuyOrders     │ │ - Update        │ │ - EMA           │   │
│  │ - CheckStatus   │ │ - SELL          │ │ - Market Data   │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│  Database Layer (SQLite):                                      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐  │
│  │   ATH.db    │ │   POS.db    │ │   HIST.db   │ │  WPD.db  │  │
│  │ All-Time    │ │ Positions   │ │ History     │ │ Daily    │  │
│  │ Highs       │ │ Active      │ │ Completed   │ │ Summary  │  │
│  │             │ │ Orders      │ │ Trades      │ │          │  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘  │
├─────────────────────────────────────────────────────────────────┤
│  External APIs:                                                 │
│  └── Binance REST API (Market Data, Trading, Account Info)     │
└─────────────────────────────────────────────────────────────────┘
```

## 📊 Technische Indikatoren

### MACD (Moving Average Convergence Divergence)
Der Bot verwendet den MACD-Indikator für Momentum-Analyse:

```java
// Standard MACD-Parameter
FAST_PERIOD = 12    // Schnelle EMA
SLOW_PERIOD = 26    // Langsame EMA  
SIGNAL_PERIOD = 9   // Signal-Linie EMA
```

**MACD-Komponenten:**
- **MACD-Linie**: Differenz zwischen 12-EMA und 26-EMA
- **Signal-Linie**: 9-EMA der MACD-Linie
- **Histogramm**: Differenz zwischen MACD-Linie und Signal-Linie

**Trading-Signale:**
- **Bullish Crossover**: MACD-Linie kreuzt Signal-Linie von unten
- **Bearish Crossover**: MACD-Linie kreuzt Signal-Linie von oben
- **Divergenzen**: Abweichungen zwischen Preis und MACD-Bewegung

### Exponential Moving Averages (EMA)
- Verwendung verschiedener EMA-Perioden für Trend-Erkennung
- Dynamische Anpassung der Grid-Levels basierend auf EMA-Werten
- Unterstützung für verschiedene Zeitrahmen (1H, 4H, 1D)

## 🗄 Datenbank-Schema

Das System verwendet vier SQLite-Datenbanken zur Datenverwaltung:

### 1. ATH.db (All-Time Highs)
```sql
CREATE TABLE ATH (
    Währung TEXT PRIMARY KEY,
    AllTimeHigh REAL,
    LPP REAL,               -- Last Price Point
    EURBalance REAL,
    Grid INTEGER,
    Value REAL
);
```

### 2. POS.db (Aktive Positionen)
```sql
CREATE TABLE POS (
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    BuyOrderId INTEGER,
    OrderPrice REAL,
    Qty TEXT,
    BuyAmount REAL,
    BuyPrice REAL,
    Währung TEXT,
    BuyDate TEXT,
    BuyTime TEXT,
    Status INTEGER          -- 0: Pending, 1: Filled, 5: Error, 7: Processing
);
```

### 3. HIST.db (Trading-Historie)
```sql
CREATE TABLE HIST (
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    BuyOrderId INTEGER,
    SellOrderId INTEGER,
    Quantity REAL,
    Währung TEXT,
    BuyPrice REAL,
    SellPrice REAL,
    Gewinn REAL,
    Tax REAL,
    Fee REAL,
    GewinnAfterTax REAL,
    LossAfterTax REAL,
    BuyDate TEXT,
    BuyTime TEXT,
    SellDate TEXT,
    SellTime TEXT,
    Status INTEGER,
    Split TEXT
);
```

### 4. WPD.db (Tägliche Performance)
```sql
CREATE TABLE WPD (
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    GewinnAfterTax REAL,
    TotalTax REAL,
    TotalGewinn REAL,
    TotalFee REAL,
    LossAfterTax REAL,
    TotalRows INTEGER,
    Date TEXT,
    Status INTEGER,
    SellPerDayAVG REAL,
    RRR REAL                -- Risk-Reward Ratio
);
```

## 💾 Installation

### Voraussetzungen
- Java 8 oder höher
- Maven 3.6+
- SQLite JDBC Driver
- Binance API-Schlüssel

### Setup-Schritte

1. **Repository klonen:**
```bash
git clone <repository-url>
cd trading_bot
```

2. **Maven-Dependencies installieren:**
```bash
mvn clean install
```

3. **Datenbank-Verzeichnis erstellen:**
```bash
mkdir C:\TradingBot\SQLiteStudio\Datenbanken\LTC_EUR\
```

4. **Konfiguration anpassen:**
   - API-Schlüssel in `Settings/bnb.java` konfigurieren
   - Datenbankpfade in `Database/dbUrl.java` überprüfen

## ⚙️ Konfiguration

### API-Konfiguration (Settings/bnb.java)
```java
public class bnb {
    private static final String API_KEY = "your_binance_api_key";
    private static final String SECRET_KEY = "your_binance_secret_key";
    
    public static BinanceApiRestClient getClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(API_KEY, SECRET_KEY);
        return factory.newRestClient();
    }
}
```

### Trading-Parameter (Settings/set.java)
```java
public class set {
    public static final double PROFIT_PERCENTAGE = 1.31;  // 1.31% Gewinn-Ziel
    public static final int GRID_SIZE = 100;              // Grid-Größe für Orders
    public static final double MIN_ORDER_SIZE = 10.0;     // Minimum EUR pro Order
}
```

### Datenbank-Pfade (Database/dbUrl.java)
```java
public class dbUrl {
    private static final String BASE_PATH = "jdbc:sqlite:C:/TradingBot/SQLiteStudio/Datenbanken/LTC_EUR/";
    
    public static String getATH() { return BASE_PATH + "ATH_LTCEUR.db"; }
    public static String getPOS() { return BASE_PATH + "POS_LTCEUR.db"; }
    public static String getHIST() { return BASE_PATH + "POS_LTCEUR_HIST.db"; }
    public static String getWPD() { return BASE_PATH + "WPD_LTCEUR.db"; }
}
```

## 📈 Trading-Strategie

### Grid-Trading-Algorithmus

Der Bot implementiert eine sophisticated Grid-Trading-Strategie:

1. **ATH-basierte Grid-Generierung:**
   - Ausgehend vom All-Time-High wird ein Grid von Kauf-Levels erstellt
   - Jedes Level ist um 1/Grid-Größe% niedriger als das vorherige
   - Standard Grid-Größe: 100 (entspricht 0.01% pro Level)

2. **Dynamische Order-Platzierung:**
```java
// Pseudocode für Buy-Order-Logik
while (BuyOrderCalc) {
    Ath = Ath - ((Ath / 100) / grid);
    BuyPrice = round.two(Ath);
    
    if (TickerPrice >= BuyPrice) {
        // Order platzieren
        setBuyOrder(currency, buyAmount, buyPrice);
        break;
    }
}
```

3. **Gewinn-Realisierung:**
   - Verkauf bei +1.31% Gewinn vom Kaufpreis
   - Berücksichtigung von Trading-Fees und Steuern
   - Automatische Übertragung in HIST-Datenbank

### Order-Management-Zyklus

```
1. Market Price Monitoring (alle 1 Sekunde)
   ↓
2. ATH Update Check
   ↓  
3. Buy Amount Calculation
   ↓
4. Buy Order Placement (bei Grid-Triggern)
   ↓
5. Order Status Monitoring
   ↓
6. Sell Order Placement (bei +1.31% Profit)
   ↓
7. Trade History Recording
   ↓
8. Risk Management Check
   ↓
9. Repeat Cycle
```

## 🛡 Risikomanagement

### Automatische Verlustbegrenzung
- **Täglich um 22:00 Uhr**: Verkauf der Position mit größtem Verlust
- **Minimum Order Size**: 10 EUR pro Position
- **Stop-Loss-Mechanismus**: Bei extremen Marktbewegungen

### Balance-Management
```java
// Balance-Überwachung
public static void checkBNB_Balance(String currencyPair, String asset, BinanceApiRestClient client) {
    // Überprüfung des BNB-Guthabens für Fee-Zahlung
    // Automatischer Kauf von BNB bei niedrigem Guthaben
}
```

### Steuer- und Fee-Berechnung
```java
public static double getTaxe(String Quantity, String BuyPrice, double SellPrice) {
    double profit = getProfit(SellPrice, Double.parseDouble(BuyPrice));
    double revenue = Double.parseDouble(Quantity) * profit;
    return revenue * 0.25; // 25% Kapitalertragsteuer
}
```

## 📦 Module-Übersicht

### BuyOrderProcess/
- **`BuyOrderPocess.java`**: Hauptlogik für Kauf-Order-Platzierung
- **`BuyAmountFunktion.java`**: Berechnung der optimalen Kauf-Mengen
- **`CheckOrderStatus.java`**: Überwachung des Order-Status
- **`Ticker.java`**: Echtzeit-Preisdaten-Abruf

### SellOrderProcess/
- **`SellOrderProcess.java`**: Verkaufs-Order-Management
- **`Update.java`**: Trade-Informationen-Update
- **`SELL.java`**: Gewinn-/Verlust-Berechnungen

### Indicator/
- **`MACD.java`**: MACD-Indikator-Implementierung mit Ta4j
- **`Merge.java`**: Daten-Aggregation und -Analyse
- **`Update.java`**: Indikator-Updates

### SQL_Database/
- **`ATHSQL.java`**: All-Time-High Datenbank-Operationen
- **`POSSQL.java`**: Positions-Datenbank-Management
- **`HISTSQL.java`**: Trading-Historie-Verwaltung
- **`WPDSQL.java`**: Tägliche Performance-Tracking

### HelperFunctions/
- **`Time.java`**: Zeitstempel-Funktionen
- **`sleep.java`**: Timing-Hilfsfunktionen
- **`round.java`**: Rundungs-Funktionen
- **`Asset.java`**: Balance-Management

### Settings/
- **`bnb.java`**: Binance API-Client-Konfiguration
- **`set.java`**: Trading-Parameter-Einstellungen

## 🚀 Nutzung

### Bot starten
```java
// Hauptklasse ausführen
java com.binance.api.tradingbot.TradingMain.LTC_EUR_Live
```

### Programm-Flow
1. **Initialisierung**: Laden der Konfiguration und Datenbankverbindung
2. **Market Monitoring**: Kontinuierliche Preisüberwachung
3. **Order Processing**: Automatische Order-Platzierung basierend auf Grid-Strategie
4. **Risk Management**: Tägliche Verlustbegrenzung und Balance-Checks
5. **Reporting**: Automatische Performance-Dokumentation

### Überwachung während des Betriebs
- **Console Output**: Detaillierte Logs aller Trading-Aktivitäten
- **Database Monitoring**: Echtzeit-Updates in SQLite-Datenbanken
- **Error Handling**: Automatische Fehlerbehandlung mit Retry-Mechanismen

## 📊 Monitoring

### Performance-Metriken
- **Täglicher Gewinn/Verlust**: Automatische Berechnung nach Steuern
- **Risk-Reward Ratio**: Verhältnis von Gewinnen zu Verlusten
- **Trading-Volumen**: Tracking des gehandelten Volumens
- **Success Rate**: Prozentsatz profitabler Trades

### Logging und Debugging
- **Umfassende Logs**: Alle Trading-Aktivitäten werden protokolliert
- **Error Tracking**: Automatische Fehlerprotokollierung
- **Performance Monitoring**: Kontinuierliche Systemleistungsüberwachung

### Dashboard-Daten
```sql
-- Tägliche Performance abfragen
SELECT Date, GewinnAfterTax, TotalTax, RRR 
FROM WPD 
ORDER BY Date DESC 
LIMIT 30;

-- Aktive Positionen anzeigen
SELECT Currency, COUNT(*) as Positions, SUM(BuyAmount) as TotalInvested
FROM POS 
WHERE Status IN (0,1) 
GROUP BY Currency;
```

## 🔍 Detaillierte Trading-Logik

### Buy-Order-Algorithmus

```java
public static void setBuyOrder(String CurrencyPair, String EURO, BinanceApiRestClient client, List<Double> LivePrice) {
    double BuyAmount;
    double Ath = ATHSQL.getAllTimeHigh(CurrencyPair);
    double unten = POSSQL.getLastPrice(CurrencyPair, LivePrice);
    double BuyPrice;
    int Count = 0;
    boolean BuyOrderCalc = true;
    double TickerPrice = LivePrice.get(0);
    int grid = set.getGridforCurrency(CurrencyPair);

    while (BuyOrderCalc) {
        Ath = Ath - ((Ath / 100) / grid);
        BuyPrice = round.two(Ath);

        if (TickerPrice >= BuyPrice) {
            Count++;
            if (Count >= 3) {
                BuyOrderCalc = false;
            }
        }
        // ... weitere Logik
    }
}
```

### Sell-Order-Algorithmus

```java
public static void setSellOrder(String currency, double percent, BinanceApiRestClient client, List<String> getDataRecords, List<Double> LivePrice) {
    for (String record : getDataRecords) {
        String[] data = record.split(", ");
        double buyPrice = Double.parseDouble(data[4]);
        double sellPrice = buyPrice * (1 + percent / 100);
        
        if (LivePrice.get(0) >= sellPrice) {
            // Verkaufsorder platzieren
            placeSellOrder(client, data, sellPrice);
        }
    }
}
```

### Risk-Reward-Ratio Berechnung

```java
public static double calculateRiskRewardRatio(double totalGewinn, double totalVerlust) {
    if (totalVerlust == 0) return Double.MAX_VALUE;
    return Math.abs(totalGewinn / totalVerlust);
}
```

### Automatische Verlustbegrenzung (Daily Loss Cutting)

```java
public static void executeHourly() {
    LocalDateTime now = LocalDateTime.now();
    int currentHour = now.getHour();
    int currentMinute = now.getMinute();
    
    // Täglich um 22:00 Uhr
    if (currentHour == 22 && currentMinute == 0) {
        // Finde Position mit größtem Verlust
        List<String> maxLossPosition = new ArrayList<>();
        POSSQL.getDataRecordsPOS_WithMaxInMinus("LTCEUR", maxLossPosition);
        
        if (!maxLossPosition.isEmpty()) {
            // Verkaufe die Position mit größtem Verlust
            SellAsset.Sell_Asset_with_Qty_0_0_Double_Amount(client, "LTCEUR");
        }
    }
}
```

## 💡 Erweiterte Features

### MACD-Integration für Trading-Signale

```java
public static MACDResult getMACD(BinanceApiRestClient client, String symbol, CandlestickInterval interval) {
    // Abrufen von Candlestick-Daten
    List<Candlestick> candlesticks = client.getCandlestickBars(symbol, interval, 100, null, null);
    
    // Erstelle BarSeries für Ta4j
    BarSeries series = createBarSeries(candlesticks);
    
    // Berechne MACD-Komponenten
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    EMAIndicator ema12 = new EMAIndicator(closePrice, 12);
    EMAIndicator ema26 = new EMAIndicator(closePrice, 26);
    MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
    EMAIndicator signal = new EMAIndicator(macd, 9);
    
    // Berechne Histogramm
    double macdValue = macd.getValue(series.getEndIndex()).doubleValue();
    double signalValue = signal.getValue(series.getEndIndex()).doubleValue();
    double histogram = macdValue - signalValue;
    
    return new MACDResult(macdValue, signalValue, histogram);
}
```

### Balance-Synchronisation

```java
public static void CompareBalanceInSQLWithBinanceBalance(String EURO, BinanceApiRestClient client) {
    // Hole aktuelles EUR-Guthaben von Binance
    Account account = client.getAccount();
    double binanceBalance = Double.parseDouble(getEUR_Account_Balance(EURO, account));
    
    // Hole gespeicherten Wert aus Datenbank
    double sqlBalance = SETSQL.getEURBalance();
    
    // Vergleiche und aktualisiere bei Abweichungen
    if (Math.abs(binanceBalance - sqlBalance) > 0.01) {
        SETSQL.updateEURBalance(binanceBalance);
        System.out.println("Balance synchronisiert: " + binanceBalance + " EUR");
    }
}
```

### Fehlerbehandlung und Retry-Mechanismen

```java
public static void handleBinanceApiError(BinanceApiException e) {
    switch (e.getError().getCode()) {
        case -1121: // Invalid symbol
            System.err.println("Ungültiges Trading-Paar");
            break;
        case -2010: // NEW_ORDER_REJECTED
            System.err.println("Order abgelehnt: " + e.getError().getMsg());
            break;
        case -1013: // Filter failure: LOT_SIZE
            System.err.println("Ungültige Ordergröße");
            break;
        default:
            System.err.println("API-Fehler: " + e.getError().getMsg());
            // Retry nach 60 Sekunden
            sleep.for_60_seconds();
    }
}
```

## 📈 Performance-Optimierungen

### Datenbankverbindung-Pooling

```java
public class DatabasePool {
    private static final HikariConfig config = new HikariConfig();
    private static final HikariDataSource ds;
    
    static {
        config.setJdbcUrl(dbUrl.getPOS());
        config.setMaximumPoolSize(10);
        ds = new HikariDataSource(config);
    }
    
    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
```

### Asynchrone Order-Verarbeitung

```java
public class AsyncOrderProcessor {
    private static final ExecutorService executor = Executors.newFixedThreadPool(5);
    
    public static void processOrderAsync(String currency, double price, double amount) {
        executor.submit(() -> {
            try {
                BuyOrderPocess.setBuyOrder(currency, "EUR", bnb.getClient(), Arrays.asList(price));
            } catch (Exception e) {
                System.err.println("Async Order Fehler: " + e.getMessage());
            }
        });
    }
}
```

## ⚠️ Wichtige Hinweise

### Risiken
- **Marktrisiko**: Kryptowährungen sind hochvolatil
- **Technisches Risiko**: API-Ausfälle oder Netzwerkprobleme
- **Liquiditätsrisiko**: Bei extremen Marktbewegungen
- **Regulatory Risk**: Änderungen in Gesetzen und Vorschriften

### Empfehlungen
- **Backtesting**: Strategie zunächst mit historischen Daten testen
- **Kleine Startbeträge**: Mit geringen Summen beginnen
- **Kontinuierliches Monitoring**: Regelmäßige Überwachung der Performance
- **Risk Management**: Niemals mehr investieren als man verlieren kann
- **Diversifikation**: Nicht alles in eine Strategie setzen

### Support und Wartung
- **Regelmäßige Updates**: Bot-Logik entsprechend Marktbedingungen anpassen
- **Database Maintenance**: Regelmäßige Bereinigung alter Datensätze
- **API-Key Security**: Sichere Aufbewahrung der Binance API-Schlüssel
- **Backup-Strategien**: Regelmäßige Sicherung der Handelsdaten

### Troubleshooting

#### Häufige Probleme und Lösungen:

1. **Connection Timeout zu Binance API**
   ```java
   // Retry-Mechanismus implementieren
   int retryCount = 0;
   while (retryCount < 3) {
       try {
           // API-Call
           break;
       } catch (Exception e) {
           retryCount++;
           sleep.for_5_seconds();
       }
   }
   ```

2. **SQLite Database Locked**
   ```java
   // Connection-Pooling verwenden
   try (Connection conn = DatabasePool.getConnection()) {
       // Database operations
   }
   ```

3. **Insufficient Balance Errors**
   ```java
   // Balance-Check vor Order-Platzierung
   if (getEURBalance() < orderAmount) {
       System.out.println("Insufficient balance for order");
       return;
   }
   ```

---

*Dieser Trading Bot dient ausschließlich zu Bildungs- und Testzwecken. Trading mit Kryptowährungen birgt erhebliche Risiken. Verwenden Sie den Bot nur mit Geld, dessen Verlust Sie sich leisten können.*
