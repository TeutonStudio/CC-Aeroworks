# Branch Changes: Kritische Build- und Laufzeitprobleme

## Ziel dieses Branches

Dieser Branch bereitet die Behebung der beiden aktuell kritischen Projektprobleme vor:

1. **Der Build ist aus einem frischen Clone nicht reproduzierbar.**
2. **Zentrale Funktionen sind zwar kompiliert und bis zum Mod-/Serverstart geprüft, aber nicht ausreichend in einer echten Spielwelt verifiziert.**

Der Branch beginnt bewusst mit diesem Ausführungsplan. Funktionsänderungen an Peripheral, Displays, Rendering oder Combined Input sollen erst erfolgen, nachdem Build und Baseline-Testumgebung belastbar sind.

---

## Problem 1: Reproduzierbarer Build

### Aktueller Zustand

- Das README dokumentiert Aufrufe über `./gradlew`, der Gradle-Wrapper ist jedoch nicht vollständig versioniert.
- `.gitignore` schließt derzeit unter anderem `gradle/`, `src/test/`, `tools/`, `examples/` und das gesamte `libs/`-Verzeichnis aus.
- Das README verweist auf Dateien und Beispiele, die dadurch in einem frischen Clone fehlen können.
- Aeroworks, Aeronautics und weitere Zielmods werden als lokale JAR-Abhängigkeiten benötigt und dürfen nicht ungeprüft im Repository verteilt werden.
- Ein lokaler erfolgreicher Build beweist deshalb noch nicht, dass ein zweiter Rechner denselben Stand reproduzieren kann.

### Zielzustand

Ein neuer Entwickler oder CI-Runner kann:

1. das Repository klonen,
2. mit dem eingecheckten Wrapper die korrekte Gradle-Version starten,
3. eindeutig erkennen, welche externen Modartefakte fehlen,
4. rechtmäßig beschaffte Artefakte anhand von Version und Prüfsumme validieren,
5. anschließend Tests und Build mit dokumentierten Befehlen reproduzieren.

Ein Build ohne proprietäre oder nicht redistributierbare Fremd-JARs muss mit einer klaren, kurzen Fehlermeldung abbrechen. Er darf nicht erst nach mehreren Minuten mit schwer lesbaren Kotlin-Importfehlern kollabieren, wie es Buildsysteme aus sportlichem Ehrgeiz gern tun.

### Geplante Änderungen

#### 1. Gradle-Wrapper versionieren

Einzuchecken:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

Prüfung:

```bash
./gradlew --version
```

Der Wrapper muss auf einem sauberen Clone ohne global installierte Gradle-Version funktionieren.

#### 2. `.gitignore` korrigieren

Nicht länger vollständig ignorieren:

- `/gradle/`
- `/src/test/`
- `/tools/`
- `/examples/`
- `/libs/`

Stattdessen nur erzeugte oder fremde Binärdateien ignorieren, beispielsweise:

```gitignore
libs/*.jar
.gradle/
build/
run/
run-data/
.kotlin/
.idea/
*.iml
```

Dokumentation und Manifeste unter `libs/` bleiben versioniert.

#### 3. Abhängigkeitsmanifest ergänzen

Neue versionierte Datei, voraussichtlich `libs/dependencies.json` oder `libs/README.md`, mit mindestens:

- logischem Namen,
- erwartetem Dateinamen,
- Mod-ID,
- Version,
- SHA-256,
- erforderlichem oder optionalem Status,
- erlaubter Bezugsquelle beziehungsweise manueller Beschaffungsanweisung,
- Hinweis, dass die JAR selbst nicht Bestandteil des Repositorys ist.

Erforderliche Baseline:

- Create 6.0.10
- Aeroworks 1.3.0
- Aeronautics 1.3.0
- CC:Tweaked 1.119.0 für die API-Baseline
- Sable 2.0.1, soweit für den vollständigen Zielstand erforderlich

Optionale Matrix:

- CC:Tweaked 1.120.0
- Drive By Wire 0.2.9

#### 4. Frühe Gradle-Validierung einführen

Eine Task wie `verifyModDependencies` soll vor `compileKotlin`, `runClient`, `runServer` und `build` ausgeführt werden.

Sie prüft:

- Existenz aller Pflichtartefakte,
- genau eine passende Datei pro Abhängigkeit,
- SHA-256 oder mindestens eindeutig erwartete Version,
- keine versehentlich doppelt eingelegten Versionen,
- verständliche Fehlerausgabe mit dem Pfad zur Installationsdokumentation.

Beispiel für die gewünschte Fehlermeldung:

```text
Missing required local dependency: Aeroworks 1.3.0
Expected: libs/aeroworks-1.3.0.jar
See: libs/README.md
```

