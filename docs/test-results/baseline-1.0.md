# Baselinebericht 1.0

## Status

**Gesamtstatus: BLOCKED**

Dieser Bericht wurde mit der Testinfrastruktur angelegt, bevor die geschützten lokalen Modartefakte und eine interaktive Minecraft-Testwelt in der ausführenden Umgebung verfügbar waren. Er dokumentiert ausdrücklich keinen bestandenen Release-Gate. Ein nicht ausgeführter Test wird hier nicht durch Zuversicht ersetzt.

## Zielstand

| Feld | Wert |
|---|---|
| Repository | `TeutonStudio/CC-Aeroworks` |
| Branch | `agent/critical-build-test-plan` |
| Ziel-Commit des Codes | `38b5442d99a736791c1d195912c8367f54db4448` |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| Java | 21 |
| Create | 6.0.10 |
| Aeronautics | 1.3.0 |
| Aeroworks | 1.3.0 |
| CC:Tweaked-Baseline | 1.119.0 |
| Bericht angelegt | 2026-08-04 |

Der Ziel-Commit enthält den strukturierten manuellen Testplan. Spätere reine Ergebnisaktualisierungen dürfen diesen Wert beibehalten; bei Code-, Ressourcen- oder Buildänderungen muss ein neuer Bericht beziehungsweise eine neue Revision mit neuem Ziel-SHA angelegt werden.

## Verfügbare Testinfrastruktur

| Prüfung | Status | Hinweis |
|---|---|---|
| Gradle-Bootstrap | `NOT RUN` | Muss auf einem frischen Clone mit Java 21 ausgeführt werden. |
| `tools/verify-repository.py` | `NOT RUN` | Benötigt lokalen Git-Checkout. |
| `verifyDependencyManifest` | `NOT RUN` | Benötigt Gradle-Initialisierung. |
| `verifyModDependencies` ohne JARs | `NOT RUN` | Erwarteter kontrollierter Fehlschlag muss noch bestätigt werden. |
| Unit-Tests | `BLOCKED` | Pflicht-Mod-JARs in dieser Ausführungsumgebung nicht verfügbar. |
| Vollständiger Build | `BLOCKED` | Pflicht-Mod-JARs in dieser Ausführungsumgebung nicht verfügbar. |
| Dedicated-Server-Smoke-Test | `BLOCKED` | Pflicht-Mod-JARs und Serverlaufzeit nicht verfügbar. |
| Interaktive Clienttests | `BLOCKED` | Keine Minecraft-Client-/Grafikumgebung verfügbar. |

## Profilstatus

| Profil | Status | Blocker |
|---|---|---|
| `BASE-CLIENT` | `BLOCKED` | Lokale Modartefakte und Clientumgebung fehlen. |
| `BASE-SERVER` | `BLOCKED` | Lokale Modartefakte und Serverlaufzeit fehlen. |
| `FALLBACK-CLIENT` | `BLOCKED` | Clientumgebung und Flywheel-Konfiguration fehlen. |
| `MULTI-COMPUTER` | `BLOCKED` | Interaktive Welt mit zwei CC-Computern fehlt. |
| `CC-120` | `BLOCKED` | Separates CC:Tweaked-1.120.0-Artefaktset fehlt. |
| `SABLE-STATIC` | `BLOCKED` | Sable-Artefakt und Schiffstestwelt fehlen. |
| `SABLE-MOVING` | `BLOCKED` | Sable-Artefakt und bewegte Schiffstestwelt fehlen. |
| `DRIVEBYWIRE` | `BLOCKED` | Drive-By-Wire-Artefakt und Testwelt fehlen. |
| `FULL-SERVER` | `BLOCKED` | Vollständiges optionales Server-Artefaktset fehlt. |

## Pflichtfälle `BASE-CLIENT`

| Testfall | Status | Nachweis / Abweichung |
|---|---|---|
| `BUILD-CLIENT-01` | `NOT RUN` | - |
| `PERIPHERAL-DIRECT-01` | `NOT RUN` | - |
| `DISPLAY-TEXT-01` | `NOT RUN` | - |
| `DISPLAY-TEXT-02` | `NOT RUN` | - |
| `DISPLAY-TEXT-03` | `NOT RUN` | - |
| `DISPLAY-TEXT-04` | `NOT RUN` | - |
| `DISPLAY-PIXEL-01` | `NOT RUN` | - |
| `DISPLAY-PIXEL-02` | `NOT RUN` | - |
| `DISPLAY-PIXEL-03` | `NOT RUN` | - |
| `DISPLAY-PIXEL-04` | `NOT RUN` | - |
| `RENDER-FLYWHEEL-01` | `NOT RUN` | - |
| `COMBINED-LEVER-01` | `NOT RUN` | - |
| `COMBINED-JOYSTICK-01` | `NOT RUN` | - |
| `COMBINED-THROTTLE-01` | `NOT RUN` | - |
| `COMBINED-TARGET-01` | `NOT RUN` | - |
| `COMBINED-LIFECYCLE-01` | `NOT RUN` | - |
| `GUIDE-01` | `NOT RUN` | - |

## Pflichtfälle `BASE-SERVER`

| Testfall | Status | Nachweis / Abweichung |
|---|---|---|
| `BUILD-SERVER-01` | `NOT RUN` | - |
| `PERIPHERAL-MODEM-01` | `NOT RUN` | - |
| `PERIPHERAL-LIFECYCLE-01` | `NOT RUN` | - |
| `EVENT-01` | `NOT RUN` | - |
| `DISPLAY-PERSIST-01` | `NOT RUN` | - |

## Ausführungsschritte für die nächste Revision

1. Baseline-JARs gemäß `libs/README.md` in ein isoliertes Verzeichnis legen.
2. Prüfsummen dieser konkreten Artefakte in `libs/dependencies.json` festschreiben.
3. Repositoryprüfung ausführen:

   ```bash
   python3 tools/verify-repository.py
   ```

4. Baselineprofil ausführen:

   ```bash
   python3 tools/run-integration-profile.py BASE-SERVER \
     --dependency-dir test-mods/base-cc-1.119 \
     --server-smoke
   ```

5. `BASE-CLIENT` nach erfolgreichem Build interaktiv nach `docs/manual-test-plan.md` ausführen.
6. Jeden `PASS` mit Logstelle, Screenshotbeschreibung oder reproduzierbarer Weltposition belegen.
7. Für jedes `FAIL` ein Issue oder einen einzelnen Fix-Commit verlinken.
8. Gesamtstatus erst auf `PASS` setzen, wenn sämtliche Release-Gates aus `runtime-test-matrix.md` erfüllt sind.
