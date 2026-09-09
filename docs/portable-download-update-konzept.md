# Konzept: Portable Desktop-Version, Updates und mehrere Börsen

Stand: 9. September 2026

## Ziel

OneOfX soll zunächst als portable Windows-Anwendung über eine Webseite angeboten werden. Die Anwendung soll ohne Installation sowohl aus einem Desktop-Ordner als auch direkt von einem USB-Stick funktionieren. Java, Programmdateien und SQLite werden vollständig mitgeliefert.

Die erste veröffentlichte Version unterstützt Bitpanda Fusion. Architektur und Datenmodell werden jedoch bereits so aufgebaut, dass später weitere Börsen ergänzt werden können.

## Portable Programmstruktur

Der Download wird als ZIP-Datei angeboten und in einen frei wählbaren Ordner entpackt:

```text
OneOfX/
├── OneOfX.exe
├── updater/
├── app/
├── runtime/
├── data/
│   └── oneofx_fusion.db
├── config/
├── logs/
└── backups/
```

- `OneOfX.exe` startet Launcher, Updateprüfung und Anwendung.
- `runtime/` enthält eine passende Java-Laufzeit.
- `app/` enthält die eigentlichen Programmdateien.
- `data/` enthält die SQLite-Datenbank.
- `config/` enthält nicht geheime Programmeinstellungen.
- `logs/` enthält technische und handelsbezogene Protokolle.
- `backups/` enthält automatische Datenbanksicherungen.

Alle Pfade werden relativ zum Programmordner aufgelöst. Das aktuelle Arbeitsverzeichnis des Betriebssystems darf dafür nicht verwendet werden. So bleibt dieselbe Anwendung auf dem Desktop und auf einem USB-Stick funktionsfähig.

Die erste Veröffentlichung wird für Windows x64 erstellt. Andere Betriebssysteme und Prozessorarchitekturen benötigen jeweils eigene Downloads.

## Veröffentlichung über die Webseite

Auf der Webseite werden mindestens folgende Dateien bereitgestellt:

- aktuelle stabile portable ZIP-Version
- Versionsinformationen und Änderungsprotokoll
- digital signierte Updatebeschreibung
- signierte Updatepakete
- Installations- und Sicherheitshinweise

Die Anwendung wird als eigenständiges App-Image inklusive Java-Laufzeit gebaut. Zusätzlich kann später eine klassische Installer-Version angeboten werden. Portable Version und Installer verwenden denselben Programmcode.

## Updateverfahren

Der Launcher prüft beim Start oder auf Wunsch des Benutzers eine Updatebeschreibung über HTTPS. Diese enthält mindestens:

```json
{
  "version": "1.1.0",
  "channel": "stable",
  "download": "https://example.org/downloads/oneofx-1.1.0.zip",
  "sha256": "...",
  "minimumDatabaseVersion": 2,
  "signature": "..."
}
```

Ein Update läuft in dieser Reihenfolge ab:

1. Verfügbarkeit und Version prüfen.
2. Sicherstellen, dass keine Orderübermittlung läuft.
3. Trading-Engine kontrolliert pausieren.
4. SQLite-Datenbank sichern.
5. Update in ein temporäres Verzeichnis herunterladen.
6. Prüfsumme und digitale Signatur kontrollieren.
7. Neue Programmversion in ein separates Versionsverzeichnis installieren.
8. Datenbankmigrationen in einer Transaktion ausführen.
9. Neue Version starten und einen Gesundheitstest durchführen.
10. Bei einem Fehler automatisch zur vorherigen Version zurückkehren.

Benutzerdaten dürfen bei einem Update nicht überschrieben werden. Ausgetauscht werden nur Programm- und Laufzeitdateien. Datenbank, Konfiguration, API-Zugangsdaten, Protokolle und Backups bleiben erhalten.

Die Oberfläche bietet:

