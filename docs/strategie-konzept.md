# Konzept: BTC-Grid mit Trendfilter und Verlustbegrenzung

Stand: 9. September 2026. Die einfache MACD-Variante dieses Konzepts ist inzwischen technisch umgesetzt; ihr historischer Nutzennachweis durch den unten beschriebenen Backtest steht noch aus. Ziel ist, den Kapitalaufbau in ungünstigen Marktphasen zu begrenzen und Verluste bestehender Positionen ausdrücklich zu behandeln. Kein Indikator kann sicherstellen, dass der Kurs nach einem Kauf weiter steigt.

**Aktuell umgesetzte Abweichung vom ursprünglichen Entwurf:** MACD(12,26,9) und StochRSI(14,14,3,3) laufen beide auf dem 1D-Timeframe. Sie werden jede Minute mit der aktuell laufenden UTC-Tageskerze neu berechnet. Neue Grid-Käufe sind nur freigegeben, wenn das MACD-Histogramm positiv ist (`MACD > Signallinie`) und zugleich StochRSI `%K > %D` gilt. Ein einmaliges Crossover und eine Schwelle von 20 oder 30 werden nicht verlangt. Da die laufende Kerze verwendet wird, kann die Freigabe innerhalb eines Tages wechseln. Der zuvor vereinbarte Ausstieg bei `MACD <= 0`, Notstopp, TSL, Kapitalgrenze und 24-Stunden-Sperre bleiben bestehen. Die nachfolgenden 3D- und Crossover-Abschnitte dokumentieren den älteren Vergleichsentwurf und nicht die aktive Einstellung.

Die vorgeschlagenen Parameter sind vorab festgelegte Testannahmen. Für die konkrete Kombination aus Grid, 3D-MACD, StochRSI und Trailing Stop liegt hier kein eigener historischer Rentabilitätsnachweis vor.

**Empfohlener Aufbau**

Zuerst eine einfache Variante mit MACD, Kapitalgrenze und klaren Ausstiegen testen. StochRSI als zusätzliche Vergleichsvariante behandeln. Er muss seine zusätzliche Komplexität durch bessere Ergebnisse nach Kosten rechtfertigen.

| Baustein | Zeitrahmen | Regel / Zweck |
| --- | --- | --- |
| Trend und Momentum | Abgeschlossene 3-Tages-Kerzen | MACD mit EMA-Perioden 12/26 und Signal-EMA 9 |
| Optionales Einstiegstiming | Abgeschlossene Tageskerzen | StochRSI mit RSI 14, Stochastic 14, K-Glättung 3, D-Glättung 3 |
| Grid-Ausführung | Laufende Preise und Orderstatus | Bestehende Limit-Kaufmechanik innerhalb der Freigabe und Kapitalgrenze |
| Gewinnmitnahme | Laufende Preise | Bisherige TSL-Aktivierung bei +2,5 %, Abstand 0,8 % zunächst als Vergleichsbasis |
| Verlustbegrenzung | Laufende Preise | Preisbasierter Notstopp zusätzlich zum Ausstieg bei Trendverlust |

In den vorangegangenen Vorschlägen bezogen sich beide Indikatoren auf 3D. Für die Kombination lautet die neue, ausdrücklich noch ungetestete Hypothese: 3D für den übergeordneten Trend, 1D für einen Rücksetzer innerhalb dieses Trends. Auf derselben Zeiteinheit können sich positives MACD-Momentum und ein StochRSI-Crossover aus einer tiefen Zone seltener überschneiden.

Die 12/26 EMA-Perioden umfassen bei 3D jeweils 36/78 Kalendertage, bei Wochenkerzen 12/26 Wochen. Das sind Periodenskalen, keine festen Verzögerungen und keine harten Grenzen der EMA-Historie. Ein Wochenfilter ist nicht grundsätzlich schlechter; seine Freigabe wird bei ausschließlicher Verwendung abgeschlossener Kerzen lediglich seltener aktualisiert. Ob 1D, 3D oder 1W zum Grid passt, muss der Vergleich zeigen.

**Verhalten der einfachen MACD-Variante**

