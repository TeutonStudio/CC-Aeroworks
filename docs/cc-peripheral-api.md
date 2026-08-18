# CC-Aeroworks Lua- und Peripheral-API

Diese Datei ist die kompakte externe Referenz zur öffentlichen CC-Aeroworks-API. Die Methodeninventare werden im Code zusätzlich in `ApiReferenceCatalog.kt` gepflegt und durch `tools/verify-api-reference.py` gegen die tatsächlichen `@LuaFunction`-Oberflächen geprüft.

Es gibt zwei grundsätzlich verschiedene Zugriffsarten:

1. Ein normaler CC:Tweaked-Computer sieht ein physisch angeschlossenes Pult als lokales `ControlDesk`-Peripheral.
2. Der eingebettete Computer eines `ComputerControlDesk` besitzt zusätzlich die globalen APIs `peripherals`, `channels`, `controls`, `wires` und `telemetry`.

Die frühere globale API `aeroworks` gehört **nicht** mehr zum öffentlichen Vertrag.

## Verfügbarkeit

| Oberfläche | Externer CC-Computer | Eingebetteter Computer | Modul |
|---|---:|---:|---|
| `ControlDesk` | ja | über normale Peripheral-Wege | - |
| `peripherals` | nein | ja | `cc_aeroworks.peripherals` |
| `channels` | nein | ja, bevorzugt | `cc_aeroworks.channels` |
| `controls` | nein | ja | `cc_aeroworks.controls` |
| `wires` | nein | ja | `cc_aeroworks.wires` |
| `telemetry` | nein | ja | `cc_aeroworks.telemetry` |
| `display` | Display-Skript | Display-Skript | `display` |
| `touchdisplay` | Display-Skript | Display-Skript | `touchdisplay` |

## Lokales `ControlDesk`

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein ControlDesk verbunden")
```

Zusätzliche Typnamen sind `control_desk`, `cc_aeroworks:control_desk` und `cc_aeroworks_control_desk`.

### Methoden

```text
getInfo()
getSocketCount()
getSockets()
getModules()
getModule(socket)
getInput(socket)
getInputs()
getDisplays()
getDisplay(socket)
setDisplayText(socket, text)
setDisplayNumber(socket, value, zeroPad?)
clearDisplay(socket)
clearDisplays()
getDisplaySize(socket)
getDisplayPixel(socket, x, y)
setDisplayPixel(socket, x, y, enabled)
setDisplayPixelBatch(socket, points, enabled?)
setDisplayPixels(socket, rows)
clearDisplayPixels(socket)
getDisplayBinding(socket)
setDisplayTouchScript(socket, path)
clearDisplayBinding(socket)
```

`setDisplayPixelBatch(socket, points, enabled?)` erwartet eine Liste aus `{x=..., y=...}` mit 1-basierten Displaykoordinaten. Alle Punkte werden in einer einzigen Kopie des gepackten Rasters angewendet; das Display wird höchstens einmal persistiert. Der Rückgabewert ist die Anzahl tatsächlich geänderter Pixel.

`left`, `right` und `big` entsprechen den nullbasierten Socket-Indizes `0`, `1` und `2`.

Die Display-Binding-Methoden sind absichtlich Teil des lokalen Adapters. Das eingebettete Desk-Handle besitzt stattdessen die netzwerkbezogenen Methoden `getPeripherals`, `find`, `findAll` und `wrap`.

## `peripherals`

```lua
local peripherals = require("cc_aeroworks.peripherals")
local network = peripherals.getNetwork()
```

Methoden:

```text
find(type)
findAll(type)
wrap(x, y, z, type?)
wrap(position, type?)
getDesks()
getTree()
getTypes()
getNetwork()
refresh()
```

`find("ControlDesk")` liefert immer eine nach Pultposition adressierte Tabelle. Andere Typen liefern bei keinem Treffer `nil`, bei genau einem Treffer direkt das Handle und bei mehreren Treffern eine Tabelle. Wer immer eine Sammlung benötigt, verwendet `findAll`.

```lua
local desks = peripherals.find("ControlDesk")
local modem = peripherals.find("endermodem")
local allModems = peripherals.findAll("endermodem")
local tree = peripherals.getTree()
```

### Metadaten physischer Peripheral-Handles

Ein physisches Handle aus `peripherals.find`, `peripherals.findAll`, `peripherals.wrap` oder einem Desk-Unterbaum delegiert die normalen Methoden direkt an das gefundene CC:Tweaked-Peripheral. Zusätzlich stellt CC-Aeroworks `getPeripheralInfo()` bereit, sofern das Ziel nicht bereits selbst eine Methode mit diesem Namen besitzt. Besitzt das Ziel eine eigene `getPeripheralInfo`-Methode, bleibt diese unverändert maßgeblich.

```lua
local modem = peripherals.find("endermodem")
if modem then
    local info = modem.getPeripheralInfo()
    print(info.address, info.type, info.side)
