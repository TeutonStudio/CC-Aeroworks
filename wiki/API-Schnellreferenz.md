# API-Schnellreferenz

## Zugriffswege

| Eingebetteter Computer | Externer Computer |
|---|---|
| globale `peripherals`- und `telemetry`-APIs | lokales Peripheral `ControlDesk` |
| sieht alle Pulte, Nachbargeräte und lokale Telemetrie | sieht nur das direkt verbundene Pult |
| kein Modem erforderlich | direkt oder über Wired Modem |
| `cc_aeroworks_*`-Ereignisse | `cc_aeroworks_desk_input` |

Die alte globale `aeroworks`-API und die netzwerkweiten `getDesk...`-Methoden sind nicht Teil des neuen Vertrags.

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

Methoden:

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
setDisplayPixels(socket, rows)
clearDisplayPixels(socket)
```

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
getTypes()
getNetwork()
refresh()
```

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
- mehr als 64 Pulten,
- einem Computer außerhalb des Besitzerverbunds.

# Globale `telemetry`-API

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

Methoden:

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

- Text: zwei beziehungsweise drei Zeichen.
- Zahlen: zweistellig `-9..99`, dreistellig `-99..999`.
- Pixelursprung: `(1,1)` links oben.
- Rastergröße: über `getDisplaySize` lesen, nicht fest annehmen.
- `setDisplayPixels`: exakt `height` Strings aus `0` und `1`, jeweils exakt `width` Zeichen.

## Ponder

Die Computerpulte besitzen getrennte Szenen für Netzwerkaufbau, Peripheral-Suche und Diagnose. Displays besitzen getrennte Szenen für Herstellung, Montage und Programmierung. Radar besitzt Szenen für automatisches Routing und Data-Link-Kompatibilität.

Telemetrie wird zusätzlich über [[Telemetrie]] und die Repository-Dokumente `docs/telemetry.md` sowie `docs/docking-telemetry.md` beschrieben.