- automatische Updateprüfung beim Start
- Schaltfläche „Nach Updates suchen“
- Anzeige der installierten und verfügbaren Version
- manuelle oder automatische Installation
- stabile und optionale Beta-Updatekanäle
- Änderungsprotokoll vor der Installation

Auf einem USB-Stick muss vor dem Update geprüft werden, ob Schreibrechte und ausreichend freier Speicher vorhanden sind. Der Stick darf während eines Updates oder Datenbankzugriffs nicht entfernt werden.

## Datenbankmigrationen

SQLite erhält eine Tabelle zur Versionsverwaltung:

```sql
CREATE TABLE schemaVersion (
    version INTEGER PRIMARY KEY,
    appliedAt TEXT NOT NULL,
    applicationVersion TEXT NOT NULL
);
```

Jede Schemaänderung wird als nummerierte, additive Migration ausgeliefert. Migrationen müssen:

- in einer Transaktion laufen
- vorab eine Datenbanksicherung erzeugen
- vorhandene Handelsdaten erhalten
- wiederholbar geprüft werden können
- bei einem Fehler vollständig zurückgerollt werden

## Sicherheitskonzept

- Paper-Trading ist bei einer Neuinstallation der Standard.
- Live-Trading muss ausdrücklich aktiviert werden.
- API-Keys werden niemals im Download mitgeliefert.
- API-Keys werden nicht unverschlüsselt in SQLite oder Konfigurationsdateien gespeichert.
- Für portable Nutzung wird ein vom Benutzer vergebenes Hauptpasswort verwendet.
- Der lokale Schlüsselspeicher wird mit einem geeigneten passwortbasierten Verfahren und authentifizierter Verschlüsselung geschützt.
- Programm- und Updatepakete werden digital signiert.
- Schreibvorgänge an Orderjournal und Datenbank müssen absturzsicher bleiben.
- Ein sichtbarer Not-Aus pausiert neue Orders, ohne die Überwachung bestehender Positionen zu beenden.

Eine nur an einen einzelnen Windows-Benutzer gebundene Schlüsselablage eignet sich nicht als alleinige Lösung, wenn derselbe USB-Stick auf mehreren Computern eingesetzt werden soll.

## Architektur für mehrere Börsen

Trading-Logik, Oberfläche, Datenbank und Börsenanbindung werden getrennt. Die Trading-Engine verwendet ausschließlich eine gemeinsame Börsenschnittstelle:

```java
public interface ExchangeAdapter {
    MarketPrice getPrice(String symbol);
    List<Candle> getCandles(String symbol, Timeframe timeframe, int limit);
    AccountBalance getBalance();
    ExchangeOrder placeOrder(OrderRequest request);
    ExchangeOrder cancelOrder(String orderId);
    ExchangeOrder getOrderStatus(String orderId);
    TradingRules getTradingRules(String symbol);
    ExchangeCapabilities getCapabilities();
}
```

Geplante Adapterstruktur:

```text
ExchangeAdapter
├── BitpandaFusionAdapter
├── BinanceAdapter
├── KrakenAdapter
└── weitere Adapter
```

Jeder Adapter verarbeitet selbstständig:

- Authentifizierung
- API-Endpunkte und Rate-Limits
- Symbol- und Paarformatierung
- Mindestmengen und Preisraster
- unterstützte Ordertypen
- Gebühreninformationen
- börsenspezifische Fehlerzustände

Die Strategie darf keine börsenspezifischen Klassen verwenden. Sie arbeitet mit einheitlichen internen Modellen. Nicht jede Börse bietet dieselben Funktionen; deshalb beschreibt `ExchangeCapabilities`, welche Ordertypen, Marktdaten und Schutzfunktionen verfügbar sind.

## Erweiterung des Datenmodells

Die Datenbank wird langfristig um folgende fachliche Zuordnungen erweitert:

