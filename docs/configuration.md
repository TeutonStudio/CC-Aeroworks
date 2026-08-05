# Configuration

CC-Aeroworks verwendet getrennte Client- und Serverkonfigurationen.

## Client

Die Clientdatei ist `config/cc_aeroworks-client.toml`.

- `combinedLeverSensitivity`: Änderung des Gleitkommaakkumulators pro vertikalem Mausdelta; Standard `0.15`.
- `combinedLeverInvertY`: kehrt die vertikale Richtung um; Standard `false`.
- `combinedLeverPacketRate`: maximale Pakete pro Sekunde; Standard `20`, Bereich 1 bis 20.
- `freezeCameraOnlyWithValidTarget`: Sicherheitsoption, Standard `true`. Unabhängig vom Wert friert die Implementierung ohne gültigen Lever nie die Kamera ein; `false` erweitert aus Sicherheitsgründen nicht den Zielbereich.

Die Aktivierungstaste wird pro Achse direkt im Aeroworks-Modulbildschirm gespeichert. Nach Auswahl des Input Types `Kombiniert` zeigt das mittlere Eingabefeld nicht mehr die Mausachse, sondern die Aktivierungstaste. Linksklick startet die Tastenerfassung, Rechtsklick löscht sie. Beim erstmaligen Umschalten auf `Kombiniert` wird eine leere Belegung mit `K` vorbelegt.

Ein Linksklick auf das vorhandene Modussymbol schaltet bei Lever, Joystick und jeder Achse des Throttle Quadrant zyklisch `Buttons -> Analog -> Kombiniert -> Buttons`. Beim Throttle Quadrant besitzen `red`, `amber`, `green` und `blue` jeweils eine eigene Aktivierungstaste. Der Joystick besitzt Bindings für `x` und `y`: links/rechts verwendet Maus X, vor/zurück Maus Y. Sind beide Joystick-Achsen auf dieselbe Taste gelegt, werden X und Y bei gehaltener Taste parallel gesteuert. Gleiches gilt für mehrere Quadrant-Kanäle mit identischer Taste. Intern wird die freie Aeroworks-Input-Source `cc_aeroworks.combined` gespeichert; es wird kein fremder Enumwert ergänzt.

Maus Y ist für `Kombiniert` fest vorgegeben. Die Aktivierungstaste stammt aus der vorhandenen, von Aeroworks persistent gespeicherten negativen Tastenbindung der jeweiligen Achse. Eine globale Minecraft-Keybinding-Zuordnung ist dafür nicht mehr erforderlich.

## Server und Displayauflösung

Die Serverdatei heißt `cc_aeroworks-server.toml`. Sie liegt weltbezogen unter `<welt>/serverconfig/cc_aeroworks-server.toml` und wird an verbundene Clients synchronisiert.

- `display.small.width`: exakte Pixelbreite des zweistelligen Displays; Standard `7`, Bereich 1 bis 64.
- `display.small.height`: exakte Pixelhöhe des zweistelligen Displays; Standard `5`, Bereich 1 bis 32.
- `display.large.width`: exakte Pixelbreite des dreistelligen Displays; Standard `11`, Bereich 1 bis 64.
- `display.large.height`: exakte Pixelhöhe des dreistelligen Displays; Standard `5`, Bereich 1 bis 32.

Die aktuell wirksame Auflösung ist in Lua über `getDisplaySize(socket)` sowie in Displaybeschreibungen über `pixelWidth`, `pixelHeight`, `PIXEL_WIDTH` und `PIXEL_HEIGHT` verfügbar. Kotlin-Code kann dieselben Werte über `DeskDisplayType.pixelWidth`, `DeskDisplayType.pixelHeight` und `DeskDisplayType.pixelResolution` abrufen.

Eine Auflösungsänderung verändert den Rastervertrag. Bereits gespeicherte Pixelraster mit abweichender Länge werden nicht gestreckt oder zugeschnitten und müssen anschließend neu geschrieben werden. Textzustände bleiben davon unberührt.