Alle Vergleiche beziehen sich auf den jüngsten vollständig abgeschlossenen 3D-Balken. M bezeichnet die MACD-Linie, S die Signallinie.

| Zustand | Neue Käufe | Wartende eigene Buy-Orders | Bestehende eigene Positionen |
| --- | --- | --- | --- |
| M > 0 und M > S | Erlaubt, solange Kapital-/Risikobudget reicht | Weiter verwalten | TSL und Notstopp überwachen |
| M > 0 und M <= S | Gesperrt | Ungefüllte Restmengen stornieren | Weiter halten; TSL und Notstopp bleiben aktiv |
| M <= 0 | Gesperrt | Ungefüllte Restmengen stornieren | Trend-Ausstieg: schließen, auch im Verlust |
| Daten fehlen, sind ungültig oder nicht aktuell genug | Gesperrt | Storno anfordern, soweit API verfügbar | Kein Verkauf allein aufgrund des Indikator-Datenfehlers; TSL und Notstopp bei gültigen Livepreisen weiter prüfen; hinterlegte Schutzorders bleiben aktiv |

M > S bedeutet ein positives Histogramm. Es bedeutet nicht zwangsläufig, dass das Histogramm von Kerze zu Kerze steigt. Der Verlust dieser Bedingung ist hier eine Kaufpause. M <= 0 bedeutet EMA 12 <= EMA 26; dies definiert für den Entwurf den Trendverlust und damit den Ausstieg.

Der Trend-Ausstieg ist keine Garantie eines maximalen Verlusts. Bis zum nächsten 3D-Schluss kann der Kurs stark gefallen sein. Deshalb ist zusätzlich ein preisbasierter Notstopp vorgesehen. Die Regel, bereits bei M <= S alles zu schließen, ist eine mögliche spätere Exit-Vergleichsvariante, kein belegter Verbesserungshebel.

Bei einem Trend-Ausstieg bleibt die Kaufseite gesperrt, bis alle betroffenen eigenen Orders und Positionen abschließend abgeglichen sind. Danach erfordert der Wiedereinstieg einen neu abgeschlossenen 3D-Balken mit M > 0 und M > S. Initial genügen gültige historische Daten und ein aktuell positiver Zustand. Eine zusätzliche Zwei-Kerzen-Bestätigung gehört nicht zum ersten Entwurf: Zwei 3D-Kerzen würden sechs Tage Bestätigungszeit umfassen und benötigen einen eigenen Nutzennachweis.

**Optionaler StochRSI: aus einem Ereignis wird eine begrenzte Freigabe**

Für einen reproduzierbaren Test entfällt die Formulierung „möglichst unter 20–30“. Zunächst wird genau 20 auf der 0–100-Skala gewählt. Ein tägliches Einstiegssignal liegt vor, wenn:

```text
K[t-1] <= D[t-1]
K[t-1] <= 20 und D[t-1] <= 20
K[t] > D[t]
und die MACD-Kauffreigabe bereits gilt
```

Dieses Ereignis öffnet ein Kaufzeitfenster. Es endet beim nächsten abgeschlossenen Tagesbalken mit K <= D, sobald die MACD-Kauffreigabe entfällt, bei einer Notstopp-Auslösung oder bei ungültigen/veralteten Signaldaten. Eine ausgeschöpfte Kapitalgrenze verhindert ausschließlich zusätzliche Orders: Sie beendet das Fenster nicht und storniert keine korrekt reservierten Orders. Nach Verkäufen kann frei gewordenes Budget innerhalb eines weiterhin gültigen Fensters erneut genutzt werden. Die MACD-Freigabe allein öffnet ein verlorenes StochRSI-Zeitfenster nicht erneut. Dafür ist ein neues gültiges Crossover nötig. Der zuletzt verarbeitete Signalzeitpunkt und das Fenster müssen dauerhaft gespeichert werden, damit ein Neustart kein altes Signal erneut verwendet.

Innerhalb des Fensters arbeitet das Grid weiter, stets mit derselben Kapitalgrenze. Ein StochRSI-Gegensignal löst ausschließlich eine Kaufpause mit Storno der offenen Restmengen aus. Bereits gekaufte Positionen folgen den MACD-, TSL- und Notstopp-Regeln. K > 80 ist kein zusätzlicher Verkaufsgrund. Schwelle 30 kann später als vorab angekündigter Robustheitsvergleich untersucht werden.

