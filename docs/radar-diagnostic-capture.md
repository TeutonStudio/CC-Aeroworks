# RadarDisplay diagnostic capture

Dieser Ablauf ist absichtlich auf **einen** reproduzierbaren Spieltest zugeschnitten. Der Diagnose-Build schreibt sehr ausführliche Zeilen mit dem Präfix:

```text
[CCA-RADAR-TRACE]
```

Jede Zeile enthält eine Session-ID, fortlaufende Sequenznummer, logische Seite, Thread, Dimension, Game-Tick und Pultposition.

## Start

Den Entwicklungsclient so starten, dass die Terminalausgabe zusätzlich gespeichert wird:

```bash
./gradlew runClient --stacktrace 2>&1 | tee radar-diagnostic-terminal.log
```

## Exakter Spielablauf

1. Welt laden und mindestens drei Sekunden warten.
2. Ein Pult mit montiertem RadarDisplay aufstellen beziehungsweise den vorhandenen Aufbau laden, aber das Pult zunächst **nicht** verbinden.
3. Sicherstellen, dass Radar und Network Filterer als Create:-Radars-Aufbau grundsätzlich funktionieren.
4. Drei Sekunden warten. Dadurch wird der unverlinkte Ausgangszustand protokolliert.
5. Mit dem nativen Create:-Radars-Data-Link-Gegenstand den Network Filterer auswählen.
6. Mit demselben Gegenstand eine freie Seite des Pults mit RadarDisplay anklicken.
7. Prüfen, dass Create: Radars den physischen Data-Link-Block am Pult platziert.
8. Mindestens fünf Sekunden warten, ohne weitere Blöcke zu verändern.
9. Ein Ziel in Radarreichweite bringen, vorzugsweise zuerst einen Spieler oder Mob.
10. Mindestens fünf Sekunden warten.
11. Falls möglich einen Detection-Filter einmal aus- und wieder einschalten und jeweils zwei Sekunden warten.
12. Den **physischen Data-Link-Block am Pult** abbauen.
13. Mindestens drei Sekunden warten.
14. Spiel normal beenden.

Ein direkt angrenzender Network Filterer/Network Controller am Pult ist für diesen Referenzlauf nicht der Verbindungsmechanismus. Entscheidend ist der native physische Data-Link-Endpoint, weil `NetworkData.getFiltererForEndpoint(dimension, deskPos)` genau diesen Zustand abbildet.

## Dateien für die Analyse

Die wichtigste Datei ist:

```text
run/logs/latest.log
```

Zusätzlich ist die gespeicherte Terminalausgabe nützlich:

```text
radar-diagnostic-terminal.log
```

Optional kann eine kompakte Trace-Datei erzeugt werden:

```bash
grep -E "CCA-RADAR-TRACE|CC-Aeroworks.*Radar endpoint|CC-Aeroworks.*Radar client snapshot|Create: Radars API access failed|Native Create: Radars monitor rendering failed" run/logs/latest.log > radar-trace-only.log
```

Für die eigentliche Fehleranalyse ist die **ungefilterte `latest.log` vorzuziehen**, weil Java-/Mixin-/Renderer-Exceptions außerhalb des Präfixes stehen können.

## Bedeutung der Trace-Stufen

| Präfix | Bereich |
| --- | --- |
| `Cxx` | Client-Bootstrap und Registrierung des Overlay-Events |
| `DL_CLASSIFY` | native `DataLinkBlockItem.getFilterTarget(...)`-Klassifikation des angeklickten Blocks |
| `Mxx` | `ConsoleBlockEntityRadarMixin`-Tick-Hook |
| `Sxx` | serverseitige `NetworkData`-, Gruppe-, Radar-, Filter- und Trackauflösung |
| `Nxx` | tatsächliches `ConsoleBlockEntity`-Clientpacket-NBT auf Server und Client |
| `Rxx` | Registrierung des Pults und `AFTER_BLOCK_ENTITIES`-Overlay-Pass |
| `Dxx` | virtueller nativer Monitor, Reflection, Hydrierung, Sockettransform und `MonitorRenderer.renderRadarDisplay(...)` |

### Entscheidende Stufen

Ein vollständig erfolgreicher Pfad enthält mindestens:

```text
DL_CLASSIFY ... acceptedAsMonitor=true
S11_FILTERER_LOOKUP ... -> <filtererPos>
S12_MONITOR_ENDPOINTS ... containsDesk=true
S15_RADAR_STATE ... running=true
S17_SNAPSHOT_RESULT ... status=ACTIVE
N11_SERVER_WRITE_PAYLOAD ... status:"active"
N21_CLIENT_READ_DECODED ... isFresh=true
R04_OVERLAY_DESK ... attempting native render
D23_HYDRATE_OK ... isLinked=true
D24_RENDERER_LOOKUP ... MonitorRenderer
D30_BEFORE_NATIVE_RENDER ...
D31_NATIVE_RENDER_OK ...
R09_END_BATCH ...
```

Wenn die Anzeige trotz `D31_NATIVE_RENDER_OK` und `R09_END_BATCH` leer bleibt, ist die Netzwerk-/NBT-Kette bewiesen und die Analyse kann sich ausschließlich auf Pose, RenderType, Bufferflush oder Sichtfläche konzentrieren. Wenn eine frühere Stufe fehlt oder einen Skip meldet, ist exakt diese Grenze der nächste Reparaturpunkt.
