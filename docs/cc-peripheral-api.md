# CC:Tweaked APIs

## Einzelnes Steuerungspult

Peripheral-Typ: `cc_aeroworks_control_desk`.

Methoden:

- `getSocketCount()`
- `getSockets()`
- `getModules()`
- `getModule(socket)`
- `getInput(socket)`
- `getInputs()`
- `getDisplays()`
- `getDisplay(socket)`
- `setDisplayText(socket, text)`
- `setDisplayNumber(socket, value, zeroPad?)`
- `clearDisplay(socket)`
- `clearDisplays()`
- `getDisplaySize(socket)`
- `getDisplayPixel(socket, x, y)`
- `setDisplayPixel(socket, x, y, enabled)`
- `setDisplayPixels(socket, rows)`
- `clearDisplayPixels(socket)`

Sockets: `left=0`, `right=1`, `big=2`.

Ereignis:

```lua
local _, peripheralName, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_desk_input")
```

Bei entferntem Modul oder Kanal ist `value` nil.

## Computer-Steuerungspult

Nur der eingebettete Computer besitzt die globale API `aeroworks` und das require-Modul `cc_aeroworks.aeroworks`.

```lua
local desks = aeroworks.getDesks()
local first = aeroworks.getDesk(1)
local same = aeroworks.getDesk(first.id)
```

Alle Einzelpultmethoden existieren mit einem zusätzlichen ersten Deskparameter:

```lua
aeroworks.getModules(desk)
aeroworks.getModule(desk, socket)
aeroworks.getInput(desk, socket)
aeroworks.getInputs(desk)
aeroworks.getDisplays(desk)
aeroworks.getDisplay(desk, socket)
aeroworks.setDisplayText(desk, socket, text)
aeroworks.setDisplayNumber(desk, socket, value, zeroPad)
aeroworks.clearDisplay(desk, socket)
aeroworks.clearDisplays(desk)
aeroworks.getDisplaySize(desk, socket)
aeroworks.getDisplayPixel(desk, socket, x, y)
aeroworks.setDisplayPixel(desk, socket, x, y, enabled)
aeroworks.setDisplayPixels(desk, socket, rows)
aeroworks.clearDisplayPixels(desk, socket)
```

`desk` ist ein 1-basierter Netzwerkindex oder die stabile Desk-ID.

Eingabeereignis:

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
  os.pullEvent("cc_aeroworks_console_input")
```

Strukturereignis:

```lua
local _, state, memberCount, revision =
  os.pullEvent("cc_aeroworks_console_changed")
```
