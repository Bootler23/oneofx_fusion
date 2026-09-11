# Bedienkonzept für OneOfX Fusion

Stand: 11. September 2026

## Ziel

OneOfX soll trotz seines großen Funktionsumfangs ruhig, eindeutig und sicher zu bedienen sein. Farben, Typografie, Karten, Abstände, Dark Theme und die allgemeine visuelle Identität bleiben bestehen. Verändert werden ausschließlich:

- Informationsarchitektur und Navigation
- Reihenfolge und Gruppierung von Feldern
- Arbeitsabläufe und Abhängigkeiten zwischen Funktionen
- Sichtbarkeit von seltenen oder fortgeschrittenen Optionen
- Status-, Fehler- und Sicherheitsrückmeldungen

Der Leitsatz lautet: **Im aktuellen Moment nur das zeigen, was für die nächste Entscheidung benötigt wird.** „Less is more“ bedeutet hier nicht weniger Leistungsumfang, sondern weniger gleichzeitige Komplexität.

## 1. Bestandsaufnahme

Die heutige Oberfläche besitzt bereits gute Grundlagen: eine dauerhaft sichtbare Bot-Auswahl, einen Engine-Status, getrennte Paper-/Live-Modi, automatische Speicherung, Bestätigungen vor Live-Aktionen, eine Grid-Vorschau sowie eigene Ansichten für Warnungen und operative Daten.

Die Bedienung wird derzeit vor allem durch die Verteilung der Funktionen erschwert:

- Elf gleichrangige Menüpunkte konkurrieren in der Seitenleiste miteinander.
- Ein neuer Bot wird zunächst nur als deaktivierte Hülle angelegt. Danach muss der Nutzer selbst herausfinden, dass API-Zugang, Paare, Budget, Strategie, Risiko und Backtest auf verschiedenen Seiten liegen.
- „Bots“ enthält Grunddaten, Limits und Baseconfig, während „Einstellungen“ die paarbezogene Kaufstrategie und das Risiko enthält. Diese Trennung folgt der internen Datenstruktur, nicht der Aufgabe des Nutzers.
- Orders, Positionen, Kontostände und Terminal gehören im Alltag zusammen, liegen aber auf vier Seiten.
- Backtest und Variantenoptimierung werden gleichzeitig und gleichwertig gezeigt, obwohl die Optimierung ein deutlich seltenerer Expertenfall ist.
- DCA-, Trailing-, Laufzeit- und Spezialfelder sind sichtbar, auch wenn die jeweilige Funktion deaktiviert ist.
- Der globale Startknopf ist immer präsent. Ob der gewählte Bot tatsächlich startbereit ist und welcher Schritt noch fehlt, erfährt man erst nach dem Klick.
- Automatisches Speichern ist grundsätzlich angenehm, der konkrete Zustand „speichert / gespeichert / Eingabe fehlerhaft“ ist aber nicht direkt an der bearbeiteten Sektion sichtbar.
- Die Strategiezuweisung mit Bot-, Pool-, Marktphasen- und Paar-Priorität ist mächtig, verlangt aber ein mentales Modell, das die Oberfläche derzeit nur textlich erklärt.

## 2. Das neue mentale Modell

Die Anwendung wird nicht nach Datenbankobjekten, sondern nach fünf Nutzeraufgaben gegliedert:

| Hauptbereich | Nutzerfrage | Enthaltene Funktionen |
| --- | --- | --- |
| **Übersicht** | Läuft alles sicher und muss ich etwas tun? | Engine, Kapital, Ergebnis, Warnungen, letzte Aktivität, Schnellzugriffe |
| **Bots** | Was soll dieser Bot handeln und nach welchen Regeln? | Bot-Liste, Einrichtung, Paare, Strategie, Risiko, erweiterte Ausführung |
| **Handel** | Was geschieht gerade am Markt? | Chart, Positionen, Orders, manuelle Order, Kontostände |
| **Backtests** | Wie hat sich die Konfiguration historisch verhalten? | einfacher Test, Ergebnisse, Verlauf, erweiterte Variantenoptimierung |
| **System** | Ist die technische Verbindung in Ordnung? | API-Zugang, Datenbankstatus, Warnungsarchiv, Aktivitätsprotokoll |

Damit sinkt die Seitenleiste von elf auf fünf Punkte. Keine Funktion geht verloren:

