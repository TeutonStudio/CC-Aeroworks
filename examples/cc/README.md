# CC:Tweaked-Beispiele

Dieser Ordner ist der kanonische Einstiegspunkt für ausführbare CC-Aeroworks-Lua-Beispiele. Der Root-README verlinkt hierher, enthält aber bewusst keinen kopierten Lua-Code. So existiert für ein Beispiel nur eine ausführbare Wahrheit, was für Dokumentation bereits erstaunlich viel Zivilisation ist.

Die Dateien sind in drei Gruppen geteilt:

- **API-Einstiege:** kurz, überwiegend read-only und zum Kopieren gedacht;
- **interaktive Beispiele:** demonstrieren gezielt Display-, Touch- oder Override-Funktionalität;
- **Diagnoseprogramme:** umfangreicher, topologie- und lifecycle-bewusst und eher zum Prüfen als zum Abschreiben gedacht.

## Schnellübersicht

| Datei | Läuft auf | Verändert Zustand | Zweck |
|---|---|---:|---|
| [`local-desk.lua`](local-desk.lua) | normal / Wired | nein | lokales `ControlDesk` inspizieren |
| [`network-basics.lua`](network-basics.lua) | eingebettet | nein | Pultnetz über `peripherals` lesen |
| [`channels-demo.lua`](channels-demo.lua) | eingebettet | nein | logische `channels` untersuchen |
| [`wires-demo.lua`](wires-demo.lua) | eingebettet | nein | konfigurierte Wire-Kanäle lesen |
| [`telemetry-read.lua`](telemetry-read.lua) | eingebettet | nein | strukturierte Telemetrie lesen |
| [`control-override-demo.lua`](control-override-demo.lua) | eingebettet | **ja, kurzzeitig** | nativen Control-Kanal überschreiben und freigeben |
| [`dashboard.lua`](dashboard.lua) | beide | **ja, Display** | numerischen Input auf Display spiegeln |
| [`pixel-test.lua`](pixel-test.lua) | beide | **ja, Display** | dynamische Pixelauflösung testen |
| [`touch-test.lua`](touch-test.lua) | Display-Handler | **ja, Display** | Tap-/Draw-Eingabe testen |
| [`input-monitor.lua`](input-monitor.lua) | beide | nein | Eingänge live diagnostizieren |
| [`embedded-console.lua`](embedded-console.lua) | eingebettet | nein | einzelnes Pult detailliert inspizieren |
| [`multiblock-dashboard.lua`](multiblock-dashboard.lua) | beide | nein | alle erreichbaren Pulte live überblicken |
| [`telemetry-dashboard.lua`](telemetry-dashboard.lua) | eingebettet | nein | lokale und angedockte Telemetrie inspizieren |

**beide** bedeutet: eingebetteter ComputerControlDesk sowie normaler beziehungsweise über Wired Modems verbundener CC:Tweaked-Computer, sofern das jeweilige Skript die beiden Zugriffswege selbst erkennt.

## API-Einstiege

### `local-desk.lua`

Minimaler Einstieg für einen normalen oder verkabelten CC:Tweaked-Computer. Das Skript findet ein erreichbares `ControlDesk`-Peripheral und zeigt Desk-Metadaten, installierte Module und Displays. Es schreibt nichts.

Verwendete Oberfläche: lokales `ControlDesk`.

### `network-basics.lua`

Minimaler Einstieg für den eingebetteten ComputerControlDesk. Das Skript lädt `cc_aeroworks.peripherals`, zeigt den Netzwerkstatus und listet alle Desk-Handles nach Adresse mit Modul- und Displayzahl.

Verwendete Oberfläche: `cc_aeroworks.peripherals`.

### `channels-demo.lua`

Read-only-Einstieg in die bevorzugte High-Level-Steuerungsoberfläche. Ohne Argument zeigt das Skript die Einträge unter `/`; mit einem Pfad zeigt es `stat` und den aktuellen `read`-Wert.

Beispielaufruf im CraftOS-Terminal: `channels-demo.lua /groups/flight/roll`.

Verwendete Oberfläche: `cc_aeroworks.channels`.

### `wires-demo.lua`

Zeigt Backend, Aktivierungsstatus und alle konfigurierten benutzerdefinierten Drive-By-Wire-/Redstone-Kanäle mit aktuellem Wert. Das Beispiel verändert bewusst keinen Ausgang.

Verwendete Oberfläche: `cc_aeroworks.wires`.

### `telemetry-read.lua`

Listet verfügbare Create-Informationsquellen mit Alias, Typ und Stale-Zustand. Mit einem Alias oder einer ID als Argument wird die vollständige strukturierte Quelle ausgegeben.

Verwendete Oberfläche: `cc_aeroworks.telemetry`.

## Interaktive Beispiele

### `control-override-demo.lua`

Listet alle nativen Aeroworks-Control-Kanäle aus `cc_aeroworks.controls`, lässt einen Kanal auswählen und setzt für zwei Sekunden einen Wert im Bereich `-15..15`. Anschließend wird **nur der ausgewählte Override** wieder freigegeben, auch wenn der eigentliche Test fehlschlägt oder beendet wird.

Das Beispiel ist absichtlich nicht mehr an einen bestimmten Yoke, Modulnamen oder Socket gebunden. Für normale Cockpitautomatisierung sollte weiterhin bevorzugt `channels` verwendet werden; `controls` ist die Low-Level-Sicht für native signierte Aeroworks-Werte.

### `dashboard.lua`

Spiegelt einen frei gewählten numerischen Pulteingang auf ein frei gewähltes CC-Aeroworks-Display und unterstützt beide Computer-Betriebsarten automatisch.

