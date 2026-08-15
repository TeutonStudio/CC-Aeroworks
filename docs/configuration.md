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

- `display.small.width`: exakte Pixelbreite des kleinen Pultdisplays und der kleinen Radaranzeige; Standard `7`, positive Ganzzahl.
- `display.small.height`: exakte Pixelhöhe des kleinen Pultdisplays und der kleinen Radaranzeige; Standard `5`, positive Ganzzahl.
- `display.large.width`: exakte Pixelbreite des großen Pultdisplays und der großen Radaranzeige; Standard `11`, positive Ganzzahl.
- `display.large.height`: exakte Pixelhöhe des großen Pultdisplays und der großen Radaranzeige; Standard `5`, positive Ganzzahl.

Die früheren Obergrenzen von 64 Pixeln Breite und 32 Pixeln Höhe wurden entfernt. Konfigurationsseitig gilt nur noch der Bereich positiver vorzeichenbehafteter Ganzzahlen. Die physisch sinnvolle Größe bleibt selbstverständlich endlich, auch wenn `Int.MAX_VALUE` sich davon emotional nicht beeindrucken lässt.

### Raw Display API

Die direkte Raw-Pixel-API verwendet weiterhin ein vollständiges Raster und ist deshalb für kleine beziehungsweise selten aktualisierte Anzeigen gedacht. Ein Raw-Raster muss technisch in einen Java-String beziehungsweise ein Java-Array passen; Lua-Datenmenge und Verarbeitungsaufwand steigen mit `Breite × Höhe`.

Eine Auflösungsänderung verändert den Raw-Rastervertrag. Bereits gespeicherte Pixelraster mit abweichender Länge werden nicht gestreckt oder zugeschnitten und müssen anschließend neu geschrieben werden. Textzustände bleiben davon unberührt.

### Reactive Display UI

Das große Display besitzt zusätzlich einen transienten Reactive-UI-Framebuffer. Dieser wird nicht als vollständiger `0`/`1`-String gespeichert, sondern als sparse `64x64`-Bit-Tiles:

- leere Tiles benötigen keinen Frame-Inhalt,
- Draw-Invalidierungen rasterisieren nur betroffene Bounds neu,
- ein Commit vergleicht geänderte Tile-Bits mit dem vorherigen Snapshot,
- visuell identische Tiles erzeugen keinen Netzwerkpatch,
- Netzwerkpatches werden auf höchstens 256 Tiles pro Payload geteilt,
- Runtime-Frames werden nicht bei jeder Änderung in Welt-NBT persistiert.

Damit bestimmt eine große konfigurierte Koordinatenfläche nicht automatisch die Kosten jedes UI-Updates. Eine vollständig gefüllte riesige Anzeige bleibt allerdings eine vollständig gefüllte riesige Anzeige. Die Architektur beseitigt unnötige Arbeit, keine Mathematik.

Die aktuell wirksame Auflösung ist in Lua über `getDisplaySize(socket)` sowie in Displaybeschreibungen über `pixelWidth`, `pixelHeight`, `PIXEL_WIDTH` und `PIXEL_HEIGHT` verfügbar. Reactive UI erhält dieselben Maße über `ui.listDisplays()` beziehungsweise den gemounteten Display-Runtime-Descriptor. Kotlin-Code kann dieselben Werte über `DeskDisplayType.pixelWidth`, `DeskDisplayType.pixelHeight` und `DeskDisplayType.pixelResolution` abrufen.

Der Pixel-Editor im Handbuch übernimmt die synchronisierten Werte beim Erzeugen seines Editorzustands. Nach einer geänderten Serverkonfiguration sollte das Handbuch deshalb neu geöffnet werden, damit ein bereits offener Editor nicht mit seinem alten Raster weiterarbeitet.

Details zur reaktiven Architektur: [`reactive-display-ui.md`](reactive-display-ui.md).

## Server und Create-Telemetrie

Unter `telemetry` steuert die Serverkonfiguration den Runtime-Speicher und die Lifecycle-Prüfungen der Create-Display-Link-Telemetrie:

- `telemetry.maxSourcesPerEndpoint`: maximale Zahl gespeicherter Display-Link-Quellen pro ComputerControlDesk oder Docking Connector; Standard `128`, Bereich `1..4096`.
- `telemetry.maxListEntries`: maximale Zahl zurückgegebener Einträge einer Item-/Fluidliste pro Source; Standard `128`, Bereich `1..4096`. `entryCount` und Gesamtmengen bleiben vollständig und `truncated=true` markiert die Kürzung.
- `telemetry.staleAfterTicks`: Alter ohne Refresh, ab dem eine Source `stale=true` meldet; Standard `220`, Bereich `1..72000`.
- `telemetry.validationIntervalTicks`: Intervall für die Prüfung, ob bekannte Display Links noch existieren, dieselbe Source verwenden und auf denselben Endpoint zeigen; Standard `20`, Bereich `1..1200`.
- `telemetry.dockScanIntervalTicks`: Intervall für den Sable-sublevelweiten Scan nach optionalen Simulated-Docking-Connectoren; Standard `40`, Bereich `1..1200`.

Diese Werte ändern weder Creates eigene Display-Link-Reichweite noch dessen Source-Refreshrate. CC-Aeroworks legt also keinen zweiten unsichtbaren Funkstandard über Create, weil einer bereits genug ist.

Aktuelle Telemetriewerte werden nicht in NBT geschrieben. Persistiert werden nur benutzerdefinierte Source- und Dock-Aliase; Messwerte werden nach Serverstart durch die Create-Display-Links wieder in den Runtime-Cache übertragen.

Details: [`telemetry.md`](telemetry.md) und [`docking-telemetry.md`](docking-telemetry.md).
