# Lua-API

[[Deutsch|API-Schnellreferenz]] · [[English|API-en]]

![Ponder-Szene zur Ausgabe auf einem montierten Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-display_programming.png)


Diese Seite erklärt die Programmierung. Die vier Hardwarevarianten und ihre Rezeptgrafiken stehen unter [[Die vier Displays|Programmierbare-Displays]].

## Zugriffswege

| Eingebetteter Computer | Externer Computer |
|---|---|
| globale `peripherals`-, `channels`-, `controls`-, `wires`- und `telemetry`-APIs | lokales Peripheral `ControlDesk` |
| sieht alle Pulte, Nachbargeräte, lokale Telemetrie und darf Steuerautorität übernehmen | sieht nur das direkt verbundene Pult |
| kein Modem erforderlich | direkt oder über Wired Modem |
| `cc_aeroworks_*`-Ereignisse | `cc_aeroworks_desk_input` |

Die alte globale `aeroworks`-API und die netzwerkweiten `getDesk...`-Methoden sind nicht Teil des neuen Vertrags.

## Dashboard Controller (optionales Create: Train Functionality 0.1.47)

```lua
local zug = peripheral.find("DashboardController")
zug.setThrottle(0.75) -- -1 rückwärts, +1 vorwärts
zug.setSteer(-1)      -- -1 links, +1 rechts
zug.setBrake(false)
zug.setStation(true)
local zustand = zug.getState()
local info = zug.getInfo()
```

`setThrottle` und `setSteer` akzeptieren ausschließlich endliche Werte von `-1` bis `1`. Im Redstone-Link-Frequenzmodus sind die Setter nicht verfügbar. Die Werte werden beim Trennen des letzten Computers, Entladen des Controllers oder Serverneustart neutralisiert.

## Lokales `ControlDesk`

```lua
local desk = peripheral.find("ControlDesk")
```

Zusätzliche Typnamen:

```text
control_desk
cc_aeroworks:control_desk
cc_aeroworks_control_desk
```

Gemeinsame lokale Modul-/Displaymethoden (auch am eingebetteten Desk-Handle):

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
setDisplayResolution(socket, ppb)
getDisplayPixel(socket, x, y)
setDisplayPixel(socket, x, y, enabled)
setDisplayPixelBatch(socket, points, enabled?)
setDisplayPixels(socket, rows)
clearDisplayPixels(socket)
getDisplayColor(socket)
setDisplayColor(socket, color)
```

## Zusätzliche Methoden des lokalen Peripherals

Diese Bindungsmethoden stehen am lokalen `ControlDesk`-Peripheral bereit, nicht am eingebetteten Desk-Handle:

```text
getDisplayBinding(socket)
setDisplayTouchScript(socket, path)
clearDisplayBinding(socket)
setControlScript(socket, path)
```

Mit Create: Radars kommen `getRadarSources()` und `setRadarSource(socket, id)` hinzu. Mit eingesetztem CC:-Terminals-Gerät kommen der Typ `terminal` und die üblichen Terminalmethoden wie `write`, `getSize`, `setCursorPos`, `blit`, Farb- und Palettenzugriffe hinzu. `getInfo().device` beschreibt das Slot-Gerät, `programmable` dessen Computerrolle. Details stehen unter [[Bedienterminals]].

## Globale `peripherals`-API

```lua
local peripherals = require("cc_aeroworks.peripherals")
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

## `ContraptionControl`

Typaliase: `contraption_control`, `create:contraption_controls`, `cc_aeroworks:contraption_control`.

```text
getInfo()
getState()
getFilter()
isEnabled()
setEnabled(boolean)
toggle()
getFloors()
getSelectedFloor()
selectFloor(indexOrName)
```

`setEnabled`/`toggle` gelten für `actors` und `doors`; die drei Floor-Methoden ausschließlich für `elevator`. Unpassende Methoden erzeugen einen Lua-Fehler. Das Ereignis `cc_aeroworks_contraption_control(address, id, mode, state, cause)` deckt manuelle, Redstone-, Lua- und Aufzugänderungen ab.

Mobil liefert `telemetry.get("contraption")` die Entity-Position, Rotation, Geschwindigkeit und Mounted-Storage-Belegung. Weltgebundene Quellen sind dort ausdrücklich `unavailable`; Sable behält dagegen die vollständige Sublevel-Semantik.

## Rückgabe von `find`

| Trefferzahl | Ergebnis |
|---:|---|
| 0 | `nil` |
| 1 | direktes Methoden-Handle |
| 2 oder mehr | nach Pultposition und Seite adressierte Tabelle |

