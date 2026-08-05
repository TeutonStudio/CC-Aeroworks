# API-Schnellreferenz

CC-Aeroworks besitzt zwei API-Zugriffswege. Beide greifen auf denselben Steuerungspult-Multiblock zu, verwenden aber unterschiedliche Methodennamen und Ereignisse.

## Zugriffsweg wählen

| Eingebetteter Computer | Externer Computer |
|---|---|
| genau ein Computer-Steuerungspult im Multiblock | normaler CC:Tweaked-Computer direkt oder über Wired Modem |
| globale `aeroworks`-API | Peripheral `cc_aeroworks_control_desk` |
| kein Modem nötig | Verbindung zu einem beliebigen Pult genügt |
| `cc_aeroworks_console_*`-Ereignisse | `cc_aeroworks_desk_*` und `cc_aeroworks_multiblock_*` |

Ein eingebetteter und ein externer Computer sind Alternativen, keine gemeinsame Voraussetzung. Mehrere externe Computer dürfen dasselbe Peripheral-Netzwerk verwenden; höchstens ein **eingebetteter** Computer ist erlaubt.

## Externer Computer

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")
```

Eine direkte Verbindung oder ein Wired Modem zu einem beliebigen Multiblockmitglied genügt.

## Eingebetteter Computer

```lua
local desks = aeroworks.getDesks()
```

Alternativ:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

Kein Peripheral und kein Modem erforderlich.

## Sockets und Deskparameter

| Socket | Index |
|---|---:|
| `left` | `0` |
| `right` | `1` |
| `big` | `2` |

Deskparameter akzeptieren:

- den aktuellen 1-basierten Netzwerkindex oder
- die stabile Desk-ID aus `getDesks()`.

Für gespeicherte Konfigurationen ist die Desk-ID vorzuziehen.

## Netzwerk und Desks

Beide Zugriffswege bieten:

```text
getNetwork()
getDesks()
getDesk(desk)
```

`getNetwork()` liefert:

```lua
{
  state = "none" | "active" | "conflict",
  memberCount = 4,
  revision = 12
}
```

Ein Desk enthält unter anderem:

```lua
{
  id = "stabile-uuid",
  index = 1,
  x = 10,
  y = 64,
  z = -5,
  variant = "control_desk" | "computer" | "advanced_computer",
  computer = false,
  facing = "north",
  loaded = true
}
```

Beim externen Peripheral markiert `attached` das physisch verbundene Pult. Bei der direkten API markiert `owner` das Pult des eingebetteten Computers.

## Einzelpultmethoden des externen Peripherals

Diese Methoden beziehen sich auf das physisch angeschlossene Pult:

```text
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

## Multiblockmethoden des externen Peripherals

```text
getNetwork()
getDesks()
getDesk(desk)
getDeskSocketCount(desk)
getDeskSockets(desk)
getDeskModules(desk)
getDeskModule(desk, socket)
getDeskInput(desk, socket)
getDeskInputs(desk)
getDeskDisplays(desk)
getDeskDisplay(desk, socket)
setDeskDisplayText(desk, socket, text)
setDeskDisplayNumber(desk, socket, value, zeroPad?)
clearDeskDisplay(desk, socket)
clearDeskDisplays(desk)
getDeskDisplaySize(desk, socket)
getDeskDisplayPixel(desk, socket, x, y)
setDeskDisplayPixel(desk, socket, x, y, enabled)
setDeskDisplayPixels(desk, socket, rows)
clearDeskDisplayPixels(desk, socket)
```

## Direkte Methoden des eingebetteten Computers

Das Desk ist jeweils der erste Parameter:

```text
getNetwork()
getDesks()
getDesk(desk)
getSocketCount(desk)
getSockets(desk)
getModules(desk)
getModule(desk, socket)
getInput(desk, socket)
getInputs(desk)
getDisplays(desk)
getDisplay(desk, socket)
setDisplayText(desk, socket, text)
setDisplayNumber(desk, socket, value, zeroPad?)
clearDisplay(desk, socket)
clearDisplays(desk)
getDisplaySize(desk, socket)
getDisplayPixel(desk, socket, x, y)
setDisplayPixel(desk, socket, x, y, enabled)
setDisplayPixels(desk, socket, rows)
clearDisplayPixels(desk, socket)
```

## Ereignisse

### Einzelpult eines externen Peripherals

```lua
local _, peripheralName, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_desk_input")
```

### Multiblock eines externen Peripherals

```lua
local _, peripheralName, deskId, deskIndex, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_multiblock_input")
```

```lua
local _, peripheralName, state, memberCount, revision =
  os.pullEvent("cc_aeroworks_multiblock_changed")
```

### Eingebetteter Computer

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
  os.pullEvent("cc_aeroworks_console_input")
```

```lua
local _, state, memberCount, revision =
  os.pullEvent("cc_aeroworks_console_changed")
```

Der erste Eingabesnapshot erzeugt kein Ereignis. Wenn ein Kanal oder Modul entfernt wurde, ist `value` gleich `nil`.

## Displayvertrag

### Text und Zahlen

- zwei oder drei Zeichen
- erlaubt: `0-9`, Minus, Leerzeichen
- zweistellig: `-9..99`
- dreistellig: `-99..999`
- optionale Nullauffüllung
- NaN und Unendlich erzeugen Lua-Fehler

### Pixel

- zweistellig: `7x5`
- dreistellig: `11x5`
- Ursprung `(1,1)` links oben
- `set...Pixels` erwartet fünf Strings aus `0` und `1`

## Fehlerzustände

| Fehler | Verhalten |
|---|---|
| mehr als 64 Pulte | Zugriff wird abgelehnt |
| Multiblock teilweise geladen | Zugriff wird abgelehnt |
| ungültiger Deskindex oder unbekannte Desk-ID | Lua-Fehler |
| ungültiger Socket | Lua-Fehler |
| mehrere eingebettete Computer | direkte API wird abgelehnt; externe Peripheral-Methoden bleiben nutzbar |

Normale Survival-Platzierung verhindert neue Mehrcomputer-Konflikte automatisch: Das neu platzierte Computerpult wird zum normalen Pult und sein Computer ausgeworfen. Konflikte aus Altwelten, Befehlen oder Strukturwerkzeugen bleiben diagnostizierbar.

## Bedienung und Ponder

Die Interaktionen stehen unter [[Bedienung]]. Über beiden Computerpultitems kann **W gehalten** werden, um die Create-Ponder-Erklärung zu öffnen.
