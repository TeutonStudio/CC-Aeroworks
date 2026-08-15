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

Die Displayauflösung wird nicht mehr über getrennte Breiten und Höhen konfiguriert. Stattdessen gibt es eine gemeinsame Dichte in **PPB (Parts per Block)**:

- `display.ppb`: logische Teile beziehungsweise Pixelzellen pro voller Blockkante; Standard `256`, Minimum `16`.
- `16 PPB` entspricht der üblichen Minecraft-Texturdichte von `16×16` Pixeln auf einer Blockseite.

Breite und Höhe werden aus der realen, blockrelativen Modulfläche berechnet. Ein Rasterpixel besitzt auf beiden Achsen denselben Pitch von `1 / PPB` Block und bleibt dadurch quadratisch.

- Kleines Display / kleines Radar: Fläche `7/16 × 7/16 Block`. Bei `16 PPB` ergibt das `7×7`, bei Standard `256 PPB` `112×112` Pixel.
- Großes Display / großes Radar: Fläche `10/16 × 7/16 Block`. Bei `16 PPB` ergibt das `10×7`, bei Standard `256 PPB` `160×112` Pixel.

Intern gilt je Achse `floor(Modulgröße_in_16tel × PPB / 16)`. Falls ein PPB-Wert kein Vielfaches von 16 ist, bleibt dadurch höchstens ein Bruchteil einer Pixelzelle als symmetrischer Rand übrig; das Raster wird nicht gestreckt. Die sichtbaren Pixelmodelle werden zusätzlich relativ zur Vanilla-Dichte mit `16 / PPB` skaliert, damit auch bei hoher Dichte keine fest großen Pixelmodelle übereinanderliegen. Das gilt sowohl für den klassischen Renderpfad als auch für Flywheel.

Die früheren Schlüssel `display.small.width`, `display.small.height`, `display.large.width` und `display.large.height` werden nicht mehr verwendet. Bestehende Serverkonfigurationen erhalten beim Wechsel auf diese Version den neuen PPB-Standardwert, sofern `display.ppb` nicht ausdrücklich gesetzt wird.

Die aktuell wirksame Auflösung ist in Lua über `getDisplaySize(socket)` sowie in Displaybeschreibungen über `pixelWidth`, `pixelHeight`, `PIXEL_WIDTH` und `PIXEL_HEIGHT` verfügbar. Kotlin-Code kann dieselben Werte über `DeskDisplayType.pixelWidth`, `DeskDisplayType.pixelHeight` und `DeskDisplayType.pixelResolution` abrufen; `DeskDisplayType.partsPerBlock` liefert zusätzlich die zugrunde liegende Dichte.

Der Pixel-Editor im API-Handbuch übernimmt diese synchronisierten Werte beim Erzeugen seines Editorzustands. Nach einer geänderten Serverkonfiguration sollte das Handbuch deshalb neu geöffnet werden, damit ein bereits offener Editor nicht mit seinem alten Raster weiterarbeitet.

Eine PPB-Änderung verändert den Rastervertrag. Bereits gespeicherte Pixelraster mit abweichender Länge werden nicht gestreckt oder zugeschnitten und müssen anschließend neu geschrieben werden. Textzustände bleiben davon unberührt.

Der Speicherbedarf, die Lua-Datenmenge und der Renderaufwand steigen weiterhin mit `Breite × Höhe`. `256 PPB` bedeutet beim großen Display bereits `17.920` logische Pixel. Ein absurd hoher PPB-Wert bleibt also technisch möglich, aber die JVM wird diese philosophische Entscheidung nicht zwingend würdigen.

## Server und Create-Telemetrie

Unter `telemetry` steuert die Serverkonfiguration den Runtime-Speicher und die Lifecycle-Prüfungen der Create-Display-Link-Telemetrie:

- `telemetry.maxSourcesPerEndpoint`: maximale Zahl gespeicherter Display-Link-Quellen pro ComputerControlDesk oder Docking Connector; Standard `128`, Bereich `1..4096`.
- `telemetry.maxListEntries`: maximale Zahl zurückgegebener Einträge einer Item-/Fluidliste; Standard `128`, Bereich `1..4096`.
- `telemetry.staleAfterTicks`: Alter ohne Refresh, ab dem eine Source `stale=true` meldet; Standard `220`, Bereich `1..72000`.
- `telemetry.validationIntervalTicks`: Intervall für die Prüfung, ob bekannte Display Links noch existieren, dieselbe Source verwenden und auf denselben Endpoint zeigen; Standard `20`, Bereich `1..1200`.
- `telemetry.dockScanIntervalTicks`: Intervall für den Sable-sublevelweiten Scan nach optionalen Simulated-Docking-Connectoren; Standard `40`, Bereich `1..1200`.

Diese Werte ändern weder Creates eigene Display-Link-Reichweite noch dessen Source-Refreshrate. CC-Aeroworks legt also keinen zweiten unsichtbaren Funkstandard über Create, weil einer bereits genug ist.

Aktuelle Telemetriewerte werden nicht in NBT geschrieben. Persistiert werden nur benutzerdefinierte Source- und Dock-Aliase; Messwerte werden nach Serverstart durch die Create-Display-Links wieder in den Runtime-Cache übertragen.

Details: [`telemetry.md`](telemetry.md) und [`docking-telemetry.md`](docking-telemetry.md).