| Bisher | Neu |
| --- | --- |
| Dashboard | Übersicht |
| Bots + Einstellungen + Strategien | Bots mit internen Reitern |
| Trading-Terminal + Orders + Positionen + Kontostände | Handel mit internen Reitern |
| Backtesting | Backtests |
| Aktivität + API-Zugang | System; aktive Probleme zusätzlich auf Übersicht |

Die Bot-Auswahl bleibt global in der Kopfleiste. Direkt daneben stehen immer Modus und Zustand, beispielsweise `Grid BTC · PAPER · läuft`. So ist in jeder Ansicht eindeutig, auf welchen Bot sich Werte und Aktionen beziehen.

### Grundlayout und Platzierung

```text
┌──────────────┬───────────────────────────────────────────────────────┐
│ oneofx       │ Seitentitel       Bot: Grid BTC · PAPER · Gestoppt   │
│              │                                      [Bot starten]   │
│ Übersicht    ├───────────────────────────────────────────────────────┤
│ Bots         │                                                       │
│ Handel       │                 Seiteninhalt                          │
│ Backtests    │                                                       │
│ System       │                                                       │
│              ├───────────────────────────────────────────────────────┤
│ Lokal/DB     │ Letzte Rückmeldung · Speichern/Fehler · Aktualität    │
└──────────────┴───────────────────────────────────────────────────────┘
```

- **Links:** nur die fünf stabilen Hauptbereiche. Unterfunktionen gehören als Reiter in ihren Bereich, nicht zusätzlich in die Hauptnavigation.
- **Oben links im Arbeitsbereich:** aktueller Seitentitel.
- **Oben rechts:** Bot-Auswahl, Paper/Live-Modus und Engine-Zustand als eine zusammengehörige Kontexteinheit.
- **Ganz rechts:** genau eine primäre Zustandsaktion. Bei gestopptem Bot `Bot starten`, bei laufendem Bot `Käufe pausieren`, sofern diese Funktion technisch sicher verfügbar ist. `Engine stoppen` liegt als klar beschriftete sekundäre Aktion daneben oder unter `Weitere Aktionen`.
- **Im Seiteninhalt oben:** Ergebnis, Status oder wichtigster nächster Schritt; Formulare und Detailtabellen folgen darunter.
- **Direkt am Objekt:** Bearbeiten-, Ersetzen-, Stornieren- und Löschen-Aktionen. Eine Aktion für eine Tabellenzeile steht nicht losgelöst am Seitenkopf.
- **Unten:** nur kurzlebige globale Rückmeldungen. Feldfehler und Speicherfehler erscheinen zusätzlich direkt an ihrer Ursache.

Auf jeder Ansicht gibt es höchstens eine visuell primäre Aktion. Mehrere mögliche Nebenaktionen werden sekundär dargestellt oder unter `Weitere Aktionen` gebündelt. Das betrifft nur Gewichtung und Platzierung; das bestehende Farbsystem bleibt unverändert.

## 3. Wichtigster Ablauf: einen Bot einrichten

„Neuen Bot anlegen“ öffnet einen geführten Ablauf. Er ersetzt nicht die Expertenkonfiguration, sondern sorgt für einen sicheren, vollständigen ersten Zustand.

### Schritt 1 – Zweck und Modus

- Bot-Name
- `Paper` oder `Live`; **Paper ist vorausgewählt**
- Bei Live ein kurzer, sachlicher Hinweis auf echte Orders
- Wenn kein API-Zugang vorliegt: eingebetteter Schritt „Verbindung einrichten“, danach automatische Rückkehr

### Schritt 2 – Markt und Kapital

- ein oder mehrere geprüfte Handelspaare
- verfügbares Guthaben als Kontext
- Gesamtbudget
- maximales Exposure
- maximale Zahl offener Positionen

Die technische Paarprüfung geschieht direkt nach der Auswahl. Eine ungültige Auswahl wird nicht erst beim Start gemeldet.

### Schritt 3 – Handelslogik

Zwei klar benannte Wege:

- **Einfach:** vorhandene Strategie auswählen, Betrag je Order, Grid-Art und Grid-Abstand
- **Erweitert:** eigener Regelbaum, Config Pools, DCA, spezielle Ordertypen und Laufzeiten