Die Prüfung darf optionale Artefakte nicht verlangen.

#### 5. Abhängigkeitspfad konfigurierbar machen

Der lokale Modpfad soll über eine Gradle-Property überschreibbar sein, beispielsweise:

```properties
mod_dependency_dir=libs
```

Damit können Entwickler und CI dieselben Artefakte außerhalb des Repositorys bereitstellen, ohne `build.gradle` zu verändern.

#### 6. Dokumentation und Beispiele wirklich versionieren

Sicherstellen, dass folgende Pfade im Repository vorhanden und nicht ignoriert sind:

- `libs/README.md`
- `docs/cc-peripheral-api.md`
- `docs/peripheral-programming.md`
- `docs/manual-test-plan.md`
- `examples/cc/`
- `src/test/`
- notwendige Skripte unter `tools/`

README-Befehle werden anschließend auf einem frischen Clone wörtlich ausgeführt. Dokumentation, die nur auf dem Rechner des ursprünglichen Autors funktioniert, ist eher Folklore als Dokumentation.

#### 7. CI in zwei Stufen aufteilen

**Stufe A: Repository- und Wrapper-Prüfung ohne geschützte JARs**

- Wrapper vorhanden und ausführbar
- Gradle-Konfiguration lädt bis zur erwarteten Abhängigkeitsprüfung
- JSON/TOML/Markdown- und Ressourcenvalidierung
- keine versehentlich eingecheckten Fremd-JARs
- Manifest vollständig und intern konsistent

**Stufe B: Vollständiger Build mit bereitgestellten Zielartefakten**

Nur wenn die Artefakte rechtmäßig automatisiert bezogen oder als geschütztes CI-Artefakt bereitgestellt werden können:

```bash
./gradlew clean test build
```

Falls eine öffentliche CI-Beschaffung rechtlich oder technisch nicht möglich ist, wird dies ausdrücklich dokumentiert. Der reproduzierbare Ablauf bleibt dann: frischer Clone plus exakt manifestierte, separat bereitgestellte Ziel-JARs.

### Vorgesehene Commits

1. `build: restore gradle wrapper`
2. `chore: fix repository ignore rules`
3. `docs: document local mod dependencies`
4. `build: validate required mod jars`
5. `test: restore tracked tests and examples`
6. `ci: add repository and full build checks`

### Abnahmekriterien für Problem 1

- [ ] `./gradlew --version` funktioniert in einem frischen Clone.
- [ ] Ohne lokale Pflicht-JARs erscheint eine gezielte Abhängigkeitsmeldung.
- [ ] Mit den dokumentierten JARs läuft `./gradlew clean test build` erfolgreich.
- [ ] Kein Entwickler muss Dateinamen oder Versionen aus alten Chatverläufen erraten.
- [ ] Tests, Tools und Beispiele sind tatsächlich Teil des Repositorys.
- [ ] CI erkennt fehlende Wrapperdateien, versehentlich eingecheckte Mod-JARs und inkonsistente Manifeste.
- [ ] Der README-Ablauf wurde auf einem zweiten Verzeichnis oder Rechner von Null an geprüft.

---

## Problem 2: Fehlende Laufzeitverifikation

### Aktueller Zustand

Der Code deckt bereits viele stark gekoppelte Bereiche ab:

- CC:Tweaked-Capability und Peripheral-Lebenszyklus,
- Eingabe-Polling und Lua-Events,
- Text- und Pixeldisplays,
- Create Display Targets,
- Fallback- und Flywheel-Rendering,
- Combined Input mit Client- und Servervalidierung,
- mehrere Mixins gegen Aeroworks- und Vanilla-Interna,
- optionale Drive-By-Wire- und Sable-Pfade.

Build, Mixin-Anwendung und Start bis Hauptmenü beziehungsweise Dedicated-Server-`Done` sind nützlich, prüfen aber weder Montage noch sichtbares Rendering, Persistenz, Netzwerkzugriff, bewegte Schiffe oder reale Eingabebedienung.

### Zielzustand

Für den aktuellen Einzel-Desk-Funktionsumfang existiert eine dokumentierte, wiederholbare Baseline mit:

- automatisierten Unit- und GameTests, soweit technisch möglich,
- klarer manueller Testmatrix für Render-, UI- und Bewegungsthemen,
- gespeicherten Testergebnissen je Modversionskombination,
- reproduzierbaren Fehlerberichten,
- einem eindeutigen Release-Gate.

Issue #1 und andere größere Features sollen erst auf dieser Baseline aufgebaut werden.

### Geplante Änderungen

#### 1. Testmatrix festschreiben

