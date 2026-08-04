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

## Pflichtartefakte

Für die Baseline werden genau diese Versionen erwartet:

| Mod | Mod-ID | Version | Status |
|---|---|---:|---|
| Create | `create` | 6.0.10 | erforderlich |
| Create: Aeronautics | `aeronautics` | 1.3.0 | erforderlich |
| Create: Aeroworks | `aeroworks` | 1.3.0 | erforderlich |
| CC: Tweaked | `computercraft` | 1.119.0 | erforderlich |
| Sable | `sable` | 2.0.1 | optional |
| Drive By Wire | `drive_by_wire` | 0.2.9 | optional |

Die maschinenlesbaren Dateimuster stehen in [`dependencies.json`](dependencies.json). Dateinamen dürfen den üblichen Plattformzusatz enthalten, müssen aber Modname und Version eindeutig enthalten. Source-, API- oder Development-JARs sind nicht zulässig.

## Prüfsummen

`dependencies.json` unterstützt pro Artefakt eine feste SHA-256-Prüfsumme. Die Felder bleiben zunächst `null`, weil verschiedene offizielle Distributionsplattformen identische Modversionen mit unterschiedlich benannten oder neu signierten Dateien ausliefern können. Sobald die vom Team verwendeten Zielartefakte festgelegt sind, werden deren SHA-256-Werte eingetragen und damit verbindlich.

Lokale Prüfsumme bestimmen:

```bash
sha256sum libs/*.jar
```

PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 .\libs\*.jar
```

## CC:Tweaked-Kompatibilitätstest

CC:Tweaked 1.120.0 wird in einem **separaten** Abhängigkeitsverzeichnis getestet. Lege 1.119.0 und 1.120.0 niemals gleichzeitig in denselben Classpath. Beispiel:

```bash
./gradlew -Pmod_dependency_dir=libs-cc-1.120 clean test build
```

Der Standardbuild bleibt auf 1.119.0 festgelegt, bis die Kompatibilitätsmatrix vollständig ausgeführt und dokumentiert wurde.

## Erwartete Befehle

```bash
./gradlew verifyModDependencies
./gradlew clean test build
./gradlew runClient
```

Fehlt ein Pflichtartefakt oder passen mehrere Dateien auf dasselbe Muster, bricht `verifyModDependencies` mit einer gezielten Meldung ab.