end
```

Die von CC-Aeroworks ergänzte Metadatentabelle enthält:

| Feld | Bedeutung |
|---|---|
| `address` | kanonische Netzwerkadresse des Peripherals |
| `type` | primärer Peripheral-Typ |
| `types` | alle erkannten Typnamen/Aliase |
| `deskId` | stabile ID des zugehörigen ControlDesk |
| `deskAddress` | kanonische `x,y,z`-Adresse des Pults |
| `deskPosition` | Tabelle mit `x`, `y`, `z` und `dimension` des Pults |
| `position` | Tabelle mit `x`, `y`, `z` und `dimension` des Peripherals |
| `side` | Pultseite, an der das Peripheral entdeckt wurde |
| `loaded` | `true`, solange das Handle zur aktuell geladenen Topologie gehört |

Der Graph lädt keine Chunks nach. Konflikte, teilweise geladene Pultreihen, zu große Netze oder falsche Eigentümerschaft werden als Lua-Fehler abgelehnt.

### Eingebettetes Desk-Handle

Ein Handle aus `peripherals.find("ControlDesk")` besitzt die üblichen Modul-, Input- und Displaymethoden einschließlich `setDisplayPixelBatch` sowie:

```text
getPeripherals()
find(type)
findAll(type)
wrap(side)
```

Es ist einem lokalen `ControlDesk` ähnlich, aber nicht identisch. Insbesondere gehören die Display-Binding-Methoden zum lokalen Adapter und nicht zu diesem Handle.

## `channels`: bevorzugte High-Level-Steuerung

Neue Cockpit- und Automatisierungsprogramme sollten nach Möglichkeit `channels` verwenden. Die API adressiert physische Controls, logische Gruppen und benutzerdefinierte Wire-Kanäle über stabile Pfade.

```lua
local channels = require("cc_aeroworks.channels")
local roll = channels.read("/groups/flight/roll_right")
channels.override("/groups/flight/roll_right", 7)
channels.setWire("/groups/flight/gear", 15)
```

Methoden:

```text
ls(path?)
stat(pathOrId)
read(pathOrId)
setWire(pathOrId, value)
pulseWire(pathOrId, ticks?, value?)
resetWire(pathOrId)
override(pathOrId, value)
overrideBatch(commands)
release(pathOrId)
releaseAll()
```

Die logische Richtungssicht verwendet Redstone-Stärken `0..15`. Details zu Gruppen, Aliasen und Drive By Wire stehen in `wire-channels.md`.

## `controls`: native Aeroworks-Autorität

`controls` ist die Low-Level-Schnittstelle für Programme, die bewusst mit nativen signierten Aeroworks-Kanälen arbeiten.

```text
getChannels()
getState(deskId, socket, channel)
override(deskId, socket, channel, value)
overrideBatch(commands)
release(deskId, socket, channel)
releaseAll()
```

Werte liegen im Bereich `-15..15`. Overrides sind Laufzeitzustand und werden bei ungültigem Netzwerk oder Computer-Aus fail-safe freigegeben. Siehe `control-overrides.md`.

## `wires`: direkte Wire-Ausgänge

```text
list()
exists(name)
get(name)
set(name, value)
pulse(name, ticks?, value?)
reset(name)
resetAll()
getInfo(name)
getBackend()
isEnabled()
```

`wires` verwaltet Runtime-Werte `0..15`. Das Anlegen, Löschen und Umbenennen von Kanälen ist Konfiguration und absichtlich nicht Teil dieser öffentlichen Runtime-API.

## `telemetry`

Create-Display-Links können strukturierte Informationsquellen am eingebetteten Computer bereitstellen.

```text
list()
get(nameOrId)
find(type)
rename(nameOrId, alias)
clearName(nameOrId)
getStatus()
getDocks()
getDock(nameOrId)
renameDock(nameOrId, alias)
clearDockName(nameOrId)
```

```lua
local fuel = telemetry.get("fuel")
if fuel then print(fuel.value.percent) end
```

Mit Create: Simulated liefert `getDock` ein Dock-Handle mit `getInfo`, `listTelemetry`, `getTelemetry`, `renameTelemetry`, `clearTelemetryName` und `getTransferBuffers`. Ohne Simulated bleibt die lokale Telemetrie vollständig funktionsfähig. Siehe `telemetry.md` und `docking-telemetry.md`.

## Display-Skripte

Das Modul `display` ist eventbasiert:

```text
resolve(event)
getSize(event)
clear(event)
getPixel(event, x, y)
setPixel(event, x, y, enabled)
setPixelBatch(event, points, enabled?)
setPixels(event, rows)
setText(event, text)
setNumber(event, value, zeroPad?)
```

`setPixelBatch` ist der bevorzugte Weg für mehrere punktuelle Rasteränderungen. Im Gegensatz zu `setPixels` muss das vollständige Raster nicht durch Lua übertragen werden.

`touchdisplay` erbt diese Methoden und ergänzt:

```text
isTap(event)
isDraw(event)
position(event)
drawStart(event)
drawDelta(event)
drawDirection(event)
drawSpeed(event)
drawSamples(event)
drawStroke(event)
drawEnded(event)
drawIdentity(event)
normalizedPosition(event)
```

`drawDelta(event)` bleibt die serverseitig aufgelöste Pixelverschiebung zwischen den Endpunkten zweier akzeptierter Draw-Events. `drawDirection(event)` liefert den normierten lokalen Bewegungsvektor des virtuellen Fingers in Display-U/V. `drawSpeed(event)` liefert die geglättete Geschwindigkeit in normierten Displayeinheiten **pro Sekunde**.

`drawSamples(event)` liefert den serverseitig aufgelösten Sub-Tick-Pfad des aktuellen Events. Ein normales Tick-Paket enthält bis zu 16 hochfrequente Client-Samples; bei nicht-ersten Events steht der vorherige akzeptierte Endpunkt als erstes Sample davor. Falls diese Tabelle fehlt, leer ist oder bei einem bewegten Folgeevent nur den aktuellen Punkt enthält, rekonstruiert der Helper mindestens `previous -> current` aus `deltaX/deltaY`.

`drawStroke(event)` ist die bevorzugte Freihand-Zeichenfunktion. Sie interpoliert benachbarte Samples als kubische Hermite-Kurven, rasterisiert die Kurve kontinuierlich, entfernt doppelte Pixel und übergibt die resultierenden Punkte anschließend an einen einzigen nativen `setDisplayPixelBatch(...)`-Aufruf.

Beispiel:

```lua
local touch = require("touchdisplay")