```lua
local modem = peripherals.find("endermodem")
```

`findAll(type)` liefert immer eine Tabelle.

```lua
for address, modem in pairs(peripherals.findAll("endermodem")) do
  print(address)
end
```

`ControlDesk` ist eine Ausnahme und liefert immer alle Pulte als Tabelle:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
```

## Typnormalisierung

Ein gemeldeter Typ wie

```text
advanced_peripherals:ender_modem
```

kann über folgende Namen gesucht werden:

```text
advanced_peripherals:ender_modem
ender_modem
EnderModem
endermodem
```

Primärtyp und zusätzliche CC:Tweaked-Typen werden indexiert. Bei kollidierenden Kurzformen sollte die vollständige namespaced ID verwendet werden.

## Desk-Handle

Zusätzlich zu den lokalen Modul- und Displaymethoden besitzt ein Desk-Handle:

```text
getPeripherals()
find(type)
findAll(type)
wrap(side)
```

Metadaten:

```lua
{
  id = "stabile-uuid",
  address = "12,64,-7",
  index = 2,
  x = 12,
  y = 64,
  z = -7,
  dimension = "minecraft:overworld",
  space = "world", -- world | sable | contraption
  computer = false,
  variant = "control_desk",
  facing = "north",
  loaded = true
}
```

## Peripheral-Handle

Das Handle delegiert die echten Methoden des Ziel-Peripherals. Zusätzliche Metadaten stehen über `getPeripheralInfo()` bereit, sofern das Ziel nicht selbst eine Methode dieses Namens definiert:

```lua
{
  address = "12,64,-7/north",
  type = "advanced_peripherals:ender_modem",
  types = { "advanced_peripherals:ender_modem", "modem" },
  deskId = "stabile-uuid",
  deskAddress = "12,64,-7",
  deskPosition = { x = 12, y = 64, z = -7, dimension = "minecraft:overworld" },
  position = { x = 12, y = 64, z = -8, dimension = "minecraft:overworld" },
  side = "north",
  loaded = true
}
```

## Koordinatenzugriff

```lua
local desk = peripherals.wrap(12, 64, -7)
local sameDesk = peripherals.wrap({ x = 12, y = 64, z = -7 })
local device = peripherals.wrap(12, 64, -8)
local radar = peripherals.wrap(12, 64, -8, "radar")
```

Es werden keine Chunks geladen. Gesucht wird nur in der Dimension des eingebetteten Computers.

Integrierte Create-Zugsteuerungen und Vorrichtungs-Steuerungen sind transparente Brückensegmente und erscheinen nicht in `deskCount` oder `getDesks()`. Direkt angrenzende Geräte bleiben über ihre echte Position verfügbar und werden dem nächstgelegenen echten Desk zugeordnet.

## Netzwerkstatus

```lua
{
  state = "active",
  revision = 12,
  dimension = "minecraft:overworld",
  deskCount = 4,
  peripheralCount = 3
}
```

Globale Graphzugriffe werden abgelehnt bei:

- mehreren eingebetteten Computern,
- teilweise geladenen Pultreihen,
- mehr als 64 physischen Pult-, Zugsteuerungs- und Vorrichtungs-Steuerungssegmenten,
- einem Computer außerhalb des Besitzerverbunds.

## Globale `channels`-API

Die bevorzugte Oberfläche für neue Automatisierungsprogramme ist `require("cc_aeroworks.channels")`:

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

Richtungskanäle und Wire-Werte liegen bei `0..15`. Pfade wie `/groups/flight/gear` benötigen eine konfigurierte Gruppe und Bindung. Weitere Wurzeln sind `/modules`, `/wires` und `/groups`.

## Globale `wires`-API

Modulname: `cc_aeroworks.wires`. Sie steuert konfigurierte Wire-Ausgänge direkt:

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

Werte liegen bei `0..15`. Anlegen, Löschen und Umbenennen erfolgt im Channels-Tab oder Shellbefehl `wires`, nicht über diese Runtime-API. Siehe [[Kanäle und Automatisierung|Kanaele-und-Automatisierung]].

## Globale `controls`-API

Nur der eingebettete Computer besitzt diese API:

```lua
local controls = require("cc_aeroworks.controls")
```

Methoden:

```text
getChannels()
getState(deskId, socket, channel)
override(deskId, socket, channel, value)
overrideBatch(commands)
release(deskId, socket, channel)
releaseAll()
```

Kontinuierliche Fahrzeugkanäle:

```text
aeroworks:lever             -> lever
aeroworks:joystick          -> x, y
aeroworks:wheel             -> wheel
aeroworks:yoke              -> turn, pitch
aeroworks:throttle_quadrant -> red, amber, green, blue
```

Display-Pointer-X/Y und binäre Buttons sind nicht Teil der Override-API. Werte sind ganzzahlig `-15..15`.

Beispiel:

```lua
controls.overrideBatch({
  { desk = yokeDeskId, socket = "big", channel = "turn", value = rollCommand },
  { desk = yokeDeskId, socket = "big", channel = "pitch", value = pitchCommand },
})
```

`hard`-Overrides blockieren normale Writes auf den übernommenen Kanal. Computerwrites laufen über Aeroworks' normalen Controller-Setter, sodass effektiver Steuerwert und sichtbare Modulstellung zusammenbleiben. Identische Sollwerte werden nicht erneut geschrieben.

Overrides sind reine Laufzeit-Zustände. Computer-Aus, BlockEntity-Invalidierung, ein ungültiges Multiblock oder ein verschwundenes Ziel geben sie automatisch frei.

Ereignisse:

```text
cc_aeroworks_control_override(action, deskId, deskIndex, socket, socketName, channel, value, mode)
cc_aeroworks_control_release(deskId, socket, socketName, channel, reason)
```

Details: [[Kanäle und Automatisierung|Kanaele-und-Automatisierung]] und [Control-Overrides](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/control-overrides.md).

## Globale `telemetry`-API

Nur der eingebettete Computer besitzt diese API:

```lua
local telemetry = require("cc_aeroworks.telemetry")
```

Lokale Methoden:

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

Strukturiert unterstützte Create-Display-Sources:

```text
create:fill_level   -> fill_level
create:count_items  -> item_count
create:list_items   -> item_list
create:count_fluids -> fluid_amount
create:list_fluids  -> fluid_list
```

Beispiel Füllstand:

```lua
local fuel = telemetry.get("fuel")
if fuel then
  print(fuel.value.current, fuel.value.maximum, fuel.value.percent)
