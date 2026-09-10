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
`ONEOFX_DB_DIR` liegt die gemeinsame Datenbank `oneofx_fusion.db` im lokalen,
von Git ignorierten Ordner `data`.

Optional lassen sich einzelne Dateien und die API-Basis-URL überschreiben:

```powershell
$env:ONEOFX_SETTINGS_DB = "C:\Daten\SETTING.db"
$env:ONEOFX_TRADING_DB = "C:\Daten\oneofx_fusion.db"
$env:ONEOFX_WPD_DB = "C:\Daten\WPD.db"
$env:BITPANDA_FUSION_BASE_URL = "https://api.fusion.bitpanda.com"
$env:ONEOFX_PROFIT_TAX_RATE_PERCENT = "42"
```

Der Steuersatz muss zwischen 0 und 100 liegen und beträgt ohne Konfiguration
weiterhin 42 Prozent. Verluste werden als negative Steuer verbucht und senken
damit die kumulierte Steuerreserve für spätere Gewinne.

API-Keys gehören nie in Java-Dateien, `.env`-Dateien oder Commits.

## Bauen und testen

```powershell
mvn test
```

Der Programmeinstieg der Oberfläche ist
`com.oneofx.fusion.tradingbot.desktop.DesktopLauncher`. Der bisherige direkte
Konsolenstart bleibt unter
`com.oneofx.fusion.tradingbot.TradingMain.oneofx` verfügbar. Vor echtem Handel
sollten zuerst nur Read-Rechte verwendet und Preise, Paare, Trading-Regeln sowie
Balance-Abgleich kontrolliert werden.

In VS Code startet `Strg+F5` direkt diesen Programmeinstieg.

## Desktop-Oberfläche und portable Version

Die Desktop-Oberfläche enthält:

- dunkles OneOfX-Dashboard mit Seitenleiste, Status-Karten und HiDPI-Skalierung
- dauerhaft erreichbare Start-/Stop-Steuerung mit Engine-Status
- mehrere getrennte Bots mit eigener Paarliste, Strategie, Status und Auswahl
- botweites Budget, Exposure-Limit sowie Grenzen für offene Positionen und Orders
- echte lokale Paper-Ausführung mit virtuellem EUR-Konto, Orders, Positionen,
  konfigurierbaren Gebühren und Slippage
- vollständige Bot-Baseconfig mit Ordertypen, Orderlaufzeiten, Cooldown,
  Take Profit, Trailing Stop-Buy, Gewinnbedingung, Zeitausstieg und DCA
- benannte Config Pools, die mehreren Handelspaaren als gemeinsame
  Ausführungsüberschreibung zugeordnet werden können
- Hinzufügen von Handelspaaren erst nach Live-Prüfung gegen den Fusion-Paarkatalog
- sicheres Entfernen: unbenutzte Paare werden gelöscht, Paare mit Positionen archiviert
- getrennte Einstellungsbereiche für Kaufstrategie und Risikomanagement
- arithmetische Grids mit festem Preisabstand und geometrische Grids mit Prozentabstand
- reaktive Grid-Vorschau mit Preisstufen, Kapitalbedarf und Handelsregelprüfung
- automatische, transaktionale Speicherung ohne Speichern-Schaltfläche
- Ansichten für offene Orders, Positionen, ungeklärte Übermittlungen und Kontostände
- lokale Warnzentrale und dauerhaftes Aktivitätsprotokoll
- geschützter API-Zugang nur für die aktuelle Sitzung

Beim ersten Start nach dem Update werden alle vorhandenen Einstellungen,
Positionen, Orderjournale und Aktivitäten automatisch dem `Standard-Bot`
zugeordnet. Die lokale Engine handelt immer nur den oben ausgewählten Bot.
Live- und Paper-Bots verwenden denselben Marktfilter sowie dieselben Grid-,
Stop-Loss- und Trailing-Stop-Regeln. Paper-Orders werden ausschließlich lokal
verbucht; aktuell simuliert die Engine EUR-Paare und vollständige Ausführungen
beim Erreichen eines Limitpreises. Das Paper-Konto lässt sich bewusst per
Schaltfläche auf das Bot-Budget zurücksetzen.

Eine bereits gebaute lokale Testversion liegt unter:

```text
dist\oneofx\oneofx.exe
```

`oneofx.exe` kann per Doppelklick gestartet werden. Für den Einsatz auf einem
USB-Stick wird der vollständige Ordner `dist\oneofx` kopiert. Datenbank und
weitere beschreibbare Daten liegen innerhalb dieses Ordners und wandern dadurch
mit dem Stick mit.

Eine neue portable Version wird unter PowerShell erzeugt mit:

```powershell
.\scripts\build-portable.ps1
```

Dafür werden Maven 3.9 sowie ein JDK ab Version 17 mit `jpackage` benötigt.

## Aktive Strategie- und Schutzregeln

Die Kaufseite wird durch MACD(12,26,9) und StochRSI(14,14,3,3) auf dem
1D-Timeframe gesteuert. Der Bot berechnet beide Indikatoren einmal pro Minute
neu und bezieht dabei ausdrücklich die aktuell laufende UTC-Tageskerze ein.
Die Freigabe kann sich deshalb innerhalb eines Tages mehrfach ändern.

| Bestätigter Zustand | Neue Grid-Buys | Vorhandene Bot-Positionen |
| --- | --- | --- |
| MACD > 0, MACD > Signallinie und StochRSI %K > %D | erlaubt, wenn `buyStatus` aktiv ist und das Budget reicht | TSL und harter Stop bleiben aktiv |
| MACD > 0, aber Histogramm nicht positiv oder %K <= %D | pausiert; offene Buy-Restmengen werden storniert | TSL und harter Stop bleiben aktiv |
| MACD <= 0 | pausiert; offene Buy-Restmengen werden storniert | werden per Market-Sell geschlossen |
| Signaldaten fehlen oder sind veraltet | pausiert; offene Buy-Restmengen werden storniert | kein MACD-Verkauf; Stops bleiben aktiv |

`currency.maxBuyAmount` ist die Gesamtgrenze der gebundenen Kaufbeträge eines
Paares, nicht die Größe einer einzelnen Order. Gezählt werden vorhandene
Bot-Positionen, offene Buy-Orders und ungeklärte Buy-Übermittlungen. Gebühren
sind darin nicht zusätzlich reserviert; das konfigurierte Limit sollte deshalb
unter der tatsächlich gewünschten Kapitalobergrenze liegen.

`tradeSettings.SL` ist der harte Stop-Abstand in Prozent je Position. `0`
deaktiviert ihn; positive und historisch negativ gespeicherte Werte werden als
Betrag interpretiert. Nach einem harten Stop sperrt `strategyState` neue Käufe
für dieses Paar 24 Stunden. TSL und MACD-Ausstieg bleiben daneben aktiv.

Wichtig: Diese Regeln gelten beim ersten Start auch für bereits in `positions`
gespeicherte Bot-Positionen. Eine MACD-Linie unter oder auf der Nulllinie oder
ein bereits verletzter `SL` kann daher unmittelbar echte Market-Sells auslösen.
Weitere Hintergründe stehen im [Strategiekonzept](docs/strategie-konzept.md).
Der aktuelle Umsetzungsstand und der noch offene Funktionsumfang der lokalen
Cryptohopper-Alternative stehen in der
[OneOfX-Local-Roadmap](docs/oneofx-local-roadmap.md).

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
