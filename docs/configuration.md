# Configuration

CC-Aeroworks verwendet getrennte Client- und Serverkonfigurationen.

## Client

Die Clientdatei ist `config/cc_aeroworks-client.toml`.

- `combinedLeverSensitivity`: Änderung des Gleitkommaakkumulators pro vertikalem Mausdelta; Standard `0.15`.
- `combinedLeverInvertY`: kehrt die vertikale Richtung um; Standard `false`.
- `combinedLeverPacketRate`: maximale Pakete pro Sekunde; Standard `20`, Bereich 1 bis 20.
- `freezeCameraOnlyWithValidTarget`: Sicherheitsoption, Standard `true`. Unabhängig vom Wert friert die Implementierung ohne gültigen Lever nie die Kamera ein; `false` erweitert aus Sicherheitsgründen nicht den Zielbereich.

Die Aktivierungstaste wird pro Achse direkt im Aeroworks-Modulbildschirm gespeichert. Nach Auswahl des Input Types `Kombiniert` zeigt das mittlere Eingabefeld nicht mehr die Mausachse, sondern die Aktivierungstaste. Linksklick startet die Tastenerfassung, Rechtsklick löscht sie. Beim erstmaligen Umschalten auf `Kombiniert` wird eine leere Belegung mit `K` vorbelegt.

Ein Linksklick auf das vorhandene Modussymbol schaltet bei Lever, Joystick und jeder Achse des Throttle Quadrant zyklisch `Buttons -> Analog -> Kombiniert -> Buttons`. Beim Throttle Quadrant besitzen `red`, `amber`, `green` und `blue` jeweils eine eigene Aktivierungstaste. Der Joystick besitzt Bindings für `x` und `y`: links/rechts verwendet Maus X, vor/zurück Maus Y. Sind beide Joystick-Achsen auf dieselbe Taste gelegt, werden X und Y bei gehaltener Taste parallel gesteuert. Gleiches gilt für mehrere Quadrant-Kanäle mit identischer Taste.

## Server und Displayauflösung

Die Serverdatei heißt `cc_aeroworks-server.toml`. Sie liegt weltbezogen unter `<welt>/serverconfig/cc_aeroworks-server.toml` und wird an verbundene Clients synchronisiert.

- `display.ppb`: Pixeldichte in **Parts per Block**; Standard `256`, Minimum `16`.

`16 PPB` entspricht der normalen Minecraft-Texturdichte von 16 Teilen pro Blockkante. Kleine und große Displays besitzen keine getrennt einstellbaren X/Y-Auflösungen mehr. Stattdessen wird das Raster aus der tatsächlichen Moduloberfläche und einer einzigen Pixeldichte abgeleitet. Dadurch besitzen X und Y immer denselben physischen Pixelabstand und jeder Pixel bleibt quadratisch.

Die nutzbaren Displayflächen entsprechen den Modulmodellen:

- kleines Display: `7/16 × 7/16` Block,
- großes Display: `10/16 × 7/16` Block.

Für jede Achse gilt:

```text
Pixelanzahl = floor(Oberfläche_in_16tel × PPB / 16)
```

Daraus ergeben sich beispielsweise:

| PPB | kleines Display | großes Display |
| ---: | ---: | ---: |
| 16 | 7 × 7 | 10 × 7 |
| 256 | 112 × 112 | 160 × 112 |

Bei PPB-Werten, die nicht durch 16 teilbar sind, wird abgerundet. Der verbleibende Bruchteil wird beim Rendern symmetrisch als Rand verteilt, statt das Raster über die physische Modulfläche hinauszuschieben.

Die alten Schlüssel `display.small.width`, `display.small.height`, `display.large.width` und `display.large.height` werden nicht mehr ausgewertet. Eine bestehende Serverkonfiguration erhält deshalb nach der Umstellung den neuen Standardwert `display.ppb = 256`, sofern kein PPB-Wert gesetzt wurde.

Die aktuell wirksame Auflösung ist in Lua über `getDisplaySize(socket)` verfügbar. Die Rückgabe enthält `width`, `height`, `ppb`, `surfaceWidthParts` und `surfaceHeightParts`. Displaybeschreibungen enthalten zusätzlich weiterhin die kompatiblen Felder `pixelWidth`, `pixelHeight`, `PIXEL_WIDTH` und `PIXEL_HEIGHT`. Kotlin-Code kann dieselben Rasterwerte über `DeskDisplayType.pixelWidth`, `DeskDisplayType.pixelHeight` und `DeskDisplayType.pixelResolution` abrufen.

Der Pixel-Editor im API-Handbuch verwendet das reale konfigurierte Raster. Hohe Auflösungen werden als kompakte Übersicht ohne hunderte überlappende Achsenbeschriftungen dargestellt; der kopierbare Lua-Rastercode bleibt vollständig, während die sichtbare Codevorschau gekürzt wird.

Eine PPB-Änderung verändert den Rastervertrag. Pixelzustände werden seit dem neuen Format bitgepackt und speichern ihre eigene Breite und Höhe. Passt ein gespeichertes Raster nicht mehr zur aktuellen PPB-Auflösung, wird es als leeres Raster im Pixelmodus behandelt und nicht als Displaytext missverstanden. Das Raster muss anschließend durch das Skript neu gezeichnet werden. Textzustände bleiben davon unberührt.

Touchereignisse melden weiterhin die rasterbezogenen Felder `x`, `y`, `width` und `height`. Zusätzlich werden die normierten Oberflächenkoordinaten `u` und `v` im Bereich `0..1` bereitgestellt. Das ROM-Modul `touchdisplay` stellt sie über `normalizedPosition(event)` bereit, damit Handler nicht von einer bestimmten PPB-Auflösung abhängen müssen.

## Server und Create-Telemetrie

Unter `telemetry` steuert die Serverkonfiguration den Runtime-Speicher und die Lifecycle-Prüfungen der Create-Display-Link-Telemetrie:

- `telemetry.maxSourcesPerEndpoint`: maximale Zahl gespeicherter Display-Link-Quellen pro ComputerControlDesk oder Docking Connector; Standard `128`, Bereich `1..4096`.
- `telemetry.maxListEntries`: maximale Zahl zurückgegebener Einträge einer Item-/Fluidliste; Standard `128`, Bereich `1..4096`. `entryCount` und Gesamtmengen bleiben vollständig und `truncated=true` markiert die Kürzung.
- `telemetry.staleAfterTicks`: Alter ohne Refresh, ab dem eine Source `stale=true` meldet; Standard `220`, Bereich `1..72000`.
- `telemetry.validationIntervalTicks`: Intervall für die Prüfung, ob bekannte Display Links noch existieren, dieselbe Source verwenden und auf denselben Endpoint zeigen; Standard `20`, Bereich `1..1200`.
- `telemetry.dockScanIntervalTicks`: Intervall für den Sable-sublevelweiten Scan nach optionalen Simulated-Docking-Connectoren; Standard `40`, Bereich `1..1200`.

Diese Werte ändern weder Creates eigene Display-Link-Reichweite noch dessen Source-Refreshrate. CC-Aeroworks legt also keinen zweiten unsichtbaren Funkstandard über Create, weil einer bereits genug ist.

Aktuelle Telemetriewerte werden nicht in NBT geschrieben. Persistiert werden nur benutzerdefinierte Source- und Dock-Aliase; Messwerte werden nach Serverstart durch die Create-Display-Links wieder in den Runtime-Cache übertragen.

Details: [`telemetry.md`](telemetry.md) und [`docking-telemetry.md`](docking-telemetry.md).