end
```

Source-Metadaten enthalten unter anderem:

```lua
{
  id = "stabile-uuid",
  alias = "fuel",
  sourceType = "create:fill_level",
  kind = "fill_level",
  supported = true,
  available = true,
  stale = false,
  lastSeenTick = 12345,
  ageTicks = 4,
  revision = 8,
  value = { ... },
  displayText = { "75%" }
}
```

Unbekannte Create-Sources werden mit `supported=false` und `displayText` geliefert. Strukturierte Zahlen werden nicht aus dem formatierten Text geparst.

## Dock-Handle

Mit optionalem Create: Simulated:

```lua
local dock = telemetry.getDock("left_cargo")
```

Gemeinsame lokale Modul-/Displaymethoden (auch am eingebetteten Desk-Handle):

```text
getInfo()
listTelemetry()
getTelemetry(nameOrId)
renameTelemetry(nameOrId, alias)
clearTelemetryName(nameOrId)
getTransferBuffers()
```

Remote-Beispiel:

```lua
if dock and dock.getInfo().locked then
  local remoteFuel = dock.getTelemetry("fuel")
  if remoteFuel then print(remoteFuel.value.percent) end
end
```

`getTransferBuffers()` beschreibt ausschließlich die Connector-Puffer für Items, Fluids und Energie. Tatsächliche Tank-/Cargo-Inhalte kommen über Display-Link-Telemetrie.

## Ereignisse

Peripheral-Netz:

```lua
local _, address, primaryType =
  os.pullEvent("cc_aeroworks_peripheral_attached")
```

```lua
local _, address, primaryType =
  os.pullEvent("cc_aeroworks_peripheral_detached")
