# Steuerungspulte mit CC:Tweaked programmieren

## Externer Computer

Ein gewöhnlicher Computer oder ein Wired Modem wird mit einem beliebigen Steuerungspult des Multiblocks verbunden.

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")
```

Die bisherigen Methoden wie `getInput("left")` adressieren weiterhin das physisch angeschlossene Pult. Für den gesamten Multiblock wird zuerst ein Desk gewählt:

```lua
local desks = console.getDesks()

for _, desk in ipairs(desks) do
  print(
    ("Desk %d: %s (%s)"):format(
      desk.index,
      desk.id,
      desk.variant
    )
  )
end

local first = desks[1]
local last = desks[#desks]

local input = console.getDeskInput(first.id, "left")
console.setDeskDisplayNumber(last.id, "big", input, false)
```

Der Deskparameter kann der 1-basierte Index oder die stabile ID sein. IDs sind vorzuziehen, wenn ein Programm Umbauten überstehen soll.

## Eingabeereignisse des gesamten Multiblocks

```lua
while true do
  local _, peripheralName, deskId, deskIndex, socket, moduleId, value, channel, socketName =
    os.pullEvent("cc_aeroworks_multiblock_input")

  if value == nil then
    print(("Desk %d, %s: Kanal entfernt"):format(deskIndex, socketName))
  else
    print(
      ("Desk %d, %s, %s = %s"):format(
        deskIndex,
        socketName,
        channel,
        tostring(value)
      )
    )
  end
end
```

Eine Strukturänderung wird getrennt gemeldet:

```lua
local _, peripheralName, state, memberCount, revision =
  os.pullEvent("cc_aeroworks_multiblock_changed")
```

## Eingebetteter Computer

Im Computer-Steuerungspult ist kein `peripheral.find` nötig. Die API ist direkt global vorhanden:

```lua
local desks = aeroworks.getDesks()
local owner

for _, desk in ipairs(desks) do
  if desk.owner then
    owner = desk
  end
end

assert(owner, "Besitzerpult fehlt")
print("Eigene Socketanzahl:", aeroworks.getSocketCount(owner.id))
aeroworks.setDisplayText(owner.id, "big", "ON")
```

Die folgende Schreibweise ist gleichwertig:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

Ein vollständiges Dashboard:

```lua
local desks = aeroworks.getDesks()
assert(#desks > 0, "Leerer Steuerungspult-Multiblock")

while true do
  local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
    os.pullEvent("cc_aeroworks_console_input")

  local target = desks[#desks]
  if value == nil then
    aeroworks.clearDisplay(target.id, "big")
  elseif type(value) == "number" then
    aeroworks.setDisplayNumber(target.id, "big", value, false)
  end
end
```

## Text und Pixel

```lua
console.setDeskDisplayText(1, "big", "42")
console.setDeskDisplayNumber(1, "big", -7, true)

console.setDeskDisplayPixels(1, "big", {
  "11111111111",
  "10000000001",
  "10111111101",
  "10000000001",
  "11111111111",
})
```

Im eingebetteten Computer werden dieselben Operationen ohne `Desk` im Methodennamen verwendet:

```lua
aeroworks.setDisplayText(1, "big", "42")
aeroworks.setDisplayPixel(1, "big", 1, 1, true)
```

## Topologiefehler

`getNetwork()` zeigt den aktuellen Zustand:

```lua
local network = console.getNetwork()
print(network.state, network.memberCount, network.revision)
```

- `too_large` und `partially_loaded` werden als Lua-Fehler behandelt.
- `conflict` bedeutet mehrere Computer-Steuerungspulte. Externe Peripheral-Zugriffe funktionieren weiterhin.
- Die direkte API eines eingebetteten Computers verweigert den Konfliktzustand, damit kein Computer zufällig zum Besitzer erklärt wird.
