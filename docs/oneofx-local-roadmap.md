# OneOfX Local – Funktionsumfang und Roadmap

Stand: 10. September 2026

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
- **Umgesetzt:** zentral konfigurierte SQLite-Verbindungen verwenden WAL,
  Foreign Keys, `synchronous=NORMAL`, einen einheitlichen Busy-Timeout und
  `IMMEDIATE`-Schreibtransaktionen. Schreibblöcke können über einen gemeinsamen
  Commit-/Rollback-Helfer ausgeführt werden; Schemaanpassungen in häufigen
  Lesepfaden wurden entfernt beziehungsweise auf einmalige Migration begrenzt.

### Paare und Grids

- **Umgesetzt:** neue Handelspaare werden vor dem Speichern live gegen den
  Bitpanda-Fusion-Paarkatalog geprüft.
- **Umgesetzt:** Tick-Größe, Mengenpräzision und Mindestorder werden lokal
  gespeichert. Die vom Handelsplatz gemeldeten Ordertypen werden je Paar
  gespeichert, angezeigt und vor jeder Konfigurations- oder Orderausführung
  geprüft. Alte Daten behalten bis zur erneuten Paarprüfung sichere
  Kompatibilitätswerte.
- **Umgesetzt:** unbenutzte Paare können gelöscht werden. Paare mit
  Positions- oder ungeklärten Orderdaten werden deaktiviert und archiviert,
  damit Überwachung und Historie erhalten bleiben.
- **Umgesetzt:** arithmetische Grids mit festem Preisabstand.
- **Umgesetzt:** geometrische Grids mit festem Prozentabstand.
- **Umgesetzt:** bestehende alte Grid-Divisoren werden ohne Änderung ihres
  bisherigen Preisverhaltens migriert.
- **Umgesetzt:** gültige Änderungen werden automatisch und transaktional
  gespeichert; ein Speichern-Button ist nicht mehr erforderlich.
- **Umgesetzt:** reaktive Grid-Vorschau mit allen kapitalgedeckten Preisstufen,
  Ordermengen, Tick-Rundung, Mindestorderprüfung, Kapitalbedarf und Preisband.

### Bestehende Handelslogik

- **Umgesetzt:** Live-Ausführung von Grid-Limit-Käufen über Bitpanda Fusion.
- **Umgesetzt:** paarspezifischer Kaufbetrag und Kapitalgrenze.
- **Umgesetzt:** harter Stop-Loss, Trailing Stop-Loss und persistenter
  24-Stunden-Cooldown nach einem harten Stop.
- **Umgesetzt:** MACD- und StochRSI-Filter der aktiven Strategie.
- **Umgesetzt:** persistentes Journal für gesendete beziehungsweise ungeklärte
  Kauf- und Verkaufsversuche.
- **Teilweise:** grundlegende Performancewerte liegen in SQLite; eine
  vollständige Performanceanalyse nach Bot, Paar und Zeitraum fehlt noch.
- **Umgesetzt:** eigene, automatisch aktualisierte Arbeitsbereiche für offene
  Orders, aktive Positionen und ungeklärte Kauf-/Verkaufsübermittlungen.
- **Umgesetzt:** Kontostände können bei Bedarf live von Fusion geladen werden;
  verfügbare, gesperrte und gesamte Beträge werden getrennt angezeigt.
- **Umgesetzt:** lokale Warnzentrale und dauerhaftes Aktivitätsprotokoll für
  Oberfläche, Engine, Konfigurationsänderungen und Orderjournale.

## Noch umzusetzen

### Phase 1 – Bot-Fundament vervollständigen

- **Umgesetzt:** Grid-Vorschau mit Preisstufen, Orderanzahl, Preisband und
  benötigtem Kapital vor der Aktivierung.
- **Umgesetzt:** mehrere voneinander unabhängige Bots mit Name, Budget,
  Handelspaaren, Strategie, Live-/Paper-Modus und eigenem Status. Bestehende
  Installationen werden automatisch einem verlustfreien Standard-Bot zugeordnet.
- **Umgesetzt:** Paper-Trading-Modus mit persistenten virtuellen Beständen,
  Limit-Käufen, Market-Verkäufen, Gebühren, Slippage und derselben Marktfilter-,
  Grid-, Stop-Loss- und Trailing-Stop-Entscheidungslogik wie im Live-Betrieb.
  Paper- und Live-Daten sind in Status, Orders, Positionen und Kontoständen
  sichtbar getrennt. Vorerst werden EUR-Paare und Vollausführungen simuliert.
