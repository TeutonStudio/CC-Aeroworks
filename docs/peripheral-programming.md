# Steuerungspulte mit CC:Tweaked programmieren

## Lokales Pult an einem externen Computer

Ein gewöhnlicher Computer oder ein Wired Modem sieht jedes direkt verbundene Steuerungspult als eigenen Adapter:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein lokales Steuerungspult verbunden")
```

Dieser Adapter arbeitet nur auf seinem Pult:

```lua
for _, module in ipairs(desk.getModules()) do
  print(module.socketName, module.id)
end

local value = desk.getInput("left")
desk.setDisplayNumber("big", value, false)
```

Mehrere Pulte in einem Wired-Modem-Netz bleiben mehrere normale CC:Tweaked-Peripherals. Das globale Multiblock-Verzeichnis gehört ausschließlich zum eingebetteten Computer.

## Eingebetteter Computer und `peripherals`

Der Computer im Computer-Steuerungspult stellt die globale API `peripherals` bereit:

```lua
local network = peripherals.getNetwork()
print(network.state, network.deskCount, network.peripheralCount)
```

Die Modulform ist gleichwertig:

```lua
local peripherals = require("cc_aeroworks.peripherals")
```

### Alle Pulte adressieren

```lua
local desks = peripherals.find("ControlDesk")

for address, desk in pairs(desks) do
  local info = desk.getInfo()
  print(address, info.id, info.variant, info.computer)
end
```

`ControlDesk` liefert immer eine Tabelle. Schlüssel sind kanonische Weltpositionen wie `12,64,-7`.

```lua
local panel = desks["12,64,-7"]
assert(panel, "Zielpult fehlt")
panel.setDisplayText("big", "123")
```

Die Desk-ID bleibt die stabile Identität; die Position ist die aktuelle Netzwerkadresse.

## Eine Peripheral-Gattung finden

Kommt ein Typ im gesamten Pultnetz genau einmal vor, wird direkt sein Methoden-Handle zurückgegeben:

```lua
local modem = peripherals.find("endermodem")
assert(modem, "Kein EnderModem gefunden")
modem.open(42)
```

Ein namespaced Typ wie `advanced_peripherals:ender_modem` ist auch über `ender_modem`, `EnderModem` oder `endermodem` erreichbar.

Bei mehreren Treffern liefert `find` eine Tabelle:

```lua
local modems = peripherals.find("endermodem")

for address, modem in pairs(modems) do
  local info = modem.getPeripheralInfo()
  print(address, info.deskAddress, info.side)
end
```

Programme, die immer eine Sammlung benötigen, verwenden `findAll`:

```lua
for address, modem in pairs(peripherals.findAll("endermodem")) do
  print(address)
end
```

Ohne Treffer liefert `find` `nil`, `findAll` dagegen `{}`.

## Suche an einem einzelnen Pult

```lua
local desk = peripherals.find("ControlDesk")["12,64,-7"]

local modem = desk.find("endermodem")
local north = desk.wrap("north")

for address, device in pairs(desk.getPeripherals()) do
  print(address, device.getPeripheralInfo().type)
end
```

## Koordinatenzugriff

```lua
local desk = peripherals.wrap(12, 64, -7)
local sameDesk = peripherals.wrap({ x = 12, y = 64, z = -7 })
local device = peripherals.wrap(12, 64, -8)
```

Stellt ein Block mehrere Peripherals bereit, wird der Typ ergänzt:

```lua
local radar = peripherals.wrap(12, 64, -8, "radar")
```

## Displays

```lua
local desks = peripherals.find("ControlDesk")
local displayDesk = desks["12,64,-7"]

displayDesk.setDisplayText("big", "42")
displayDesk.setDisplayNumber("big", -7, true)
```

Pixelraster dürfen die Auflösung nicht fest annehmen:

```lua
local size = displayDesk.getDisplaySize("big")
local rows = {}

for y = 1, size.height do
  rows[y] = string.rep(y == 1 or y == size.height and "1" or "0", size.width)
end

displayDesk.setDisplayPixels("big", rows)
```

Ein robusteres Rahmenbeispiel:

```lua
local size = displayDesk.getDisplaySize("big")
local rows = {}

for y = 1, size.height do
  local row = {}
  for x = 1, size.width do
    row[x] = (x == 1 or x == size.width or y == 1 or y == size.height) and "1" or "0"
  end
  rows[y] = table.concat(row)
end

displayDesk.setDisplayPixels("big", rows)
```

## Peripheral-Ereignisse

Nach der initialen Graphauflösung meldet eine Aktualisierung neue und entfernte Geräte:

```lua
while true do
  local event, address, primaryType = os.pullEvent()

  if event == "cc_aeroworks_peripheral_attached" then
    print("Verbunden:", address, primaryType)
  elseif event == "cc_aeroworks_peripheral_detached" then
    print("Getrennt:", address, primaryType)
  end
end
```

Nach einer Capability-Änderung ohne Block- oder Nachbarupdate kann der Graph explizit erneuert werden:

```lua
peripherals.refresh()
```

Lokale Eingaben eines direkt angeschlossenen `ControlDesk` verwenden weiterhin:

```lua
local _, peripheralName, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_desk_input")
```

## Diagnose

```lua
local network = peripherals.getNetwork()
print(textutils.serialize(network))
print(textutils.serialize(peripherals.getTypes()))
```

Globale Graphzugriffe werden als Lua-Fehler abgelehnt bei:

- mehreren eingebetteten Computern,
- teilweise geladenen Pultreihen,
- mehr als 64 verbundenen Pulten,
- einem Computer außerhalb des aufgelösten Besitzerverbunds.

Ein fehlender Peripheral-Typ ist dagegen kein Topologiefehler und liefert einfach `nil`.