TradingView beschreibt StochRSI als RSI relativ zu seiner eigenen jüngsten Bandbreite und erläutert Überverkauft-Einstiege im Kontext eines Aufwärtstrends. Das unterstützt diese Rollenverteilung, belegt aber keine profitable Handelsregel. [Offizielle StochRSI-Dokumentation](https://www.tradingview.com/support/solutions/43000502333-stochastic-rsi-stoch-rsi/)

**Kapitalgrenze und Notstopp**

Eine ausdrücklich benannte Kapitalgrenze muss die Anschaffungskosten aller offenen Positionen, die reservierten Kaufbeträge wartender Orders sowie ungeklärte Kaufübermittlungen berücksichtigen. Reservierungen bei Teilfüllungen umschichten, nicht doppelt zählen. Ein gefallener Marktwert schafft kein zusätzliches Kaufbudget. In der Umsetzung ist die vorhandene Spalte `currency.maxBuyAmount` nun ausdrücklich als Gesamtgrenze je Paar definiert. Der aktuelle Zähler enthält keine zusätzliche Gebührenreserve; das eingetragene Limit muss deshalb entsprechend konservativ unter der gewünschten tatsächlichen Obergrenze liegen.

Für den ersten Test ist ein fester prozentualer Notstopp je tatsächlichem Kauf einfacher als ein weiterer Indikator. Er gilt ab dem Fill, wird durch Nachkäufe niemals nach unten verschoben und bleibt auch vor TSL-Aktivierung wirksam. Bei später aktivem TSL gilt die höhere Schutzschwelle. Nach einer Notstopp-Auslösung werden neue Käufe für das Paar für mindestens 24 Stunden gesperrt, offene Buy-Restmengen storniert und die übrigen Positionen weiter überwacht. Jede weitere Notstopp-Auslösung startet die Frist erneut. Anschließend gelten wieder die Eintrittsbedingungen der jeweiligen Variante; ein StochRSI-Fenster benötigt ein neues Signal nach der Sperre. Die 24 Stunden sind eine unoptimierte Testannahme gegen sofortiges Zurückkaufen und gelten identisch auch für die Referenz ohne Indikatorfilter. Die Sperrfrist wird dauerhaft gespeichert.

Stoppabstand und Kapitalquote müssen zusammen gewählt werden. Rechenbeispiel, ausdrücklich kein optimierter Parameter: Bei 25 % investiertem Bot-Kapital und 8 % Stoppabstand beträgt der gemeinsame geplante Kursverlust ungefähr 2 % des Bot-Kapitals, bevor Gebühren, Slippage und Kurslücken hinzukommen. Das begrenzt weder wiederholte Verluste über mehrere Handelsphasen noch garantiert es den Ausführungspreis. Das passende persönliche Verlustbudget steht noch nicht fest; ein Live-Standardwert lässt sich daraus nicht ableiten.

Schutzorders sollten, soweit technisch möglich, beim Handelsplatz hinterlegt werden. Ein im Bot berechneter Preisstopp allein ist bei dessen Ausfall nicht wirksam. Bei konkurrierenden TSL-, Notstopp- und Trend-Ausstiegen darf jede gehaltene Menge nur einmal zum Verkauf reserviert werden.

Ein weiterer einfacher Ansatz ist, das Grid aus dem Kaufbudget und einer gewünschten Preisbandbreite abzuleiten. Beispiel: 500 EUR Budget und 31 EUR je Kauf erlauben höchstens 16 vollständige Käufe, vor Gebührenreserve. Um 16 aufeinanderfolgende Stufen über einen Rückgang von ungefähr 10 % zu verteilen, wäre ein prozentualer Abstand von rund 0,656 % nötig: Abstand = 1 - (1 - 0,10)^(1/16). Beim bisherigen Abstand von 1/23 % entsprechen 16 Stufen nur ungefähr 0,693 % Preisrückgang. Das ist eine Rechnung unter der Annahme gefüllter aufeinanderfolgender Stufen, keine Kurs- oder Renditeprognose. Die 10 % sind keine optimierte Einstellung. Die Idee verdeutlicht, wie schnell ein dichtes Grid sein Budget verbraucht. Einen solchen Umbau separat nach dem Filtervergleich testen.

**Was die Daten tatsächlich stützen**

- Deprez und Frömmel (2024) untersuchen 75.360 technische Regeln mit BTC/USD-Daten von 2012 bis Anfang 2022, Transaktionskosten und Prüfungen außerhalb der Auswahlperiode. Einige ausgewählte Portfolios zeigen bessere risikobereinigte Ergebnisse. Höhere Kosten schwächen diese Resultate deutlich. Die Studie testet weder unser Grid noch diese MACD/StochRSI-Kombination. [Autoren-Volltext](https://backoffice.biblio.ugent.be/download/01HY3C3S169G1N6QNYR55NZMFB/01HY60XZGZYHNQ6188MSVJT0SG)
- Hudson und Urquhart (2021) untersuchen 14.919 technische Regeln. Für Bitcoin finden sie außerhalb der Auswahlperiode keine entsprechende Vorhersagbarkeit. Das widerspricht einer pauschalen Behauptung, technische Regeln seien erwiesenermaßen überlegen. [Universität Birmingham](https://research.birmingham.ac.uk/en/publications/technical-trading-and-cryptocurrencies/)
- Kaminski und Lo (2014) zeigen modellabhängige Vor- und Nachteile von Stop-Regeln. Ihre Untersuchung betrifft keine BTC-Grid-Strategie. Ein Stopp ist deshalb hier eine bewusste Verlustregel; eine höhere Rendite oder ein optimaler Prozentabstand ist damit nicht belegt. [Journal of Financial Markets](https://doi.org/10.1016/j.finmar.2013.07.001)

Die zuvor betrachtete lokale Stichprobe mit zwölf Käufen und keinem abgeschlossenen Verkauf reicht nicht als Strategienachweis. Sie liefert zudem keinen Vergleich zur ungefilterten Alternative.

**Ein überschaubarer Nachweisplan**

Zunächst alle Varianten mit identischer Kapitalgrenze, gleichem Notstopp, gleicher Ordergröße und identischen Kostenannahmen vergleichen:

1. Grid ohne Indikatorfilter als Referenz.
2. Grid mit MACD-Kaufpause, zunächst ohne MACD-Verkauf: Wirkung der Einstiegsselektion.
3. Zusätzlich MACD-Trend-Ausstieg: Wirkung der Verlustrealisierung.
4. Zusätzlich täglicher StochRSI: Zusatznutzen des Timings.

Ein einfacher Tageskurs/EMA-200-Filter dient als zusätzliche Plausibilitätsreferenz, falls MACD keinen klaren Vorteil zeigt. Buy-and-Hold mit vergleichbarer Kapitalquote und separat das vollständige Buy-and-Hold ebenfalls ausweisen. So wird ein niedrigerer Drawdown durch bloß höheren Cash-Anteil sichtbar.

BTC/EUR-Daten bevorzugen. Ältere Daten eines anderen Handelsplatzes ausdrücklich als Näherung kennzeichnen; EUR/USD-Bewegungen und andere Ausführungskosten nicht stillschweigend ignorieren. Benötigt werden mehrere Aufwärts-, Abwärts- und Seitwärtsphasen, einschließlich 2018, März 2020 und 2022, soweit geeignete Daten verfügbar sind. Regeln vor dem Test festschreiben; spätere chronologische Zeiträume zurückhalten und anschließend ohne Nachoptimierung im Paper-Trading prüfen. Bereits bekannte Krisen sind Stresstests, keine vollständig unbekannte Zukunft.

Für das enge Grid sind Tageskerzen allein keine verlässliche Ausführungssimulation. Hochauflösende Trades oder mindestens Minutenkerzen mit konservativer Behandlung unklarer Intrakerzen-Reihenfolgen verwenden. Nicht jede Limitberührung garantiert einen Fill. Signale erst nach Kerzenschluss verfügbar machen; keine Ausführung rückwirkend am Tief derselben Kerze. Teilfüllungen, Storno-Rennen, Gebühren auf beide Handelsseiten, Spread und Slippage berücksichtigen. Zusätzlich mit erhöhten Ausführungskosten testen.

Entscheidend sind Gesamtkontowert einschließlich unrealisierter Gewinne/Verluste, maximaler Drawdown, Erholungsdauer, Nettoergebnis vor individueller Steuerbetrachtung, Kapitalbindung, Gebühren, Anzahl unabhängiger Handelsphasen und Verlust nach Stops. Am Testende offene Bestände zu Marktpreisen bewerten, nicht ausblenden. Eine hohe Trefferquote der abgeschlossenen Trades genügt nicht. Die zahlreichen Grid-Fills innerhalb eines Trends sind keine unabhängigen Bestätigungen.

Die Zusatzregeln bleiben nur, wenn der Vorteil über mehrere getrennte Zeitabschnitte und kleine Parameteränderungen erkennbar bleibt. Unterschiede mit sehr wenigen Marktwechseln als unsicher ausweisen. 1D/3D/1W sowie Schwelle 20/30 nur als begrenzte, dokumentierte Robustheitsprüfungen behandeln; anschließend neue ungesehene Daten benötigen, wenn daraus eine andere Variante ausgewählt wird.

**Anschluss an das vorhandene Projekt**

Umgesetzt sind derzeit: der minütlich aktualisierte 1D-Filter aus MACD-Histogramm und StochRSI-Linienlage, Kaufstorno bei fehlender Freigabe, MACD-Nulllinien-Ausstieg, Überwachung deaktivierter Paare mit Altpositionen, Nutzung von `maxBuyAmount` als gebundenes Gesamtbudget, der konfigurierte harte Stop und dessen persistente 24-Stunden-Kaufsperre. Der beschriebene Varianten-Backtest ist noch nicht umgesetzt.

- Kauf-Freigabe vom bestehenden buyStatus entkoppeln. Der aktuelle Hauptloop verarbeitet nur darüber aktivierte Währungen und könnte sonst auch deren Verkäufe auslassen. Alle Paare mit eigenen Positionen oder offenen/ungeklärten Orders müssen weiter überwacht werden.
- Eigene bereits vorhandene Positionen bei einer späteren Aktivierung ausdrücklich in die Exit-Regeln übernehmen; Positionen außerhalb des Bots nicht erfassen. Die historische Wirkung auf alte Bestände gesondert sichtbar machen.
- Der aktuelle Client bietet Intervalle bis 1D. 3D aus lückenlosen Tagesdaten mit festem UTC-Anker bilden und den Anker dokumentieren. Keine gleitende Neugruppierung bei jedem Datenabruf. Andere Chartanbieter können abweichende Anker nutzen.
- Nur abgeschlossene Kerzen aus chronologisch sortierten, ausreichend langen Reihen verwenden. Datenlücken, Zeitstempel und Aktualität prüfen. Anlaufhistorie ist nicht gleich Signalverzögerung. Keine API-Neuberechnung je Grid-Order; neue Kerzenschlüsse bestimmen die Aktualisierung.
- StochRSI verwendet derzeit 21/21/5/4 trotz anderslautender Kommentare. 14/14/3/3 im Test explizit übergeben. Ergebnisobjekte verwenden K/D von 0 bis 1, Wrapper von 0 bis 100: 20 bedeutet intern 0,20. Vergleiche möglichst vor Darstellungsrundung durchführen.
- Das dauerhafte bullische MACD-Verhältnis von einem echten Zweikerzen-Crossover unterscheiden. Bei Stornierungen mögliche Fills zuerst sauber abgleichen; eine Stornoanforderung beseitigt die Exposure-Reservierung noch nicht.

Ergebnis dieses Entwurfs ist ein Testkonzept mit klaren Entscheidungsregeln. Risikowerte, UTC-Anker und technische Datengrenzen sind vor dem Test festzulegen. Eine spätere Live-Freigabe setzt zusätzlich die verifizierte Ausführung und die Vergleichsergebnisse voraus.