return {
    onDraw = function(event)
        local _, sequence = touch.drawIdentity(event)
        if sequence == 0 then touch.clear(event) end
        touch.drawStroke(event)
    end
}
```

`isDoubleTap` und `isHold` bleiben nur für Legacy-Ereignisproduzenten erhalten. Die aktuelle kombinierte Display-Eingabe erzeugt `tap` und `draw`. Details stehen in `display-touch.md`.

## Optionale Create: Radars-Erweiterung

Die Radar-API wird durch `cc_aeroworks_radarcompat` nur aktiviert, wenn `create_radar` geladen ist. Dann erweitert der lokale `ControlDesk` seinen Vertrag um:

```text
getRadarSources()
setRadarSource(socket, sourceId)
```

Ohne Create: Radars gehören diese Methoden und die zugehörigen Handbuchseiten nicht zum verfügbaren Funktionsumfang.

## Ereignisse

Wichtige Ereignisfamilien sind:

```text
cc_aeroworks_desk_input
cc_aeroworks_desk_touch
cc_aeroworks_desk_display_input
cc_aeroworks_console_input
cc_aeroworks_console_touch
cc_aeroworks_console_display_input
cc_aeroworks_console_changed
cc_aeroworks_control_override
cc_aeroworks_control_release
cc_aeroworks_peripheral_attached
cc_aeroworks_peripheral_detached
cc_aeroworks_telemetry_added
cc_aeroworks_telemetry_changed
cc_aeroworks_telemetry_removed
cc_aeroworks_dock_changed
cc_aeroworks_remote_telemetry_changed
```

Für die beiden `*_display_input`-Ereignisse bleiben alle bisherigen Argumentpositionen bis einschließlich `isEnd` unverändert. Danach folgen `directionU`, `directionV`, `speed` und als letztes Argument die serverseitig aufgelöste `samples`-Tabelle. Die fachlichen Details stehen in `display-touch.md`.