Beim Wechsel zu „Erweitert“ bleiben bereits eingegebene Werte erhalten. Pionex reduziert die manuelle Grundeinrichtung auf wenige Pflichtwerte und führt Trigger, Stop-Loss und Take-Profit als optionale Erweiterungen; 3Commas trennt ebenfalls Manual und Advanced und erhält beim Wechsel die bestehenden Eingaben. Dieses Muster passt sehr gut zu OneOfX. [Pionex: erste Grid-Parameter](https://www.pionex.com/blog/knowledge-base/how-to-set-up-the-parameters-for-my-first-grid-bot/), [3Commas: neuer Bot-Dialog](https://help.3commas.io/en/articles/15855098-dca-bot-new-creation-form)

### Schritt 4 – Schutz

- harter Stop-Loss mit eindeutigem Ein-/Aus-Schalter
- Take Profit
- optional Trailing Stop; Aktivierung und Abstand erscheinen nur, wenn er eingeschaltet ist
- leicht verständliche Zusammenfassung des maximal gebundenen Kapitals

`0` darf nicht mehr stillschweigend „deaktiviert“ bedeuten. Die Oberfläche zeigt stattdessen `Stop-Loss verwenden` und blendet erst dann das Prozentfeld ein.

### Schritt 5 – Prüfen

Vor dem Anlegen erscheint eine kompakte Zusammenfassung:

- Modus und Bot
- Paare und Strategie
- Budget, maximales Exposure und Betrag je Order
- geschätzte Orderzahl und abgedecktes Preisband
- aktive Schutzregeln
- benötigtes gegenüber verfügbarem Kapital
- erkannte Blocker und Warnungen

Jeder Punkt besitzt bei Bedarf `Ändern`, das direkt zum passenden Schritt führt. Cryptohopper beendet seinen Einrichtungsassistenten ebenfalls mit einer Zusammenfassung; Bitsgap führt Nutzer in der Reihenfolge Bot-Typ, Börse/Paar, Investment, optionales Schnell-Setup, Backtest und Start. [Cryptohopper: Config Wizard](https://docs.cryptohopper.com/docs/fundamentals/new-bot-wizard), [Bitsgap: GRID-Bot einrichten](https://bitsgap.com/helpdesk/article/18010007626140-Setting-Up-and-Launching-Your-GRID-Bot)

Abschlussaktionen:

- bei Paper: **„Bot anlegen und im Paper-Modus testen“**
- bei Live: **„Bot anlegen“**, aber noch nicht automatisch live starten

Das verhindert, dass Erstellung und Echtgeldfreigabe zu einer einzigen unbewussten Handlung verschmelzen.

## 4. Bot-Seite: Überblick statt Formularwand

Nach Auswahl eines Bots öffnet sich zuerst dessen Status, nicht sofort ein großes Formular.

```text
Bot: Grid BTC                         PAPER · Gestoppt
────────────────────────────────────────────────────
Status | Einrichtung | Strategie | Risiko | Erweitert

Startbereit: 4 von 5 Prüfungen erfüllt
! Für BTC/EUR ist noch keine Kapitalgrenze gesetzt  [Beheben]

Budget        Gebunden       Verfügbar      Positionen
5.000 €       1.240 €        3.760 €        3 / 20

[Paper-Bot starten]                    [Weitere Aktionen]
```

### Status

Zeigt nur den aktuellen Zustand, die vier wichtigsten Kapitalwerte, aktive Paare, die wirksame Strategie und konkrete offene Aufgaben. Von hier aus ist jede Korrektur mit einem Klick erreichbar.

### Einrichtung

Enthält Name, Modus, Budget, Exposure, Positions- und Orderlimit sowie die Paarverwaltung. Diese Dinge beantworten gemeinsam die Frage: „Was darf dieser Bot insgesamt tun?“

### Strategie

Vereint Strategieauswahl, Regelbaum und Zuweisung. Oben steht in normaler Sprache die wirksame Logik, zum Beispiel:

> Kaufen, wenn MACD positiv ist und StochRSI K über D liegt. Bei fehlenden Daten keine neuen Käufe. Harter Stop-Loss: 2 %.

Der Nutzer muss die Bedeutung nicht aus einer Tabelle zusammensetzen. `Mit aktuellen Marktdaten prüfen` zeigt das Ergebnis direkt unter dieser Zusammenfassung und nicht in einem separaten Informationsdialog.

### Risiko

Gruppiert ausschließlich Verlust- und Kapitalbegrenzungen. Abhängige Felder werden nur bei aktivierter Funktion gezeigt. Neben jedem Grenzwert steht seine konkrete Wirkung, beispielsweise `Maximal 5.000 € dieses Bots können gleichzeitig gebunden sein`.

### Erweitert

Hier liegen Baseconfig, Config Pools, Ordertypen, Orderlaufzeiten, Cooldown, Trailing Stop-Buy, zeitbasierter Ausstieg, DCA, Paper-Gebühr und Slippage. Config Pools erscheinen erst, wenn der Nutzer `Abweichende Einstellungen für einzelne Paare verwenden` aktiviert.

Diese progressive Offenlegung hält seltene Funktionen erreichbar, reduziert aber die Fehlerquote und den Lernaufwand. Genau dafür empfiehlt die Nielsen Norman Group, zunächst nur wichtige Optionen und spezialisierte Optionen erst auf ausdrücklichen Wunsch zu zeigen. [NN/g: Progressive Disclosure](https://www.nngroup.com/articles/progressive-disclosure/)

## 5. Handel: Chart und operative Daten gehören zusammen

Der Bereich „Handel“ übernimmt den aktuellen Trading-Terminal als Ausgangspunkt. Unterhalb des Charts liegen Reiter:

```text
Positionen (3) | Offene Orders (4) | Ungeklärt (1) | Kontostände
```

Vorteile:

- Eine ausgewählte Position kann unmittelbar verkauft werden.
- Eine ausgewählte Order kann unmittelbar ersetzt oder storniert werden.
- Der Nutzer sieht vor einer manuellen Order gleichzeitig Kurs, Position und vorhandene Orders.
- „Ungeklärt“ wird nur hervorgehoben, wenn tatsächlich ein Fall vorliegt.
- Paar und Timeframe werden nur einmal für die gesamte Handelsansicht gewählt.

Die manuelle Order ist standardmäßig eingeklappt und wird über `Manuelle Order` geöffnet. Sie ist eine bewusste Nebenfunktion, nicht der Mittelpunkt eines automatisierten Bots.

Alle Tabellen folgen demselben Muster:

- Klick auf eine Zeile zeigt Details und passende Aktionen.
- Aktionen ohne Auswahl bleiben verborgen oder deaktiviert und erklären den Grund.
- Suche und Filter sitzen direkt über der Tabelle.
- Standardmäßig werden aktive und problematische Einträge gezeigt; abgeschlossene Historie ist ein Filter.
- IDs stehen in den Details, nicht als dominante Hauptspalte. Im Alltag sind Paar, Seite, Betrag, Preis, Status und Zeit wichtiger.

## 6. Übersicht: in zehn Sekunden wissen, ob Handlungsbedarf besteht

Die Übersicht beantwortet in dieser Reihenfolge:

1. Welcher Bot und welcher Modus sind aktiv?
2. Läuft die Engine?
3. Gibt es ein Problem, das eine Handlung verlangt?
4. Wie viel Kapital ist gebunden und wie ist das aktuelle Ergebnis?
5. Was ist zuletzt passiert?

Empfohlener Aufbau:

```text
Grid BTC · PAPER · Läuft seit 02:14 h              [Käufe pausieren]

Alles in Ordnung
Letzte erfolgreiche Marktaktualisierung vor 8 Sekunden

Gesamtwert    Gebunden    Frei    Offenes PnL    Positionen

Handlungsbedarf
! 1 ungeklärte Order                               [Prüfen]

Offene Positionen (kompakte Vorschau)
Letzte Aktivitäten (maximal fünf)
```

Reine Systemdetails wie `REST-Polling` und `Portable SQLite` wandern nach „System“. Auf der Übersicht erscheinen sie nur, wenn daraus ein Problem entsteht. Statussichtbarkeit, Fehlervermeidung und Erkennen statt Erinnern sind zentrale Usability-Heuristiken. [NN/g: 10 Usability Heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)

## 7. Starten, Pausieren und Stoppen

Der wichtigste Sicherheitsgewinn ist eine klare Trennung der Bedeutungen:

- **Käufe pausieren:** keine neuen Positionen; bestehende Positionen und Schutzlogik werden weiter überwacht.
- **Engine kontrolliert stoppen:** lokale Verarbeitung endet. Die Oberfläche erklärt ausdrücklich, was anschließend nicht mehr überwacht wird.
- **Position schließen:** separate Handelsentscheidung, niemals stiller Bestandteil von Pause oder Stop.

Falls die aktuelle Engine „Käufe pausieren“ noch nicht botweit unterstützt, wird diese Aktion erst nach technischer Umsetzung angeboten. Bis dahin muss der Stop-Dialog präzise die reale Wirkung beschreiben.

Vor jedem Start läuft eine sichtbare Bereitschaftsprüfung:

```text
✓ API-Verbindung erreichbar
✓ Bot aktiviert
✓ 2 Handelspaare geprüft
✓ Strategie wirksam
✓ Budget und Exposure gültig
! 1 ungeklärte Übermittlung                       [Öffnen]
```

Ein Blocker deaktiviert den Start und bietet direkt `Beheben`. Eine Warnung erlaubt den Start erst nach bewusster Kenntnisnahme. Für Live zeigt die finale Bestätigung Botname, Modus, Budget, Exposure, Paare und Schutzregeln. Die Schaltflächen heißen konkret `Live-Bot starten` und `Abbrechen`, nicht `Ja` und `Nein`; der riskante Schritt ist niemals vorausgewählt. Das folgt den Empfehlungen für wirksame Bestätigungsdialoge. [NN/g: Confirmation Dialogs](https://www.nngroup.com/articles/confirmation-dialog/)

## 8. Backtests: einfach zuerst, Optimierung nur bei Bedarf

Die Standardansicht enthält nur:

- vorausgewählte aktuelle Strategie und aktuelles Paar
- Zeitraum
- Startkapital
- Aktion `Backtest starten`

Spread, Teilfüllung und Volumenbeteiligung werden aus der Bot-Konfiguration übernommen und in einer einzeiligen Annahmen-Zusammenfassung gezeigt. Über `Ausführungsannahmen ändern` lassen sie sich bearbeiten.

Nach dem Lauf erscheinen zuerst:

- Rendite
- maximaler Drawdown
- Endkapital
- Zahl der Trades
- verwendete Gebühren und Annahmen
- Equity-Kurve

Trade- und Orderprotokolle liegen darunter in Reitern. `Varianten vergleichen` ist ein eigener erweiterter Modus und nicht Teil des Standardformulars. Dadurch bleibt der normale Backtest verständlich, ohne Walk-forward und In-/Out-of-Sample zu verstecken.

Von der Strategie-Seite führt `Backtest mit dieser Strategie` direkt hierher und übernimmt Bot, Paar und Konfiguration. Nach einem Ergebnis führt `Als Bot-Konfiguration übernehmen` zunächst zu einer Änderungsübersicht; es gibt keine unbemerkte Übernahme.

## 9. Felder und Formverhalten

Für alle Formulare gelten dieselben Regeln:

- Eine Information wird nur an einer Stelle bearbeitet. Andere Ansichten verlinken dorthin.
- Abhängige Felder stehen direkt unter ihrem Schalter und sind im ausgeschalteten Zustand verborgen.
- Technische Kürzel werden ausgeschrieben oder direkt erklärt: `Maximales Exposure (gleichzeitig gebundenes Kapital)`.
- Einheiten bleiben am Feld sichtbar und konsistent; deutsche Zahlenformatierung wird verwendet.
- Sinnvolle Voreinstellungen sind vorhanden, werden aber als Voreinstellung kenntlich gemacht.
- Validierung erfolgt nach Abschluss der Eingabe und direkt am betroffenen Feld, nicht ausschließlich in einem allgemeinen Dialog.
- Eine Fehlermeldung sagt immer Problem und Lösung: `Betrag je Order liegt unter dem Fusion-Minimum von 10 €. Erhöhe den Betrag auf mindestens 10 €.`
- Bei automatischer Speicherung steht in der jeweiligen Sektion `Speichert …`, `Gespeichert` oder `Nicht gespeichert – Wert prüfen`.
- Beim Seitenwechsel bleiben Scrollposition, Auswahl und unvollständige Eingaben erhalten.
- Dialoge werden nur für Live-Orders, unwiderrufliche/weitreichende Aktionen und sicherheitsrelevante Bestätigungen verwendet.

Fehler sollten nahe an ihrer Ursache erscheinen und erklären, wie sie behoben werden. Das reduziert Suchaufwand und schützt bereits geleistete Eingaben. [NN/g: Error-Message Guidelines](https://www.nngroup.com/articles/error-message-guidelines/)

## 10. Was von anderen Anbietern übernommen wird – und was nicht

| Anbieter/Muster | Sinnvoll für OneOfX | Nicht übernehmen |
| --- | --- | --- |
| Pionex: wenige Pflichtparameter, optionale Advanced Settings | einfache Ersteinrichtung | Renditeversprechen oder scheinbar „optimale“ Parameter |
| 3Commas: Manual/Advanced, Werte bleiben erhalten | progressive Offenlegung ohne Datenverlust | alle Expertenfelder auf einer endlosen Seite |
| 3Commas: kompakter Summary-Check mit Kapitalbedarf | Startprüfung und Grid-Zusammenfassung | komplizierte Kennzahlen ohne Erklärung |
| Bitsgap: klare Reihenfolge bis Backtest und Start | linearer Einrichtungsablauf | Backtest als implizite Erfolgsgarantie |
| Cryptohopper: Wizard und Abschlussübersicht | sichere Vollständigkeitsprüfung | interne Begriffe als primäre Navigation |

3Commas fasst beispielsweise maximal abgedeckten Kursrückgang, durchschnittliche Einstiegslage und benötigtes Kapital als gemeinsamen Plausibilitätscheck zusammen. Für OneOfX sollten entsprechend Preisband, Orderzahl, Kapitalbedarf und verfügbare Mittel gemeinsam sichtbar sein. [3Commas: Summary Box](https://help.3commas.io/en/articles/16666593-understanding-the-dca-bot-summary-box)

## 11. Priorisierte Umsetzung

### Phase 1 – größter Effekt bei geringem Umbau

1. Navigation auf fünf Hauptbereiche reduzieren.
2. Orders, Positionen, Ungeklärt und Kontostände im Bereich „Handel“ bündeln.
3. Bot-Einstellungen in Basis und Erweitert trennen.
4. Abhängige Felder ausblenden, wenn Trailing, DCA oder zeitbasierter Ausstieg deaktiviert sind.
5. Startbereitschaft mit konkreten Blockern und `Beheben`-Aktionen anzeigen.
6. Warnungen auf der Übersicht handlungsorientiert darstellen.

### Phase 2 – durchgängige Arbeitsabläufe

1. Einrichtungsassistent für neue Bots.
2. Strategie → aktueller Markttest → Backtest als zusammenhängender Weg.
3. Lokale Auto-Save- und Validierungsrückmeldung je Sektion.
4. Einheitliche Tabelleninteraktion und Detailbereich.

### Phase 3 – Feinschliff und Überprüfung

1. Botweite Kaufpause mit klar definierter Schutzüberwachung, sofern technisch sicher umsetzbar.
2. Kontextsensitive Kurzbeschreibungen für anspruchsvolle Felder.
3. Nutzertest mit drei realen Aufgaben und Beobachtung statt nur Befragung.
4. Tastaturreihenfolge, Fokuszustände und Screenreader-Bezeichnungen prüfen.

## 12. Messbare Abnahmekriterien

Das neue Bedienkonzept ist erfolgreich, wenn:

- ein neuer Nutzer einen Paper-Bot ohne Suche in anderen Hauptbereichen vollständig einrichten kann;
- vor jedem Start sofort sichtbar ist, ob der Bot bereit ist und welcher konkrete Schritt fehlt;
- Bot, Paper/Live-Modus und Engine-Zustand auf jeder Seite innerhalb weniger Sekunden erfassbar sind;
- kein deaktiviertes Feature drei weitere bedeutungslose Felder sichtbar lässt;
- aktive Positionen, offene Orders und Warnungen ohne Wechsel zwischen Hauptseiten gemeinsam geprüft werden können;
- jeder Fehler direkt zur betroffenen Einstellung führt und eine konkrete Lösung nennt;
- ein Standard-Backtest ohne Verständnis von Walk-forward, IS/OOS oder Teilfüllungsmodellen gestartet werden kann;
- eine Live-Aktion niemals durch einen allgemeinen oder vorausgewählten Bestätigungsbutton ausgelöst wird;
- alle heutigen Funktionen weiterhin erreichbar sind, der Standardweg aber deutlich weniger Entscheidungen zeigt.

## Kernaussage

OneOfX braucht keine neue Optik und auch keine Funktionskürzung. Es braucht eine **aufgabenorientierte Ordnung**: erst Überblick, dann Bot einrichten, dann Handel beobachten, dann analysieren. Seltene Expertenfunktionen bleiben erhalten, treten aber erst in Erscheinung, wenn der Nutzer sie bewusst öffnet. So wird aus einer umfangreichen technischen Oberfläche ein ruhiges, nachvollziehbares Arbeitswerkzeug.
