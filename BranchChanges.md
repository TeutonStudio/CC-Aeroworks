# Branch Changes: Kritische Build- und Laufzeitprobleme

## Ziel

Dieser Branch behebt die beiden infrastrukturell kritischen Probleme des aktuellen Projekts:

1. Der Build war aus einem frischen Clone nicht reproduzierbar und verwies auf ignorierte beziehungsweise fehlende Dateien.
2. Die vorhandenen Modfunktionen besaßen keine belastbare, wiederholbare Laufzeitbaseline.

Multiblock-/Desk-Cluster-Unterstützung aus Issue #1 bleibt ausdrücklich außerhalb dieses Branches. Neue Features auf eine unbekannte Baseline zu stapeln wäre architektonisch mutig und praktisch töricht.

## Aktueller Gesamtstatus

| Bereich | Implementierung | Ausführung / Verifikation |
|---|---|---|
| Reproduzierbarer Build | weitgehend umgesetzt | Fresh-Clone- und Vollbuild mit Ziel-JARs noch auszuführen |
| Öffentliche Repository-CI | umgesetzt | Workflow-Datei committed; für den aktuellen Push war beim Abschluss noch kein Lauf sichtbar |
| Geschützter Vollbuild | umgesetzt | Secrets und rechtmäßig bereitgestelltes Dependency-ZIP noch zu konfigurieren |
| Unit-Testbasis | erweitert | vollständige Ausführung durch fehlende Ziel-JARs blockiert |
| Dedicated-Server-Smoke-Test | umgesetzt | Ausführung durch fehlende Ziel-JARs blockiert |
| Manuelle Laufzeitmatrix | vollständig spezifiziert | interaktive Ausführung blockiert |
| Baselinebericht | angelegt | Gesamtstatus absichtlich `BLOCKED` |

Der Branch behauptet keinen bestandenen Release-Gate. `NOT RUN` und `BLOCKED` werden nicht in `PASS` umbenannt, nur weil die Dokumentation inzwischen ordentlich aussieht.

---

## Umgesetzte Commits

| Commit | Änderung |
|---|---|
| `e3be5a7` | `BranchChanges.md` mit ursprünglichem Ausführungsplan angelegt |
| `e1f090a` | `build: restore gradle bootstrap wrapper` |
| `e423822` | `chore: fix repository ignore rules` |
| `fc07492` | `docs: document local mod dependencies` |
| `7150cff` | `build: validate required mod jars` |
| `9440f3e` | `test: expand display and peripheral unit coverage` |
| `9d10544` | `fix: harden dependency validation script` |
| `2779013` | `test: restore tracked tests and examples` |
| `e735563` | `test: define supported runtime matrix` |
| `6d8e705` | `test: add integration test scaffolding` |
| `964ccfa` | `test: add isolated server smoke run` |
| `253d8a8` | `build: validate smoke server dependencies` |
| `abf4542` | `test: add dedicated server smoke test` |
| `38b5442` | `docs: convert manual checks to test cases` |
| `5e170a2` | `docs: record blocked baseline test results` |
| `7511d2e` | `ci: add repository and protected build checks` |
| `9e05a2c` | `docs: document reproducible build and test workflow` |

---

## Problem 1: Reproduzierbarer Build

### 1. Gradle-Einstieg