Mindestens folgende Kombinationen werden geprüft:

| Bereich | Varianten |
|---|---|
| Laufzeit | Client, integrierter Server, Dedicated Server |
| CC:Tweaked | 1.119.0 und 1.120.0 |
| Rendering | Flywheel aktiv und Fallbackpfad |
| Sable | normale Welt, statisches Schiff, bewegtes Schiff |
| Drive By Wire | nicht installiert, 0.2.9 installiert |
| Verbindung | direkter Computer, Wired Modem, zwei Computer |
| Lebenszyklus | Weltneustart, Chunk-Unload/Reload, Blockabbau |

Nicht jede Kombination muss ein vollständiger kartesischer Produkttest werden. Es wird eine minimale paarweise Matrix definiert, die jeden kritischen Pfad mindestens einmal und Kernfunktionen in der Baseline mehrfach abdeckt.

#### 2. Unit-Tests erweitern

Automatisch testbare reine Logik:

- Socketargumente und Socketnamen,
- Displaytext-Normalisierung,
- Zahlenbegrenzung und `zeroPad`,
- Pixelcodierung, Dekodierung und Versionsfallback,
- ungültige Pixelraster,
- Modulbeschreibung für Lua,
- Eingabe-Snapshot-Differenzen,
- Ereignisargumente,
- Rate-Limit-Schlüssel und Bereinigung,
- Combined-Input-Akkumulator,
- zulässige Modul-/Kanalkombinationen.

Die Tests sollen keine laufende Minecraft-Welt benötigen, sofern die Logik in kleine fachliche Klassen extrahiert werden kann.

#### 3. GameTests beziehungsweise Integrationsharness ergänzen

Wo NeoForge GameTests oder ein kleiner Testharness praktikabel sind:

- Displaymodule in gültige und ungültige Sockets montieren,
- Demontage und Drop prüfen,
- Text- und Pixelzustand speichern und neu laden,
- BlockEntity-Synchronisation auslösen,
- Peripheral-Capability am bestätigten `aeroworks:console`-Typ auflösen,
- Attach/Detach mehrerer Computer simulieren,
- Eingabeänderungen ohne doppelte Events prüfen,
- Chunk-/BlockEntity-Invalidierung ohne verbleibende aktive Peripheralreferenz prüfen,
- Payloads mit ungültiger Position, Distanz, Socket, Kanal und Wert ablehnen.

Falls Fremdmodklassen GameTests verhindern, wird die Einschränkung dokumentiert und durch einen reproduzierbaren Server-Smoke-Test ergänzt.

#### 4. Manuellen Testplan in prüfbare Fälle zerlegen

`docs/manual-test-plan.md` wird von einer langen Liste in nummerierte Fälle mit folgendem Schema überführt:

```text
ID: DISPLAY-PERSIST-01
Voraussetzungen: CC 1.119.0, Flywheel aktiv, normaler Level
Schritte: ...
Erwartung: ...
Ergebnis: PASS / FAIL / BLOCKED
Nachweis: Screenshot, Logstelle oder Weltbeschreibung
```

Kategorien:

- `BUILD-*`
- `PERIPHERAL-*`
- `EVENT-*`
- `DISPLAY-TEXT-*`
- `DISPLAY-PIXEL-*`
- `RENDER-FALLBACK-*`
- `RENDER-FLYWHEEL-*`
- `COMBINED-*`
- `SABLE-*`
- `DRIVEBYWIRE-*`
- `GUIDE-*`

#### 5. Testergebnisse versionieren

Neue Datei oder Verzeichnis, beispielsweise:

- `docs/test-results/baseline-1.0.md`

Enthalten:

- getesteter Commit-SHA,
- Datum,
- Java-/NeoForge-/Modversionen,
- verwendete Konfiguration,
- PASS/FAIL/BLOCKED je Testfall,
- bekannte Abweichungen,
- Verweise auf eröffnete Issues.

Binäre Logs und Screenshots müssen nicht dauerhaft im Repository liegen; entscheidende Logausschnitte und eindeutige Reproduktionsschritte schon.

#### 6. Dedicated-Server-Smoke-Test automatisieren

Ein Skript soll:

1. Serverkonfiguration erzeugen,
2. EULA für die isolierte Testinstanz setzen,
3. den Server mit Timeout starten,
4. auf `Done` beziehungsweise bekannte Crashmarker prüfen,
5. sauber beenden,
6. bei Fehlern das relevante Log ausgeben.

Dieser Test prüft insbesondere, dass Clientklassen und Clientmixins nicht auf dem Dedicated Server geladen werden.

#### 7. Laufzeitfehler vor neuen Features beheben