- **Umgesetzt:** vollständige, zunächst beobachtende Ansichten für offene Orders, Positionen,
  Kontostände und das Aktivitätsprotokoll.
- **Umgesetzt:** erlaubte Ordertypen werden je Handelspaar aus dem
  Fusion-Paarkatalog gespeichert und in den Paareinstellungen angezeigt.
  Manuelle Orders, Baseconfig und Config Pools bieten nur die für ihre Paare
  zulässige Schnittmenge an; Speichern, Pool-Zuordnung und Botstart besitzen
  zusätzlich eine unabhängige Sicherheitsprüfung. Bereits gespeicherte Paare
  können ihre Regeln ohne Löschen erneut von Fusion laden.
- **Umgesetzt:** Fehler-/Warnzentrale für API-Probleme, fehlende Marktdaten,
  ungeklärte Orders und verletzte Handelsregeln.

### Phase 2 – Vollständige Bot-Konfiguration

In Anlehnung an Baseconfig und
[Config Pools](https://docs.cryptohopper.com/docs/trading-bot/what-are-config-pools):

- **Umgesetzt:** globale Basiskonfiguration je Bot mit Name, Status, Modus,
  Strategie, Budget, Risikolimits, Ordertypen, Laufzeiten, Cooldown,
  Take Profit, Trailing Stop-Buy, Gewinnbedingung, Zeitausstieg und DCA.
- **Umgesetzt:** benannte Config Pools überschreiben die Baseconfig für alle
  zugeordneten Paare. Ein Pool kann mehreren Paaren zugeordnet werden und bildet
  dadurch eine lokale Paargruppe. Ohne Pool gilt automatisch die Bot-Baseconfig.
- **Umgesetzt:** Auswahl von Limit-, Market- und Stop-Ordertypen in der
  Oberfläche sowie deren Verwendung durch Live- und Paper-Ausführung.
- **Umgesetzt:** konfigurierbare maximale Anzahl offener Positionen sowie
  offener Kauf- und Verkaufsorders als harte Bot-Grenzen.
- **Umgesetzt:** maximale Laufzeit für Kauf- und Verkaufsorders; Live-Orders
  verwenden GTD, Paper-Kauforders werden lokal nach Ablauf storniert.
- **Umgesetzt:** konfigurierbarer Cooldown nach Verkäufen; der separate
  24-Stunden-Schutz nach hartem Stop bleibt als strengere Sperre erhalten.
- **Umgesetzt:** konfigurierbarer Take Profit mit gemeinsamer Exit-Entscheidung
  für Live und Paper.
- **Umgesetzt:** harter Stop-Loss und Trailing Stop-Loss als paarspezifische
  Schutzregeln.
- **Umgesetzt:** persistenter Trailing Stop-Buy mit Drop-Aktivierung und
  Rebound-Trigger je Bot und Paar.
- **Umgesetzt:** Option „nur mit Gewinn verkaufen“ für reguläre Regime- und
  Zeitausstiege; Schutzstopps bleiben davon bewusst unberührt.
- **Umgesetzt:** zeitgesteuertes Schließen von Positionen.
- **Umgesetzt:** DCA mit maximalen Nachkäufen, Kursrückgang als Trigger und
  konfigurierbarem Ordergrößen-Multiplikator.
- **Teilweise:** Kapitallimit je Paar sowie botweites Budget, Exposure-,
  Positions- und Orderlimit sind vorhanden; Portfolioquoten nach Asset fehlen.

### Phase 3 – Strategie-Designer

In Anlehnung an den
[Strategy Builder](https://docs.cryptohopper.com/docs/my-library/set-up-strategy-with-strategy-builder):

- **Umgesetzt:** visueller Regelbaukasten ohne Programmierung in der nativen
  Desktop-Oberfläche.
- **Umgesetzt:** Preis, SMA, EMA, RSI, MACD, MACD-Signallinie, StochRSI,
  Bollinger-Bänder, ATR, CCI und Volumen mit einheitlichen Timeframes von 1m
  bis 1d.
- **Umgesetzt:** Bullish/Bearish Engulfing, Hammer und Shooting Star als
  Candlestick-Bedingungen.
- **Umgesetzt:** beliebig verschachtelte UND-/ODER-Gruppen für Kauf-, Verkauf-,
  Sperr- und Bestätigungssignale.
- **Umgesetzt:** konfigurierbare Mindestanzahl bestätigender Signalzweige.
- **Umgesetzt:** Strategien lokal speichern, automatisch aktualisieren,
  duplizieren und sicher löschen.
- **Umgesetzt:** Strategiezuteilung mit der Priorität Handelspaar, Config Pool,
  Marktphase und Bot-Standard.
- **Umgesetzt:** dieselbe Auswertungsengine für Live und Paper sowie ein
  manueller Marktdatentest mit nachvollziehbarer Erklärung jeder Teilregel.
- **Zurückgestellt:** Strategie-Import und -Export als Paket; dies gehört zur
  vorerst ausgeschlossenen lokalen Copy-Trading-/Vorlagenfunktion.

### Phase 4 – Backtesting und Optimierung

- **Umgesetzt:** historische Long-Simulation mit derselben Strategieauswertung
  wie Live und Paper sowie Grid-Orders, DCA und mehreren gleichzeitigen
  Positionen.
- **Umgesetzt:** Gebühren, Slippage, Spread, Mindestorder, Tick- und
  Mengenschritte, konservative Stop-/Take-Profit-Reihenfolge sowie
  volumenbegrenzte Teilfüllungen. Limitorders werden anhand von Open, High, Low
  und Close mit konservativer Intrakerzenlogik ausgeführt.
- **Umgesetzt:** Vergleich mehrerer Strategien sowie kombinierter Grid-,
  Stop-Loss- und Take-Profit-Varianten. Der Rang basiert transparent auf
  In-Sample-Rendite abzüglich maximalem Drawdown; das Out-of-Sample-Ergebnis
  fließt nicht in die Auswahl ein.
- **Teilweise:** Equity-Kurve, maximaler Drawdown, Trefferquote und Profit
  Factor sind sichtbar und dauerhaft gespeichert; Erholungsdauer und
  Kapitalbindung fehlen.
- **Umgesetzt:** Backtest-Verlauf und einzelne simulierte Trades einschließlich
  Ausführungsgrund und Signalerklärung werden in SQLite gespeichert.
- **Offen:** Darstellung einzelner Käufe und Verkäufe im Chart.
- **Umgesetzt:** getrennte In-/Out-of-Sample-Tests und Walk-forward-Auswertung
  mit wachsendem Trainingsfenster. Jedes Fenster wählt seine Variante nur aus
  den zu diesem Zeitpunkt bekannten historischen Daten.
- **Offen:** Übernahme eines geprüften Ergebnisses in eine lokale
  Bot-Konfiguration und anschließender Paper-Test.

### Phase 5 – Trading-Terminal und Portfolio

- **Umgesetzt:** natives interaktives Kerzenchart mit Volumen, Maus-Zoom,
  Verschieben, OHLCV-Fadenkreuz, Grid-Stufen, offenen Live-/Paper-Orders,
  Positionen und historischen Kauf-/Verkaufsmarkierungen.
- **Umgesetzt:** manuelle Kauf- und Verkaufsorders im Paper- und Live-Modus
  mit Pflichtvorschau, Handelsregel- und Risikoprüfung. Verkäufe sind bis zur
  Positionsaufteilung bewusst nur für eine vollständige Position möglich.
- **Umgesetzt:** kontrollierte Stornierung und Orderänderung als
  „bestätigt stornieren, dann neu anlegen“. Live-Daten werden nur nach einem
  eindeutigen terminalen Fusion-Status ohne Ausführung geändert; Teilfüllungen
  und unklare Antworten bleiben im regulären Abgleich.
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
- **Teilweise:** Marktphasen werden intern erkannt und können automatisch eine
  zugewiesene Strategie aktivieren; eine eigene Marktphasen-Ansicht fehlt.
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

1. Positionen teilen, zusammenführen und für Strategien reservieren.
2. Portfolioanalyse, Handelsverlauf und Export; erst danach Market Making oder
   Arbitrage.

Market Making und Arbitrage sollten erst nach Paper Trading, Backtesting,
Exposure-Limits und einer belastbaren Orderabstimmung aktiviert werden. Sie
erhöhen Orderfrequenz, Kapitalbindung und Betriebsrisiko deutlich.
