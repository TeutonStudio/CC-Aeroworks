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

- `display.small.width`: exakte Pixelbreite des zweistelligen und kleinen Radar-Displays; Standard `7`, positive Ganzzahl.
- `display.small.height`: exakte Pixelhöhe des zweistelligen und kleinen Radar-Displays; Standard `5`, positive Ganzzahl.
- `display.large.width`: exakte Pixelbreite des dreistelligen und großen Radar-Displays; Standard `11`, positive Ganzzahl.
- `display.large.height`: exakte Pixelhöhe des dreistelligen und großen Radar-Displays; Standard `5`, positive Ganzzahl.

Die früheren Obergrenzen von 64 Pixeln Breite und 32 Pixeln Höhe wurden entfernt. Konfigurationsseitig gilt nur noch der Bereich positiver vorzeichenbehafteter Ganzzahlen. Ein einzelnes Raster muss technisch weiterhin in einen Java-String beziehungsweise ein Java-Array passen; außerdem steigen Speicherbedarf, Lua-Datenmenge und Renderaufwand mit `Breite × Höhe`. Die Mod skaliert den Pixelabstand bei großen Rastern herunter, damit die Anzeige auf dem physischen Modul bleibt. Das schützt jedoch niemanden vor einer absurden Milliardenpixel-Konfiguration. Manche Naturgesetze werden vom Serverbetreiber verwaltet.

Die aktuell wirksame Auflösung ist in Lua über `getDisplaySize(socket)` sowie in Displaybeschreibungen über `pixelWidth`, `pixelHeight`, `PIXEL_WIDTH` und `PIXEL_HEIGHT` verfügbar. Kotlin-Code kann dieselben Werte über `DeskDisplayType.pixelWidth`, `DeskDisplayType.pixelHeight` und `DeskDisplayType.pixelResolution` abrufen.

Eine Auflösungsänderung verändert den Rastervertrag. Bereits gespeicherte Pixelraster mit abweichender Länge werden nicht gestreckt oder zugeschnitten und müssen anschließend neu geschrieben werden. Textzustände bleiben davon unberührt.