- Börsen
- Börsenkonten
- verschlüsselte Zugangsdaten
- Handelspaare pro Börse
- Strategien pro Konto und Handelspaar
- börsenspezifische Trading-Regeln
- Orders mit Börsen- und Konto-ID
- Positionen mit Börsen- und Konto-ID

Jeder externe Auftrag benötigt einen zusammengesetzten internen Bezug aus Börse, Konto und externer Order-ID. Dadurch können identische Order-IDs verschiedener Börsen nicht kollidieren.

## Technische Module

```text
oneofx-domain
├── Strategien
├── Indikatoren
├── Risikoentscheidungen
└── gemeinsame Modelle

oneofx-exchange-api
└── ExchangeAdapter

oneofx-exchange-bitpanda
└── Bitpanda-Fusion-Implementierung

oneofx-persistence-sqlite
├── Repositories
└── Migrationen

oneofx-desktop
├── Oberfläche
├── Trading-Steuerung
└── lokale Konfiguration

oneofx-launcher
├── Start
├── Updateprüfung
└── Rollback
```

Diese Trennung erleichtert spätere Desktop-, Server- und Android-Versionen.

## Umsetzung in Phasen

### Phase 1: Technische Grundlage

- bestehende Trading-Logik vom Programmeinstieg trennen
- einheitliche Konfigurations- und Datenzugriffsschicht einführen
- portable Basispfade umsetzen
- Datenbankmigrationen versionieren
- Bitpanda-Code hinter `ExchangeAdapter` verschieben

### Phase 2: Desktop-Oberfläche

- Dashboard und Bot-Status
- Start, Pause und kontrolliertes Beenden
- Paper-/Live-Modus
- Strategie-, Risiko- und Paar-Einstellungen
- Positionen, Orders, Historie und Protokolle
- Validierung aller Einstellungen

### Phase 3: Portable Veröffentlichung

- eigenständige Windows-x64-Version inklusive Java bauen
- ZIP-Paket erzeugen
- Start auf Desktop und USB-Stick testen
- Datenbankbackup und Wiederherstellung testen
- Downloadseite und Dokumentation bereitstellen

### Phase 4: Sicherer Updater

- Launcher und Versionsprüfung
- signierte Updatebeschreibung
- Download, Prüfsumme und Signaturprüfung
- Datenbankmigration und Backup
- Gesundheitstest und automatischer Rollback

### Phase 5: Weitere Börsen

- Datenmodell um Börse und Konto erweitern
- zweiten Adapter als Architekturtest implementieren
- Capability-Prüfung in Oberfläche und Strategie integrieren
- börsenübergreifende Tests für Orders, Rundung und Fehlerfälle erstellen

## Abnahmekriterien der ersten Veröffentlichung

Die erste Download-Version ist bereit, wenn:

- sie auf einem Windows-x64-PC ohne installiertes Java startet
- sie aus einem Desktop-Ordner und von einem USB-Stick funktioniert
- ausschließlich relative, portable Datenpfade verwendet werden
- ein Programmupdate keine Benutzerdaten überschreibt
- ein fehlgeschlagenes Update automatisch zurückgerollt wird
- die Datenbank vor Updates automatisch gesichert wird
- Paper-Trading standardmäßig aktiv ist
- Live-Trading eine ausdrückliche Bestätigung verlangt
- API-Keys verschlüsselt gespeichert werden
- bestehende Orders und Positionen nach einem Neustart weiter überwacht werden
- der Bitpanda-Adapter keine Abhängigkeit der Strategie auf Bitpanda-Klassen erzwingt

## Nicht Bestandteil der ersten Phase

- Android-App
- Cloudbetrieb
- automatische Synchronisation zwischen mehreren Geräten
- gleichzeitiges Trading auf mehreren Börsen
- öffentlicher Marktplatz für externe Börsenmodule

Diese Funktionen werden architektonisch vorbereitet, aber erst nach einer stabilen und sicher getesteten Desktop-Version umgesetzt.
