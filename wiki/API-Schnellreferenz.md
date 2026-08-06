# API-Schnellreferenz

## Zugriffswege

| Eingebetteter Computer | Externer Computer |
|---|---|
| globale `peripherals`-API | lokales Peripheral `ControlDesk` |
| sieht alle Pulte und deren Nachbargeräte | sieht nur das direkt verbundene Pult |
| kein Modem erforderlich | direkt oder über Wired Modem |
| `cc_aeroworks_peripheral_*`-Ereignisse | `cc_aeroworks_desk_input` |

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

## Ereignisse

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
