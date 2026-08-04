# Steuerungspulte mit CC:Tweaked programmieren

## Einzelpult

```lua
local desk = peripheral.find("cc_aeroworks_control_desk")
assert(desk, "Kein Pult verbunden")

for _, module in ipairs(desk.getModules()) do
  print(module.socketName, module.id, module.kind)
end
```

## Multiblockcomputer

Gleich ausgerichtete Control Desks verbinden sich links und rechts. Die Reihe darf höchstens 64 geladene Pulte enthalten und genau ein Computer-Steuerungspult besitzen.

Das Terminal öffnet sich von jedem Mitglied mit Schleichen und Rechtsklick bei leerer Haupthand.

```lua
local desks = aeroworks.getDesks()

for _, desk in ipairs(desks) do
  print(desk.index, desk.id, desk.x, desk.y, desk.z, desk.variant)

  for _, module in ipairs(aeroworks.getModules(desk.id)) do
    print(" ", module.socketName, module.id)
  end
end
```

Ein Display des dritten Pults:

```lua
aeroworks.setDisplayNumber(3, "big", 42, true)
```

Änderungen aller Pulte beobachten:

```lua
while true do
  local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
    os.pullEvent("cc_aeroworks_console_input")

  print(deskIndex, socketName, moduleId, channel, value)
end
```

Bei der Entfernung eines Moduls oder Kanals ist `value` nil. Nach einer Multiblockänderung wird `cc_aeroworks_console_changed` erzeugt.
