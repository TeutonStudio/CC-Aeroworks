# API-Schnellreferenz

CC-Aeroworks besitzt zwei API-Zugriffswege. Beide greifen auf dieselben Steuerungspulte, Module, Eingaben und Displays zu, verwenden aber unterschiedliche Methodennamen.

## Zugriffswege

### Externer Computer

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")
```

Peripheral-Typ: `cc_aeroworks_control_desk`

Eine direkte Verbindung oder ein Wired Modem zu einem beliebigen Mitglied genügt.

### Eingebetteter Computer

```lua
local desks = aeroworks.getDesks()
```

Alternativ:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

Kein Peripheral und kein Modem erforderlich.

## Sockets

| Name | Index |
|---|---:|
| `left` | `0` |
| `right` | `1` |
| `big` | `2` |

Alle Socketparameter akzeptieren Namen oder Indizes.

## Deskparameter

Multiblockmethoden erwarten ein Desk als:

- 1-basierten Netzwerkindex, oder
- stabile Desk-ID aus `getDesks()`

Für gespeicherte Konfigurationen ist die Desk-ID vorzuziehen.

## Netzwerk und Desks

### Externes Peripheral

```lua
console.getNetwork()
console.getDesks()
console.getDesk(desk)
```

### Eingebetteter Computer

```lua
aeroworks.getNetwork()
aeroworks.getDesks()
aeroworks.getDesk(desk)
```

`getNetwork()` liefert ungefähr:

```lua
{
  state = "none" | "active" | "conflict",
  memberCount = 4,
  revision = 12
}
```

Ein Desk enthält mindestens:

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

Diese Methoden beziehen sich nur auf das Pult, an dem das Peripheral physisch hängt:

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

Die direkte API verwendet keine `Desk`-Zwischenstücke in den Methodennamen. Das Desk ist stattdessen der erste Parameter:

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

## Eingabewerte

`getInput` liefert:

- eine Zahl bei Modulen mit einem Kanal
- eine Tabelle nach Kanal-ID bei Modulen mit mehreren Kanälen

```lua
local value = console.getDeskInput(deskId, "left")

if type(value) == "table" then
  for channel, channelValue in pairs(value) do
    print(channel, channelValue)
  end
else
  print(value)
end
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

### Text

- zwei oder drei Zeichen
- erlaubt: `0-9`, Minus, Leerzeichen
- andere Zeichen werden zu Leerzeichen

### Zahlen

- gegen null abgeschnitten
- zweistellig: `-9..99`
- dreistellig: `-99..999`
- optionale Nullauffüllung
- NaN und Unendlich erzeugen Fehler

### Pixel

- zweistellig: `7x5`
- dreistellig: `11x5`
- Ursprung `(1,1)` links oben
- `set...Pixels` erwartet fünf Strings aus `0` und `1`

## Typische Beispiele

### Alle Pulte auflisten

```lua
local console = peripheral.find("cc_aeroworks_control_desk")

for _, desk in ipairs(console.getDesks()) do
  print(
    ("%d: %s, %s, %s"):format(
      desk.index,
      desk.id,
      desk.variant,
      desk.attached and "verbunden" or "entfernt"
    )
  )
end
```

### Stabil per Desk-ID adressieren

```lua
local desks = console.getDesks()
local savedId = desks[1].id

console.setDeskDisplayText(savedId, "big", "42")
```

### Netzwerkänderungen behandeln

```lua
while true do
  local _, _, state, memberCount, revision =
    os.pullEvent("cc_aeroworks_multiblock_changed")

  print(state, memberCount, revision)
end
```

## Fehlerzustände

| Fehler | Verhalten |
|---|---|
| Mehr als 64 Pulte | Zugriff wird abgelehnt |
| Multiblock teilweise geladen | Zugriff wird abgelehnt |
| Ungültiger Deskindex | Lua-Fehler |
| Unbekannte Desk-ID | Lua-Fehler |
| Ungültiger Socket | Lua-Fehler |
| Mehrere eingebettete Computer | Direkte API wird abgelehnt |

Im Konfliktfall bleiben die externen Peripheral-Methoden nutzbar.
