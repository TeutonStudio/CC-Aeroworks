# Lokale Mod-Abhängigkeiten

Die Fremdmods in diesem Verzeichnis werden **nicht** durch CC-Aeroworks verteilt. Beschaffe sie ausschließlich über die offiziellen Projektseiten und beachte deren Lizenzen. Lege keine fremden JARs in Git ab.

## Standardverzeichnis

Der Build liest standardmäßig `libs/`. Ein anderer absoluter oder projektrelativer Pfad kann gesetzt werden:

```bash
./gradlew -Pmod_dependency_dir=/pfad/zu/cc-aeroworks-mods verifyModDependencies
```

Unter Windows:

```bat
gradlew.bat -Pmod_dependency_dir=C:\mods\cc-aeroworks verifyModDependencies
```

## Abhängigkeiten

| Mod | Mod-ID | Version | Status |
|---|---|---:|---|
| Create | `create` | 6.0.10 | erforderlich |
| Create: Aeronautics | `aeronautics` | 1.3.0 | erforderlich |
| Create: Aeroworks | `aeroworks` | 1.3.0 | erforderlich |
| CC: Tweaked | `computercraft` | 1.119.0 | erforderlich |
| Sable | `sable` | 2.0.1 | erforderlich |
| Drive By Wire | `drive_by_wire` | 0.2.9 | optional |
| Create: Radars | `create_radar` | 0.4.4-1.21.1 | automatisch für Entwicklungsruns |
| Create Big Cannons | `createbigcannons` | 5.11.7 | optional, automatisch für Entwicklungsruns |
| Ritchie's Projectile Library | `ritchiesprojectilelib` | 2.1.2 | CBC-Laufzeitbibliothek, automatisch für Entwicklungsruns |
| Just Enough Items | `jei` | 19.27.0.340 | Entwicklungswerkzeug, automatisch für Entwicklungsruns |

Das offizielle Create-Aeronautics-Artefakt für diese Baseline heißt:

```text
create-aeronautics-bundled-1.21.1-1.3.0.jar
```

Create Aeronautics benötigt neben Create auch Sable zur Laufzeit. Sable ist daher für den Baseline-Client und -Server ein Pflichtartefakt.

Create: Radars benötigt in der verwendeten 1.21.1-Laufzeit Create Big Cannons. CBC benötigt wiederum Ritchie's Projectile Library. `runClient` und die übrigen lokalen Gradle-Runtime-Classpaths lösen deshalb alle drei Artefakte automatisch über CurseMaven als `localRuntime` auf. Versionen und CurseForge-Datei-IDs sind in `gradle.properties` festgelegt.

JEI wird zusätzlich über das offizielle BlameJared-Maven als `localRuntime` geladen. Die festgelegte Version `19.27.0.340` ist mit der Projektbasis NeoForge `21.1.228` kompatibel und ermöglicht die manuelle Kontrolle der Crafting- und Create-Verarbeitungsrezepte im Entwicklungsclient. JEI ist kein Bestandteil der veröffentlichten CC-Aeroworks-Abhängigkeiten.

Manuell in `libs/` abgelegte offizielle JARs für Create: Radars, Create Big Cannons, Ritchie's Projectile Library oder JEI werden aus dem allgemeinen Datei-Classpath ausgeschlossen, damit ihre Mod-IDs nicht doppelt geladen werden. Die veröffentlichte CC-Aeroworks-Mod bettet keines dieser Artefakte ein.

Die maschinenlesbaren Dateimuster und bekannte offizielle Beispieldateinamen stehen in [`dependencies.json`](dependencies.json). Dateinamen dürfen den üblichen Plattformzusatz enthalten, müssen aber Modname und Version eindeutig enthalten. Source-, API- oder Development-JARs sind nicht zulässig.

## Prüfsummen

`dependencies.json` unterstützt pro Artefakt eine feste SHA-256-Prüfsumme. Die Felder bleiben zunächst `null`, weil verschiedene offizielle Distributionsplattformen identische Modversionen mit unterschiedlich benannten oder neu signierten Dateien ausliefern können. Sobald die vom Team verwendeten Zielartefakte festgelegt sind, werden deren SHA-256-Werte eingetragen und damit verbindlich.

```bash
sha256sum libs/*.jar
```

```powershell
Get-FileHash -Algorithm SHA256 .\libs\*.jar
```

## CC:Tweaked-Kompatibilitätstest

CC:Tweaked 1.120.0 wird in einem **separaten** Abhängigkeitsverzeichnis getestet. Lege 1.119.0 und 1.120.0 niemals gleichzeitig in denselben Classpath.

```bash
./gradlew -Pmod_dependency_dir=libs-cc-1.120 clean test build
```

## Erwartete Befehle

```bash
./gradlew verifyModDependencies
./gradlew clean test build
./gradlew runClient
```

Fehlt ein Pflichtartefakt oder passen mehrere Dateien auf dasselbe Muster, bricht `verifyModDependencies` mit einer gezielten Meldung ab. Optionale lokale Artefakte werden geprüft, sobald eine passende JAR vorhanden ist. Create: Radars, Create Big Cannons, Ritchie's Projectile Library und JEI benötigen für `runClient` keine lokalen Dateien.