Versioniert wurden:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/wrapper/GradleBootstrap.java`

Der Bootstrap läuft als Java-21-Quelldatei, lädt die festgelegte Gradle-Distribution, prüft deren SHA-256 und verwendet ein gesperrtes Cacheverzeichnis unter `GRADLE_USER_HOME`.

### Abweichung vom ursprünglichen Plan

Der ursprüngliche Plan sah das binäre `gradle-wrapper.jar` vor. Die GitHub-Schreibschnittstelle konnte den Fremd-Blob nicht zuverlässig zwischen Repositories übertragen. Statt eine beschädigte Binärdatei mit überzeugendem Namen zu committen, verwendet der Branch einen prüfbaren Java-Quellbootstrap. Vor einer Zusammenführung kann er auf einem lokalen Checkout durch den offiziellen Wrapper ersetzt werden:

```bash
./gradlew wrapper
./gradlew wrapper
```

Dabei müssen die bestehende Gradle-Version und `distributionSha256Sum` erhalten beziehungsweise erneut geprüft werden.

### 2. Ignore-Regeln

`.gitignore` ignoriert nicht länger pauschal:

- `gradle/`
- `src/test/`
- `tools/`
- `examples/`
- `libs/`

Weiterhin ignoriert werden lokale Fremd-JARs und generierte Build-, IDE-, Run- und Testbinärdateien.

### 3. Abhängigkeitsmanifest

Versioniert wurden:

- `libs/dependencies.json`
- `libs/README.md`

Die Baseline beschreibt Create 6.0.10, Aeronautics 1.3.0, Aeroworks 1.3.0 und CC:Tweaked 1.119.0. Sable 2.0.1 und Drive By Wire 0.2.9 sind als optionale Integrationen erfasst.

Die SHA-256-Felder der Fremdmods bleiben `null`, bis das Team die konkret verwendeten offiziellen Artefakte festgelegt hat. Der geschützte CI-Download selbst benötigt bereits eine feste SHA-256 des gesamten ZIPs.

### 4. Frühe Gradle-Validierung

`gradle/dependency-validation.gradle` stellt bereit:

- `verifyDependencyManifest`
- `verifyModDependencies`

Geprüft werden:

- Manifestschema und Pflichtfelder,
- doppelte Mod-ID-/Versionsidentitäten,
- gültige Dateimuster,
- genau ein Treffer pro Pflichtartefakt,
- optionale SHA-256-Prüfsummen,
- doppelte passende JARs,
- konfigurierbares Verzeichnis über `-Pmod_dependency_dir=...`.

Die Prüfung ist vor Compile-, Build-, Client-, Server- und Smoke-Server-Aufgaben verdrahtet.

### 5. Repositoryvertrag

`tools/verify-repository.py` prüft ohne Fremd-JARs:

- benötigte Repositorydateien,
- ausführbares `gradlew`,
- JSON-Manifestschema,
- Wrapper-URL und Prüfsummenformat,
- keine eingecheckten JARs,
- UTF-8 und erwartete Zeilenenden.

### 6. CI

`.github/workflows/verify.yml` besitzt zwei Stufen.

**Repository contract** läuft bei Push und Pull Request:

- Java 21 einrichten,
- Pythonwerkzeuge kompilieren,
- Repositoryvertrag ausführen,
- Java-Bootstrap kompilieren,
- Gradle-Abhängigkeitsmanifest konfigurativ validieren.

**Protected full build** wird manuell aktiviert:

- Dependency-ZIP über `MOD_DEPENDENCY_URL` beziehen,
- ZIP gegen `MOD_DEPENDENCY_SHA256` prüfen,
- JARs in ein temporäres Verzeichnis extrahieren,
- `BASE-SERVER`-Profil mit Unit-Tests, Build und Server-Smoke-Test ausführen,
- Testberichte und Buildausgabe als kurzlebiges CI-Artefakt sichern.

### Abnahmestand Problem 1

| Kriterium | Status |
|---|---|
| Gradle-Einstieg ist versioniert | `IMPLEMENTED` |
| Manifest und Installationsdokumentation sind versioniert | `IMPLEMENTED` |
| Fehlende und doppelte JARs werden früh erkannt | `IMPLEMENTED / NOT RUN` |
| Dependency-Pfad ist konfigurierbar | `IMPLEMENTED` |
| Tests, Tools und Beispiele sind Teil des Repositorys | `IMPLEMENTED` |
| Öffentliche CI prüft Repository und Manifest | `IMPLEMENTED / NOT RUN` |
| Geschützter Vollbuild ist definiert | `IMPLEMENTED / BLOCKED` |
| `./gradlew --version` auf frischem Clone | `NOT RUN` |
| `./gradlew clean test build` mit Ziel-JARs | `BLOCKED` |
| README-Ablauf auf neutralem Checkout | `BLOCKED` |

---

## Problem 2: Fehlende Laufzeitverifikation

### 1. Testmatrix

`docs/runtime-test-matrix.md` definiert paarweise Profile für:

- Client und Dedicated Server,
- CC:Tweaked 1.119.0 und 1.120.0,
- Flywheel und Fallback-Rendering,
- direkte Verbindung, Wired Modem und zwei Computer,
- Sable statisch und bewegt,
- Drive By Wire installiert und nicht installiert,
- Welt-, Chunk-, BlockEntity- und Verbindungslebenszyklen.

Jedes Profil nennt Pflichtfälle und Release-Gates.

### 2. Unit-Testbasis

Neu beziehungsweise erweitert wurden Tests und reine Hilfsschichten für:

- stabile Socketnamen und Socketreihenfolge,
- Lua-Modulbeschreibungen einschließlich Text-/Pixelmodus,
- Combined-Input-Akkumulator und Grenzen,
- deterministische Eingabe-Snapshot-Differenzen,
- dokumentierte Ereignisargumente.

`ControlDeskPeripheralState` verwendet nun die testbare Snapshot-Differenz und erzeugt Eventargumente über eine reine Hilfsfunktion.

### 3. Integrationsharness

`tools/run-integration-profile.py`:

1. validiert das gewählte Dependency-Verzeichnis,
2. führt Unit-Tests und Build aus,
3. kann den Server-Smoke-Test ergänzen,
4. speichert Commit, Branch, Plattform, Befehle, Exitcodes und Laufzeiten als JSON.

NeoForge-GameTests sind noch nicht erfunden worden, nur um eine Checkbox zu füllen. `docs/integration-test-harness.md` beschreibt die Voraussetzungen für echte Desk-GameTests gegen Aeroworks.

### 4. Dedicated-Server-Smoke-Test

Umgesetzt wurden:

- eigener ModDevGradle-Run `smokeServer`,
- isoliertes Verzeichnis unter `build/smoke-server`,
- EULA- und Serverkonfiguration,
- Timeout,
- Prüfung des `Done`-Markers,
- bekannte Crashmarker,
- kontrolliertes `stop`, danach Terminate/Kill-Fallback,
- relevante Logausgabe bei Fehlern.

### 5. Manuelle Fälle

`docs/manual-test-plan.md` enthält eindeutige IDs, Voraussetzungen, Schritte und Erwartungen für:

- Build und Start,
- Peripheral und Lifecycle,
- Text- und Pixeldisplays,
- Events und mehrere Computer,
- Flywheel und Fallback,
- Combined Input für Lever, Joystick und Throttle,
- Zielauswahl und Abbruchbedingungen,
- Sable,
- Drive By Wire,
- Creative Tab und Handbuch.

### 6. Baselinebericht

`docs/test-results/baseline-1.0.md` ist versioniert und nennt:

- Ziel-Commit,
- exakte Baselineversionen,
- Profil- und Testfallstatus,
- konkrete Blocker,
- nächste Ausführungsschritte.

Der Gesamtstatus ist `BLOCKED`, weil die erforderlichen lokalen Modartefakte und interaktiven Laufzeitumgebungen in dieser Ausführung nicht vorhanden waren.

### Abnahmestand Problem 2

| Kriterium | Status |
|---|---|
| Kernfunktionen besitzen automatisierte oder dokumentierte Tests | `IMPLEMENTED` |
| Laufzeitprofile und Release-Gate sind definiert | `IMPLEMENTED` |
| Unit-Testbasis ist erweitert | `IMPLEMENTED / NOT RUN` |
| Integrationsrunner ist vorhanden | `IMPLEMENTED / NOT RUN` |
| Dedicated-Server-Smoke-Test ist vorhanden | `IMPLEMENTED / BLOCKED` |
| Client- und Serverstart Baseline | `BLOCKED` |
| Direktanschluss und Wired Modem | `NOT RUN` |
| Zwei-Computer-Eventfanout | `NOT RUN` |
| Chunk-/Peripheral-Lifecycle | `NOT RUN` |
| Text-/Pixelpersistenz | `NOT RUN` |
| Flywheel-/Fallback-Rendering | `NOT RUN` |
| Combined Input real bedient | `NOT RUN` |
| Bewegtes Sable-Schiff | `NOT RUN` |
| CC:Tweaked 1.119.0 und 1.120.0 getrennt geprüft | `NOT RUN` |
| Drive By Wire installiert/nicht installiert | `NOT RUN` |
| Release wird bei unvollständiger Baseline blockiert | `IMPLEMENTED` |

---

## Verbleibende verpflichtende Arbeit vor Merge

1. Repository lokal frisch klonen.
2. `python3 tools/verify-repository.py` ausführen.
3. `./gradlew --version` und `./gradlew verifyDependencyManifest` ausführen.
4. Die konkret verwendeten offiziellen Fremd-JARs festlegen und deren SHA-256 in `libs/dependencies.json` eintragen.
5. `BASE-SERVER` mit `--server-smoke` ausführen.
6. `BASE-CLIENT` und die übrigen Profile interaktiv ausführen.
7. `docs/test-results/baseline-1.0.md` mit realen Nachweisen aktualisieren.
8. Jeden gefundenen Laufzeitfehler in einem gezielten `fix:`-Commit mit Regressionstest beheben.
9. Erst bei bestandenen Release-Gates zusammenführen.

## Nicht Bestandteil dieses Branches

- Multiblock-/Desk-Cluster-Unterstützung aus Issue #1
- neue Displaygrößen oder Modultypen
- zusätzliche Lua-API-Features
- grundlegender Austausch des Renderings
- Unterstützung weiterer Aeroworks-Versionen

## Definition of Done

Die Implementierung der Infrastruktur ist abgeschlossen. Der Gesamtbranch ist erst vollständig erledigt, wenn ein frischer Clone mit den manifestierten legal bereitgestellten Abhängigkeiten baut und die verpflichtenden Laufzeitprofile mit Nachweisen `PASS` sind. Bis dahin bleibt der Branch korrekt blockiert, statt durch semantische Gymnastik „fertig“ genannt zu werden.