```

Lokales Pult:

```lua
local _, peripheralName, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_desk_input")
```

Control-Authority:

```text
cc_aeroworks_control_override(action, deskId, deskIndex, socket, socketName, channel, value, mode)
cc_aeroworks_control_release(deskId, socket, socketName, channel, reason)
```

Telemetrie:

```text
cc_aeroworks_telemetry_added(sourceId, revision)
cc_aeroworks_telemetry_changed(sourceId, revision)
cc_aeroworks_telemetry_removed(sourceId)
cc_aeroworks_dock_changed(dockId, state, locked, remoteSubLevelId)
cc_aeroworks_remote_telemetry_changed(dockId, sourceId, action, revision)
```

`action` der Remote-Telemetrie ist `added`, `changed` oder `removed`.

## Sockets

| Socket | Index |
|---|---:|
| `left` | `0` |
| `right` | `1` |
| `big` | `2` |

## Displayvertrag

- Kleine und große Displays unterstützen Text-, Zahlen- und Pixelanzeige.
- Pixelursprung: `(1,1)` links oben.
- Rastergröße: über `getDisplaySize` lesen, nicht fest annehmen.
- `setDisplayPixels`: exakt `height` Strings aus `0` und `1`, jeweils exakt `width` Zeichen.
- `getDisplayColor(socket)` / `setDisplayColor(socket, 0xRRGGBB)`: gespeicherte Farbe; Setzen nur bei erweiterten Displays.
- Touch: nur erweiterte Displays und nur mit erweitertem Computer oder erweitertem CC:-Terminals-Terminal im aktiven Pultverbund.
- Create: Radars: Radar-Ingresses erscheinen als Pseudo-Skripte im selben Dropdown wie Lua-Skripte; die Auswahl ist exklusiv und nur für erweiterte Displays verfügbar.

## Ponder

Die Computerpulte besitzen getrennte Szenen für Netzwerkaufbau, Peripheral-Suche und Diagnose. Displays besitzen getrennte Szenen für Herstellung, Montage und Programmierung. Radar besitzt Szenen für automatisches Routing und Data-Link-Kompatibilität.

Telemetrie wird zusätzlich über [[Telemetrie]] beschrieben. Der [vollständige Lua-Vertrag](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/cc-peripheral-api.md) und die [Beispielprogramme](https://github.com/TeutonStudio/CC-Aeroworks/tree/master/examples/cc) ergänzen diese Schnellreferenz.

## Display-Helper

`require("display")` arbeitet mit einer aufgelösten Display-Ereignistabelle:

```text
resolve(event)
getSize(event)
setResolution(event, ppb)
getColor(event)
setColor(event, color)
clear(event)
getPixel(event, x, y)
setPixel(event, x, y, enabled)
setPixelBatch(event, points, enabled?)
setPixels(event, rows)
setText(event, text)
setNumber(event, value, zeroPad?)
```

`require("touchdisplay")` erbt diese Funktionen und ergänzt:

```text
isTap(event)
isDraw(event)
position(event)
normalizedPosition(event)
drawStart(event)
drawDelta(event)
drawDirection(event)
drawSpeed(event)
drawSamples(event)
drawStroke(event)
drawEnded(event)
drawIdentity(event)
```

Für Freihandzeichnen ist `drawStroke` der Einstieg. Für auflösungsunabhängige Bedienfelder nutze `normalizedPosition`. Gebundene Touch-Handler werden nur vom eingebetteten Computer automatisch geladen; siehe [[Programmierbare-Displays]].

## Beispiel: ein Display im Pultnetz beschreiben

Dieses Programm läuft auf dem eingebetteten Computer und verändert die Ausgabe des ersten gefundenen Displays:

```lua
local peripherals = require("cc_aeroworks.peripherals")
for _, desk in pairs(peripherals.find("ControlDesk")) do
  for _, panel in ipairs(desk.getDisplays()) do
    local socket = panel.socketName
    local size = desk.getDisplaySize(socket)
    print(size.width, size.height, size.ppb)
    desk.setDisplayText(socket, "42")
    return
  end
end
error("Kein Display montiert")
```

Für ein bestimmtes Instrument wähle das Pult anhand seiner stabilen Desk-ID und den Socket ausdrücklich. Eine Positionsadresse kann sich bei Bewegung ändern.

## Beispiel: Pixel und Touch

Mehrere Punkte lassen sich mit einem Aufruf schreiben; Pixelkoordinaten beginnen bei `(1,1)`:

```lua
desk.setDisplayPixelBatch("big", {
  { x = 1, y = 1 },
  { x = 2, y = 1 },
  { x = 3, y = 1 },
}, true)
```

Ein Touch-Handler liegt auf dem eingebetteten Computer und wird in den Moduleinstellungen unter **Display-Steuerung** gewählt. Er benötigt ein erweitertes Display und ein erweitertes Slot-Gerät:

```lua
local touch = require("touchdisplay")
return {
  onTap = function(event)
    local u, v = touch.normalizedPosition(event)
    print(u, v)
  end,
  onDraw = function(event)
    touch.drawStroke(event)
  end,
}
```

Handler müssen zügig zurückkehren. Externe Computer starten gespeicherte owner-lokale Handler nicht automatisch; sie verarbeiten die zugestellten Ereignisse im eigenen Programm.
