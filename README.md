# CC-Aeroworks

CC-Aeroworks verbindet Create: Aeroworks Control Desks direkt mit CC:Tweaked und ergänzt zwei- und dreistellige Desk-Displays sowie die kombinierte Maussteuerung für Lever, Joystick und Throttle Quadrants. Die Displays unterstützen Ziffern und frei beschreibbare `7x5`- beziehungsweise `11x5`-Pixelraster. Das zweistellige Display passt in kleine und große Slots, das dreistellige ausschließlich in große Slots. Drive By Wire 0.2.9 ist optional unterstützt.

Für die kombinierte Steuerung im Aeroworks-Modulbildschirm den Input Type `Kombiniert` auswählen, anschließend im mittleren Eingabefeld die gewünschte Aktivierungstaste erfassen und diese beim Steuern halten. Der Modus wechselt über das vorhandene Symbol zyklisch zwischen `Buttons`, `Analog` und `Kombiniert`. Das API-Handbuch liegt als Buchitem im Abschnitt `CC-Aeroworks` des Aeroworks-Creative-Tabs.

Die Control-Desk-Anschlüsse heißen in Lua `left`, `right` und `big` (kompatible Indizes: `0`, `1`, `2`). Das Handbuch im Spiel und [die Peripheral-Dokumentation](docs/cc-peripheral-api.md) verwenden diese Namen.

## Entwicklungsstand

Das Projekt ist eine frühe Integrationsversion. Build- und Testinfrastruktur sind vorhanden, die vollständige interaktive Laufzeitmatrix ist jedoch noch nicht ausgeführt. Der aktuelle, absichtlich blockierte Baselinebericht liegt unter [`docs/test-results/baseline-1.0.md`](docs/test-results/baseline-1.0.md). Ein erfolgreicher Compilerlauf allein gilt nicht als Nachweis für Rendering, Persistenz, Sable oder Combined Input. Software wird durch Zuversicht leider nicht deterministischer.

## Entwicklungsumgebung

- Minecraft 1.21.1, NeoForge 21.1.228 und Java 21
- Kotlin 2.2.20 mit KotlinForForge NeoForge 5.11.0
- Create 6.0.10, Aeronautics/Aeroworks 1.3.0
- CC:Tweaked API-Baseline 1.119.0; Metadatenbereich bis vor 1.121

## Frischer Clone

Der eingecheckte Bootstrap benötigt ausschließlich Java 21. `gradlew` lädt die in `gradle/wrapper/gradle-wrapper.properties` festgelegte Gradle-Distribution und akzeptiert sie nur bei passender SHA-256-Prüfsumme.

Repositorydateien ohne Fremd-JARs prüfen:

```bash
python3 tools/verify-repository.py
./gradlew verifyDependencyManifest
```

Die Fremdmod-JARs werden nicht mitgeliefert und dürfen nicht in Git eingecheckt werden. Erwartete Mod-IDs, Versionen und Dateimuster stehen in [`libs/dependencies.json`](libs/dependencies.json); Beschaffungs- und Prüfanweisungen in [`libs/README.md`](libs/README.md).

Nach dem rechtmäßigen Bereitstellen der Baseline-JARs in `libs/`:

```bash
./gradlew verifyModDependencies
./gradlew clean test build
./gradlew runClient
```

Ein alternatives Abhängigkeitsverzeichnis wird ohne Änderung am Buildskript gesetzt:

```bash
./gradlew -Pmod_dependency_dir=/pfad/zu/mods clean test build
```

Fehlende Pflichtartefakte, doppelte Treffer, falsche Dateiversionen und konfigurierte Prüfsummenabweichungen werden vor Kompilierung oder Start mit einer gezielten Meldung abgelehnt.

## Tests

Die unterstützten Profile und Release-Gates stehen in [`docs/runtime-test-matrix.md`](docs/runtime-test-matrix.md). Interaktive Fälle mit Schritten und Erwartungen stehen in [`docs/manual-test-plan.md`](docs/manual-test-plan.md).

Ein vollständiges Profil ausführen und als JSON protokollieren:

```bash
python3 tools/run-integration-profile.py BASE-CLIENT \
  --dependency-dir test-mods/base-cc-1.119
```

Dedicated-Server-Baseline inklusive Smoke-Test:

```bash
python3 tools/run-integration-profile.py BASE-SERVER \
  --dependency-dir test-mods/base-cc-1.119 \
  --server-smoke
```

Der Smoke-Test verwendet ein isoliertes Verzeichnis unter `build/smoke-server`, akzeptiert dort die EULA, wartet auf den Server-`Done`-Marker, erkennt bekannte Crashmarker und beendet die Instanz kontrolliert.

## CI

`.github/workflows/verify.yml` besitzt zwei Stufen:

1. **Repository contract:** läuft bei Push und Pull Request ohne geschützte Mod-JARs. Geprüft werden Pythonwerkzeuge, Repositorystruktur, Wrapper-Bootstrap und Abhängigkeitsmanifest.
2. **Protected full build:** wird manuell aktiviert und benötigt die Repository-Secrets `MOD_DEPENDENCY_URL` und `MOD_DEPENDENCY_SHA256`. Das heruntergeladene ZIP wird vor dem Entpacken geprüft; danach laufen Build, Unit-Tests und Dedicated-Server-Smoke-Test.

Das geschützte ZIP enthält die rechtmäßig bereitgestellten Ziel-JARs im Wurzelverzeichnis oder in Unterverzeichnissen. Es wird weder committed noch als öffentliches Buildartefakt weiterverteilt.

## Dokumentation und Beispiele

- [Peripheral-API](docs/cc-peripheral-api.md)
- [Einführung zur Peripheral-Programmierung](docs/peripheral-programming.md)
- [Integrations-Testharness](docs/integration-test-harness.md)
- [Runtime-Testmatrix](docs/runtime-test-matrix.md)
- [Manueller Testplan](docs/manual-test-plan.md)
- [Lua-Beispiele](examples/cc/)

Repository: `TeutonStudio/CC-Aeroworks`
