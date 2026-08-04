# CC:Tweaked APIs

CC-Aeroworks besitzt zwei Zugriffswege:

1. Ein gewöhnlicher CC:Tweaked-Computer verwendet das Peripheral `cc_aeroworks_control_desk`.
2. Der Computer in einem Computer-Steuerungspult verwendet die globale API `aeroworks` direkt.

Sockets akzeptieren Namen oder den nativen nullbasierten Aeroworks-Index:

| Name | Index |
|---|---:|
| `left` | `0` |
| `right` | `1` |
| `big` | `2` |

Deskparameter sind entweder ein 1-basierter Multiblockindex oder eine stabile Desk-ID.

## Externes Peripheral

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Aeroworks-Steuerungspult verbunden")
```

Eine Verbindung zu einem beliebigen Mitglied genügt. `getDesks()` und alle `*Desk*`-Methoden arbeiten auf dem vollständigen direkt verbundenen Steuerungspult-Multiblock.

### Kompatible Einzelpultmethoden

Diese Methoden adressieren weiterhin genau das Pult, an dem das Peripheral hängt:

- `getSocketCount() -> number`
- `getSockets() -> table`
- `getModules() -> table`
- `getModule(socket) -> table|nil`
- `getInput(socket) -> number|table`
- `getInputs() -> table`
- `getDisplays() -> table`
- `getDisplay(socket) -> table`
- `setDisplayText(socket, text) -> string`
- `setDisplayNumber(socket, value, zeroPad?) -> string`
- `clearDisplay(socket)`
- `clearDisplays() -> number`
- `getDisplaySize(socket) -> table`
- `getDisplayPixel(socket, x, y) -> boolean`
- `setDisplayPixel(socket, x, y, enabled) -> boolean`
- `setDisplayPixels(socket, rows) -> table`
- `clearDisplayPixels(socket)`

Bestehende Programme ändern dadurch ihr Ziel nicht.

### Multiblockmethoden

- `getNetwork() -> table`
- `getDesks() -> table`
- `getDesk(desk) -> table`
- `getDeskSocketCount(desk) -> number`
- `getDeskSockets(desk) -> table`
- `getDeskModules(desk) -> table`
- `getDeskModule(desk, socket) -> table|nil`
- `getDeskInput(desk, socket) -> number|table`
- `getDeskInputs(desk) -> table`
- `getDeskDisplays(desk) -> table`
- `getDeskDisplay(desk, socket) -> table`
- `setDeskDisplayText(desk, socket, text) -> string`
- `setDeskDisplayNumber(desk, socket, value, zeroPad?) -> string`
- `clearDeskDisplay(desk, socket)`
- `clearDeskDisplays(desk) -> number`
- `getDeskDisplaySize(desk, socket) -> table`
- `getDeskDisplayPixel(desk, socket, x, y) -> boolean`
- `setDeskDisplayPixel(desk, socket, x, y, enabled) -> boolean`
- `setDeskDisplayPixels(desk, socket, rows) -> table`
- `clearDeskDisplayPixels(desk, socket)`

Beispiel:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
local desks = console.getDesks()

for _, desk in ipairs(desks) do
  print(desk.index, desk.id, desk.variant, desk.attached)
end

local target = desks[#desks]
print(console.getDeskInput(target.id, "left"))
console.setDeskDisplayText(target.id, "big", "123")
```

`getNetwork()` liefert:

```lua
{
  state = "none" | "active" | "conflict",
  memberCount = 4,
  revision = 12
}
```

`none` bedeutet hier lediglich, dass kein eingebettetes Computer-Steuerungspult im Multiblock liegt. Für einen extern angeschlossenen Computer bleibt der Multiblock vollständig verwendbar.

## Direkte API des Computer-Steuerungspults

Nur der eingebettete Computer besitzt die globale API:

```lua
local desks = aeroworks.getDesks()
```

Es ist ausdrücklich **kein** Peripheral erforderlich. Folgende Aufrufe gehören hier nicht zum Zugriffsweg:

```lua
peripheral.find(...)
peripheral.wrap(...)
peripheral.call(...)
```

Alternativ kann dasselbe API-Objekt geladen werden:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

### Direkte Methoden

- `getNetwork() -> table`
- `getDesks() -> table`
- `getDesk(desk) -> table`
- `getSocketCount(desk) -> number`
- `getSockets(desk) -> table`
- `getModules(desk) -> table`
- `getModule(desk, socket) -> table|nil`
- `getInput(desk, socket) -> number|table`
- `getInputs(desk) -> table`
- `getDisplays(desk) -> table`
- `getDisplay(desk, socket) -> table`
- `setDisplayText(desk, socket, text) -> string`
- `setDisplayNumber(desk, socket, value, zeroPad?) -> string`
- `clearDisplay(desk, socket)`
- `clearDisplays(desk) -> number`
- `getDisplaySize(desk, socket) -> table`
- `getDisplayPixel(desk, socket, x, y) -> boolean`
- `setDisplayPixel(desk, socket, x, y, enabled) -> boolean`
- `setDisplayPixels(desk, socket, rows) -> table`
- `clearDisplayPixels(desk, socket)`

Beispiel:

```lua
local network = aeroworks.getNetwork()
print(network.memberCount)

for _, desk in ipairs(aeroworks.getDesks()) do
  print(desk.index, desk.id, desk.owner)
  for _, socket in ipairs(aeroworks.getSockets(desk.id)) do
    print(socket.name, socket.index)
  end
end

aeroworks.setDisplayNumber(1, "big", 42, true)
```

Enthält derselbe Multiblock mehrere Computer-Steuerungspulte, verweigert die direkte API den mehrdeutigen Zugriff. Jeder Computer behält seine Computer-ID und sein Dateisystem.

## Desk-Beschreibungen

`getDesk` und `getDesks` liefern mindestens:

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

Beim externen Peripheral kennzeichnet `attached` das physisch angeschlossene Pult. In der direkten API kennzeichnet `owner` das Pult, dessen eingebetteter Computer die API ausführt.

## Displayvertrag

Texte werden links beginnend auf zwei beziehungsweise drei Zeichen begrenzt. Zulässig sind `0-9`, Minus und Leerzeichen; andere Zeichen werden zu Leerzeichen.

Zahlen werden gegen null abgeschnitten und auf `-9..99` beziehungsweise `-99..999` begrenzt. `zeroPad` füllt die Ziffern rechts vom Vorzeichen mit Nullen. NaN und Unendlich erzeugen einen Lua-Fehler.

Der Pixelmodus verwendet beim Zweisteller `7x5`, beim Dreisteller `11x5`. Koordinaten beginnen bei `(1,1)` links oben. `setDisplayPixels` erwartet genau fünf Strings aus `0` und `1`.

## Ereignisse

### Angeschlossenes Einzelpult

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

Der erste Eingabesnapshot erzeugt kein Ereignis. Bei entfernten Kanälen oder Modulen ist `value` `nil`.

## Fehlerzustände

- Mehr als 64 verbundene Pulte: Zugriff wird abgelehnt.
- Teilweise geladener Multiblock: Zugriff wird abgelehnt, statt einen unvollständigen Zustand als vollständig auszugeben.
- Ungültiger Deskindex oder unbekannte Desk-ID: Lua-Fehler.
- Ungültiger Socket: Lua-Fehler.
- Mehrere eingebettete Computer: direkter Zugriff ist mehrdeutig und wird abgelehnt; externe Peripheral-Methoden bleiben nutzbar.
