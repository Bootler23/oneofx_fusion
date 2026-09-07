# OneOfX für Bitpanda Fusion

Der Trading-Bot verwendet die Bitpanda Fusion REST API. Die frühere eingebettete
Binance-Clientbibliothek ist nicht mehr Bestandteil des Projekts.

## Voraussetzungen

- Java 17 oder neuer
- Maven 3.9 oder neuer
- Bitpanda-Fusion-API-Key mit `Read`-Berechtigung und, für echte Orders,
  `Trade`-Berechtigung
- die vom bisherigen Bot verwendeten SQLite-Datenbanken und Tabellen

## Konfiguration unter PowerShell

```powershell
$env:BITPANDA_FUSION_API_KEY = "DEIN_NEUER_FUSION_API_KEY"
$env:ONEOFX_DB_DIR = "C:\Pfad\zu\deinen\Datenbanken"
```

Als Alias für den API-Key wird auch `FUSION_API_KEY` akzeptiert. Ohne
`ONEOFX_DB_DIR` liegen die drei Datenbanken `SETTING.db`, `oneofx.db` und
`WPD.db` im lokalen, von Git ignorierten Ordner `data`.

Optional lassen sich einzelne Dateien und die API-Basis-URL überschreiben:

```powershell
$env:ONEOFX_SETTINGS_DB = "C:\Daten\SETTING.db"
$env:ONEOFX_TRADING_DB = "C:\Daten\oneofx.db"
$env:ONEOFX_WPD_DB = "C:\Daten\WPD.db"
$env:BITPANDA_FUSION_BASE_URL = "https://api.fusion.bitpanda.com"
```

API-Keys gehören nie in Java-Dateien, `.env`-Dateien oder Commits.

## Bauen und testen

```powershell
mvn test
```

Der Programmeinstieg ist
`com.oneofx.fusion.tradingbot.TradingMain.oneofx`. Vor echtem Handel sollten
zuerst nur Read-Rechte verwendet und Preise, Paare, Trading-Regeln sowie
Balance-Abgleich kontrolliert werden.

## Fusion-spezifisches Verhalten

- Authentifizierung erfolgt über den Header `x-api-key`.
- Paare wie `BTCEUR` werden an der API-Grenze automatisch zu `BTC-EUR`.
- Order-IDs werden als UUID-Strings gespeichert und verarbeitet.
- Orders werden asynchron angelegt; Ausführung, Gebühren und Performance werden
  erst nach einem terminalen Fill verbucht.
- Da für Fusion derzeit kein WebSocket-Marktdatenstrom dokumentiert ist, lädt
  der Bot die Ticker per gebündeltem REST-Polling.
- Ein zentraler Sliding-Window-Limiter schützt Global-, Marktdaten- und
  Order-Erstellungs-Limits mit Sicherheitsabstand.

Offizielle Dokumentation: [Fusion API](https://docs.fusion.bitpanda.com/),
[API-Einstieg](https://docs.fusion.bitpanda.com/getting-started-370709m0.md),
[Rate Limits](https://docs.fusion.bitpanda.com/rate-limits-370893m0.md).