Während der Baselineprüfung gefundene Fehler werden einzeln dokumentiert und nach Schwere priorisiert:

- Crash oder Weltkorruption: blockiert alle weiteren Arbeiten.
- Server-/Client-Desync oder falsche Rechteprüfung: blockiert Release.
- falsches Rendering oder UI-Verhalten: blockiert das betroffene Feature.
- reine Dokumentationsabweichung: wird im selben Branch korrigiert.

Keine pauschalen Sammelcommits wie `v4` oder `fixes`. Jeder Fehler erhält eine Ursache, einen Test und einen gezielten Commit. Menschen mögen Versionsnamen ohne Aussage, Git-Historien leider weniger.

### Vorgesehene Commits

1. `test: define supported runtime matrix`
2. `test: expand display and peripheral unit coverage`
3. `test: add integration test scaffolding`
4. `test: add dedicated server smoke test`
5. `docs: convert manual checks to test cases`
6. `docs: record verified baseline results`
7. weitere gezielte `fix:`-Commits für gefundene Fehler

### Abnahmekriterien für Problem 2

- [ ] Alle bestehenden Kernfunktionen besitzen mindestens einen automatisierten oder dokumentierten manuellen Test.
- [ ] Client und Dedicated Server starten mit der Baseline-Abhängigkeitsmenge.
- [ ] Direktanschluss und Wired Modem funktionieren.
- [ ] Zwei angehängte Computer erhalten keine unerwarteten doppelten oder fehlenden Events.
- [ ] Peripheral-Identität und aktiver Zustand überstehen Chunk-Unload/Reload korrekt.
- [ ] Text- und Pixelzustand bleiben nach Weltneustart erhalten.
- [ ] Fallback- und Flywheel-Rendering wurden sichtbar geprüft.
- [ ] Combined Input wurde für Lever, Joystick und Throttle Quadrant real bedient.
- [ ] Abbruchbedingungen wie Menü, Fokusverlust, Tod, Dimensionwechsel und Blockabbau geben die Kamera frei.
- [ ] Sable-Prüfungen umfassen mindestens ein bewegtes Schiff.
- [ ] CC:Tweaked 1.119.0 und 1.120.0 sind getrennt dokumentiert.
- [ ] Drive By Wire wurde installiert und nicht installiert geprüft.
- [ ] Für jeden FAIL-Fall existiert ein reproduzierbarer Fehlerbericht oder Fix.
- [ ] Ein Release wird blockiert, solange kritische Baseline-Fälle nicht PASS sind.

---

## Reihenfolge der Umsetzung

### Phase A: Buildgrundlage

1. Wrapper und Ignore-Regeln reparieren.
2. Abhängigkeitsmanifest und Validierung hinzufügen.
3. Tests, Tools und Beispiele wieder versionieren.
4. Frischen Clone auf einem neutralen Pfad bauen.

### Phase B: Automatisierte Baseline

1. Unit-Tests erweitern.
2. Server-Smoke-Test einrichten.
3. GameTest-/Integrationsmöglichkeiten ausschöpfen.
4. CI-Gates aktivieren.

### Phase C: Manuelle Laufzeitmatrix

1. Einzel-Desk-Baseline in normaler Welt.
2. Netzwerk- und Mehrcomputerfälle.
3. Renderingpfade.
4. Combined Input.
5. Sable und Drive By Wire.
6. CC:Tweaked 1.120.0-Kompatibilität.

### Phase D: Stabilisierung

1. Gefundene Fehler einzeln beheben.
2. Tests als Regressionstests ergänzen.
3. Baselinebericht abschließen.
4. Erst danach Issue #1 beziehungsweise Desk-Cluster beginnen.

---

## Nicht Bestandteil dieses ersten Fix-Branches

- Multiblock-/Desk-Cluster-Unterstützung aus Issue #1
- neue Displaygrößen oder Modultypen
- Erweiterung der Lua-API um zusätzliche Features
- grundlegender Austausch des Renderingansatzes
- Unterstützung weiterer Aeroworks-Versionen

Diese Themen bleiben absichtlich außerhalb des Branchumfangs, bis Build und bestehende Funktionen verlässlich geprüft sind.

---

## Definition of Done für den Gesamtbranch

Der Branch ist abgeschlossen, wenn:

1. ein frischer Clone mit dokumentierten legal bereitgestellten Abhängigkeiten erfolgreich baut,
2. die wichtigsten Prüfungen automatisiert laufen,
3. die vollständige Baseline-Testmatrix dokumentiert ausgeführt wurde,
4. keine offenen kritischen Laufzeitfehler verbleiben,
5. der nächste Featurebranch auf einem bekannten und reproduzierbaren Zustand aufsetzen kann.
