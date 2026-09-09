# OneOfX Local – Funktionsumfang und Roadmap

Stand: 9. September 2026

## Zielbild

OneOfX soll schrittweise zu einer nativen, lokal ausgeführten Alternative zu
Cryptohopper ausgebaut werden. Trading, Strategieauswertung, Simulation,
Auswertung und Verwaltung laufen auf dem eigenen Rechner. Ein Browser oder ein
Cloud-Abonnement ist für den vorgesehenen Kernbetrieb nicht erforderlich.

Als funktionale Referenz dienen die öffentlich beschriebenen Bereiche von
[Cryptohopper](https://www.cryptohopper.com/features/all-features), darunter
Bot-Konfiguration, Config Pools, Strategie-Designer, Paper Trading,
Backtesting, DCA, Trailing Orders, Portfoliofunktionen, Market Making und
Arbitrage. OneOfX übernimmt diese Konzepte nicht ungeprüft; echte Orders und
Risikoregeln müssen zur Bitpanda-Fusion-API und zur lokalen Architektur passen.

## Statuslegende

- **Umgesetzt:** produktiver Code und automatisierte Tests sind vorhanden.
- **Teilweise:** technische Grundlage ist vorhanden, die vollständige lokale
  Bedienung oder Konfiguration fehlt aber noch.
- **Offen:** noch nicht umgesetzt.
- **Zurückgestellt:** gehört ausdrücklich noch nicht zum aktuellen Auftrag.

## Bereits umgesetzt

### Desktop und Betrieb

- **Umgesetzt:** native Java-Desktop-Oberfläche im dunklen OneOfX-Design.
- **Umgesetzt:** portable Windows-Anwendung mit lokalem Datenordner.
- **Umgesetzt:** Start-/Stop-Steuerung und sichtbarer Engine-Status.
- **Umgesetzt:** API-Key wird nur für die aktuelle Sitzung übernommen.
- **Umgesetzt:** der portable Build sichert und erhält die vorhandene lokale
  SQLite-Datenbank beim Austausch der Anwendung.

### Paare und Grids

- **Umgesetzt:** neue Handelspaare werden vor dem Speichern live gegen den
  Bitpanda-Fusion-Paarkatalog geprüft.
- **Umgesetzt:** Tick-Größe, Mengenpräzision und Mindestorder werden lokal
  gespeichert. Die vom Handelsplatz unterstützten Ordertypen werden noch nicht
  als eigene Regel gespeichert.
- **Umgesetzt:** unbenutzte Paare können gelöscht werden. Paare mit
  Positions- oder ungeklärten Orderdaten werden deaktiviert und archiviert,
  damit Überwachung und Historie erhalten bleiben.
- **Umgesetzt:** arithmetische Grids mit festem Preisabstand.
- **Umgesetzt:** geometrische Grids mit festem Prozentabstand.
- **Umgesetzt:** bestehende alte Grid-Divisoren werden ohne Änderung ihres
  bisherigen Preisverhaltens migriert.
- **Umgesetzt:** gültige Änderungen werden automatisch und transaktional
  gespeichert; ein Speichern-Button ist nicht mehr erforderlich.

### Bestehende Handelslogik

- **Umgesetzt:** Live-Ausführung von Grid-Limit-Käufen über Bitpanda Fusion.
- **Umgesetzt:** paarspezifischer Kaufbetrag und Kapitalgrenze.
- **Umgesetzt:** harter Stop-Loss, Trailing Stop-Loss und persistenter
  24-Stunden-Cooldown nach einem harten Stop.
- **Umgesetzt:** MACD- und StochRSI-Filter der aktiven Strategie.
- **Umgesetzt:** persistentes Journal für gesendete beziehungsweise ungeklärte
  Kauf- und Verkaufsversuche.
- **Teilweise:** Dashboard, Positionen, Orderstatus und Performancewerte sind
  technisch beziehungsweise in SQLite vorhanden, werden aber noch nicht als
  vollständige Arbeitsbereiche dargestellt.

## Noch umzusetzen

### Phase 1 – Bot-Fundament vervollständigen

- **Offen:** Grid-Vorschau mit Preisstufen, Orderanzahl, Preisband und
  benötigtem Kapital vor der Aktivierung.
- **Offen:** mehrere voneinander unabhängige Bots mit Name, Budget,
  Handelspaaren, Strategie, Modus und eigenem Status.
- **Offen:** Paper-Trading-Modus mit virtuellen Beständen und derselben
  Strategie-Engine wie im Live-Betrieb.
- **Offen:** vollständige Ansichten für offene Orders, Positionen,
  Kontostände und das Aktivitätsprotokoll.
- **Offen:** Speicherung und Anzeige aller für ein Paar erlaubten Ordertypen.
- **Offen:** Fehler-/Warnzentrale für API-Probleme, fehlende Marktdaten,
  ungeklärte Orders und verletzte Handelsregeln.

### Phase 2 – Vollständige Bot-Konfiguration

In Anlehnung an Baseconfig und
[Config Pools](https://docs.cryptohopper.com/docs/trading-bot/what-are-config-pools):

- **Offen:** globale Basiskonfiguration je Bot.
- **Offen:** überschreibende Einstellungen für einzelne Paare und Paargruppen.
- **Teilweise:** Limit- und Market-Orders existieren in der Engine; eine freie
  Auswahl von Market-, Limit- und Stop-Orders in der Oberfläche fehlt.
- **Offen:** konfigurierbare maximale Anzahl offener Positionen sowie offener
  Kauf- und Verkaufsorders.
- **Offen:** maximale Laufzeit für Kauf- und Verkaufsorders.
- **Teilweise:** Cooldown ist vorhanden, aber noch fest auf den Stop-Fall und
  24 Stunden begrenzt.
- **Teilweise:** Take-Profit-Daten sind im Schema angelegt; eine vollständig
  konfigurierbare und getestete Take-Profit-Funktion fehlt.
- **Umgesetzt:** harter Stop-Loss und Trailing Stop-Loss als paarspezifische
  Schutzregeln.
- **Offen:** Trailing Stop-Buy.
- **Offen:** Option „nur mit Gewinn verkaufen“.
- **Offen:** zeitgesteuertes Schließen von Positionen.
- **Teilweise:** Grid-Nachkäufe existieren; eine eigenständige DCA-Funktion mit
  maximalen Nachkäufen, Triggern und variabler Ordergröße fehlt.
- **Teilweise:** Kapitallimit je Paar ist vorhanden; Bot-weite Portfolio-,
  Exposure- und Positionslimits fehlen.

### Phase 3 – Strategie-Designer

In Anlehnung an den
[Strategy Builder](https://docs.cryptohopper.com/docs/my-library/set-up-strategy-with-strategy-builder):

- **Teilweise:** MACD, RSI, StochRSI, EMA/SMA, Bollinger Bands, ATR, CCI und
  Volumenberechnungen sind teilweise bereits als Code vorhanden.
- **Offen:** visueller Regelbaukasten ohne Programmierung.
- **Offen:** Candlestick-Muster und einheitliche, frei wählbare Timeframes.
- **Offen:** verschachtelte UND-/ODER-Verknüpfungen.
- **Offen:** Kauf-, Verkauf-, Sperr- und Bestätigungsbedingungen.
- **Offen:** Mindestanzahl bestätigender Signale.
- **Offen:** Strategien lokal speichern und duplizieren.
- **Offen:** Strategiezuteilung nach Bot, Paargruppe oder Marktphase.
- **Offen:** nachvollziehbare Erklärung, warum ein Signal ausgelöst oder
  verworfen wurde.
- **Zurückgestellt:** Strategie-Import und -Export als Paket; dies gehört zur
  vorerst ausgeschlossenen lokalen Copy-Trading-/Vorlagenfunktion.

### Phase 4 – Backtesting und Optimierung

- **Offen:** historische Simulation mit derselben Entscheidungslogik wie Live
  und Paper Trading.
- **Offen:** Gebühren, Spread, Slippage, Mindestorder, Tick-Größe,
  Teilfüllungen und konservative Intrakerzen-Ausführung.
- **Offen:** Vergleich mehrerer Parameter- und Strategievarianten.
- **Offen:** Equity-Kurve, maximaler Drawdown, Trefferquote, Profit Factor,
  Erholungsdauer und Kapitalbindung.
- **Offen:** Darstellung einzelner Käufe und Verkäufe im Chart.
- **Offen:** Walk-forward- und getrennte In-/Out-of-Sample-Tests.
- **Offen:** Übernahme eines geprüften Ergebnisses in eine lokale
  Bot-Konfiguration und anschließender Paper-Test.

### Phase 5 – Trading-Terminal und Portfolio

- **Offen:** interaktive Charts mit Grid-Stufen, Orders, Positionen und
  Kauf-/Verkaufsmarkierungen.
- **Offen:** manuelle Kauf- und Verkaufsorders.
- **Offen:** Orderänderung und kontrollierte Stornierung.
- **Offen:** Positionen teilen, zusammenführen und für andere Strategien
  reservieren.
- **Offen:** vollständiger Handelsverlauf mit Suche, Filtern und CSV-Export.
- **Offen:** realisierte und unrealisierte Gewinne einschließlich Gebühren.
- **Teilweise:** Steuerwerte werden berechnet; eine vollständige
  Steuer-/Gebührenansicht und ein belastbarer Steuerexport fehlen.
- **Offen:** Performance nach Bot, Paar, Strategie und Zeitraum.
- **Offen:** konfigurierbarer Panic Button mit Vorschau der betroffenen Orders
  und Positionen.
- **Teilweise:** Bestände und Balance werden von Fusion gelesen; eine sichere,
  sichtbare Synchronisierung und Zuordnung vorhandener Bestände fehlt.

### Phase 6 – Lokale Automatisierung und Profiwerkzeuge

- **Offen:** lokale Wenn-dann-Trigger.
- **Offen:** Zeitpläne, Handelszeiten und Pausenfenster.
- **Offen:** lokale Desktop-Benachrichtigungen.
- **Teilweise:** Marktphasen werden intern erkannt; Konfiguration, Anzeige und
  automatischer Strategiewechsel fehlen.
- **Offen:** Portfolio-Rebalancing.
- **Offen:** Market-Making-Modus mit Inventar-, Spread- und Verlustgrenzen.
- **Offen:** Dreiecksarbitrage innerhalb einer Börse.
- **Offen:** exchangeübergreifende Arbitrage nach einer späteren Anbindung
  weiterer Börsen.

## Ausdrücklich zurückgestellt

Die folgenden Cloud-Entsprechungen werden vorerst nicht umgesetzt und zählen
nicht zum aktiven Backlog der Phasen oben:

- Marketplace beziehungsweise lokale Vorlagenbibliothek mit Import und Export.
- Copy Trading beziehungsweise importierbare Bot- und Strategiepakete.
- externe Signale über Webhook, Datei oder TradingView.
- Cloud-Konto beziehungsweise lokales Benutzerprofil.
- Cloud-Datenbank beziehungsweise formal versionierte SQLite-Datenbank.
- Cloud-KI beziehungsweise optionales lokales Analysemodul.
- mobile App.

## Empfohlene nächste Reihenfolge

1. Grid-Vorschau sowie vollständige Order-, Positions- und Ereignisansichten.
2. Mehrbot-Datenmodell und botweite Risiko-/Kapitalgrenzen.
3. Gemeinsame Ausführungsabstraktion für Live und Paper Trading.
4. vollständige Baseconfig und Config Pools.
5. Strategie-Designer mit erklärbaren Signalen.
6. Backtest-Engine und Performanceanalyse.
7. Trading-Terminal, Portfoliofunktionen und erst danach Market Making oder
   Arbitrage.

Market Making und Arbitrage sollten erst nach Paper Trading, Backtesting,
Exposure-Limits und einer belastbaren Orderabstimmung aktiviert werden. Sie
erhöhen Orderfrequenz, Kapitalbindung und Betriebsrisiko deutlich.