Das Skript entdeckt numerische Einzel- und Mehrkanal-Eingänge sowie Displays. Bei genau einem Kandidaten wird automatisch ausgewählt; bei mehreren Kandidaten erscheint eine paginierte Auswahl. Ausgewählte Endpunkte werden über stabile Desk-, Modul-, Display- und Kanalidentität verfolgt.

Während des Betriebs werden die passenden CC-Aeroworks-Ereignisse verwendet. Zusätzlich validiert das Skript die ausgewählten Endpunkte regelmäßig. Beim normalen Beenden und nach abgefangenen Laufzeitfehlern versucht es, den vorherigen Displayzustand wiederherzustellen.

`dashboard.lua` gibt bewusst den gelesenen Rohwert weiter. Für bipolare Aeroworks-Achsen können Werte wie `-15..15` auftreten; eine gewünschte Skalierung oder Invertierung gehört explizit in das eigene Programm.

### `pixel-test.lua`

Sucht alle erreichbaren CC-Aeroworks-Displays. Auf dem eingebetteten Computer wird das vollständige Pultnetz durchsucht, auf normalen CC:Tweaked-Computern alle erreichbaren `ControlDesk`-Peripherals.

Das Testmuster wird aus `getDisplaySize(...)` erzeugt und ist damit nicht auf eine feste PPB-Konfiguration oder Pixelauflösung verdrahtet. Nach kurzer Anzeige wird das Raster wieder gelöscht.

### `touch-test.lua`

Kanonischer Handler für interaktive große Displays. Das Skript verwendet `touchdisplay`, reagiert auf `tap` und geordnete `draw`-Gesten und demonstriert unter anderem `drawStart`, `drawDelta`, `drawIdentity`, `drawEnded` und normalisierte Koordinaten.

Es wird als Display-Touch-/Input-Skript gebunden und nicht als normales Terminalprogramm gestartet. Details zum aktuellen kombinierten Eingabemodus stehen in [`../../docs/display-touch.md`](../../docs/display-touch.md).

## Diagnoseprogramme

Die folgenden Programme sind absichtlich umfangreicher. Sie testen Discovery, Topologieänderungen und Fehlerzustände und sollen nicht als minimaler API-Stil missverstanden werden.

### `input-monitor.lua`

Zeigt alle numerischen Pulteingänge live im Terminal. Das Skript unterstützt sowohl das eingebettete Pultnetz als auch normale beziehungsweise über Wired Modems verbundene `ControlDesk`-Peripherals.

Eingabeereignisse aktualisieren die Anzeige sofort; Attach/Detach- und Multiblockänderungen lösen neue Topologieprüfungen aus. Eine periodische Prüfung korrigiert auch Änderungen ohne passendes Ereignis. Große Listen sind scrollbar, `r` aktualisiert und `q` beziehungsweise `Ctrl+T` beendet.

### `embedded-console.lua`

Read-only Inspector für den eingebetteten ComputerControlDesk. Das Skript verlangt die `peripherals`-API, prüft den Netzwerkzustand und zeigt pro Pult unter anderem stabile ID, Variante, Module, rohe Eingabewerte, Displays und angrenzende CC:Tweaked-Peripherals.

Der Inspector ruft keine `setDisplay...`- oder `clearDisplay...`-Methode auf.

### `multiblock-dashboard.lua`

Read-only Live-Übersicht aller erreichbaren Pulte. Im eingebetteten Modus verwendet sie `peripherals`; im normalen/Wired-Modus durchsucht sie die normale CC:Tweaked-Peripheral-Infrastruktur.

Pro Desk zeigt sie Position, Variante/Computerstatus sowie die Anzahl von Modulen, numerischen Eingabekanälen, Displays und soweit verfügbar angrenzenden Peripherals. Die Tabelle reagiert auf Netzwerkänderungen und wird zusätzlich periodisch aktualisiert.

### `telemetry-dashboard.lua`

Read-only Inspector für `cc_aeroworks.telemetry`. Er zeigt lokale Quellen, frische und veraltete Werte sowie mit optionalem Create: Simulated die Docking-Connectoren und Telemetrie verriegelter Remote-Module.

Das Dashboard reagiert auf die `cc_aeroworks_telemetry_*`, `cc_aeroworks_dock_changed` und `cc_aeroworks_remote_telemetry_changed` Ereignisse und aktualisiert zusätzlich periodisch. Es verändert weder Aliase noch Displays.

## Gemeinsame Regeln

Für neue oder überarbeitete Beispiele gelten folgende Verträge:

- keine feste Desk-Position oder Discovery-Reihenfolge als Identität verwenden;
- Displayauflösung und PPB nie fest verdrahten, sondern über die API abfragen;
- aktuelle `cc_aeroworks.*`-Module verwenden, wenn die Oberfläche als Modul veröffentlicht ist;
- die entfernte globale `aeroworks`-API und alte netzwerkweite `getDesk...`-Fassaden nicht mehr lehren;
- schreibende Beispiele im Dateikopf deutlich kennzeichnen und temporären Zustand gezielt bereinigen;
- `channels` für normale High-Level-Automatisierung gegenüber nativen `controls` bevorzugen;
- Diagnoseprogramme dürfen robust und umfangreich sein, Quickstarts sollen dagegen den relevanten API-Vertrag schnell sichtbar machen.

`tools/verify-examples.py` prüft, dass jede ausgelieferte `.lua`-Datei hier verlinkt ist, der Root-README keinen kopierten Lua-Code mehr enthält, Links auf existierende Dateien zeigen und entfernte beziehungsweise veraltete Beispielpfade nicht zurückkehren.
