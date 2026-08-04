# CC-Aeroworks Wiki

CC-Aeroworks verbindet die Steuerungspulte aus Create: Aeroworks mit CC:Tweaked. Die Mod ergänzt eingebettete Computer-Steuerungspulte, programmierbare zwei- und dreistellige Displays und eine kombinierte Maussteuerung für Lever, Joystick und Throttle Quadrants.

## Einstieg

- [[Computer-Steuerungspulte]] erklärt Crafting, Bedienung, Multiblocks und die beiden Computer-Zugriffswege.
- [[Programmierbare-Displays]] beschreibt Text-, Zahlen- und Pixelmodus samt Lua-Beispielen.
- [[Kombinierte-Eingabe]] erklärt Einrichtung und Bedienung des Halte-zu-Steuern-Modus.
- [[API-Schnellreferenz]] fasst Sockets, Methoden, Ereignisse und Fehlerzustände zusammen.

## Zwei Zugriffswege

### Eingebetteter Computer

Ein Computer-Steuerungspult enthält einen normalen oder erweiterten CC:Tweaked-Computer. Im Terminal steht die globale API `aeroworks` bereit:

```lua
local desks = aeroworks.getDesks()
assert(#desks > 0, "Kein Steuerungspult gefunden")

aeroworks.setDisplayText(desks[1].id, "big", "123")
```

Dafür sind weder Modem noch `peripheral.find` oder `peripheral.wrap` erforderlich. Dasselbe API-Objekt kann alternativ geladen werden:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

### Externer Computer

Ein gewöhnlicher CC:Tweaked-Computer oder ein Wired Modem kann mit einem beliebigen Pult des Multiblocks verbunden werden:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

for _, desk in ipairs(console.getDesks()) do
  print(desk.index, desk.id, desk.variant)
end
```

Eine einzige Verbindung reicht für den vollständigen direkt verbundenen Multiblock. Die älteren Einzelpultmethoden bleiben erhalten und beziehen sich weiterhin nur auf das physisch angeschlossene Pult.

## Wichtige Begriffe

| Begriff | Bedeutung |
|---|---|
| Desk | Ein einzelnes Steuerungspult im Multiblock |
| Socket | Ein Modulplatz: `left`, `right` oder `big` |
| Desk-ID | Stabile UUID eines Pults; robuster als der Netzwerkindex |
| Netzwerkindex | Aktuelle 1-basierte Position eines Pults im Multiblock |
| Direkte API | Globale `aeroworks`-API des eingebetteten Computers |
| Peripheral API | `cc_aeroworks_control_desk` für externe Computer |

## Multiblock-Grundregeln

Gleich ausgerichtete Steuerungspulte verbinden sich unmittelbar links und rechts zu einem linearen Multiblock. Normale Aeroworks-Pulte und beide Computer-Steuerungspultvarianten dürfen gemischt werden.

- Maximal 64 Mitglieder
- Keine automatische Chunk-Nachladung
- Teilweise geladene Netzwerke werden abgelehnt
- Mehrere eingebettete Computer im selben Multiblock erzeugen einen Konflikt
- Externe Peripheral-Zugriffe bleiben auch im Konfliktfall nutzbar

## Minimalbeispiel

Das folgende Programm spiegelt einen numerischen Eingang des ersten Pults auf das große Display des letzten Pults:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

local desks = console.getDesks()
assert(#desks > 0, "Leerer Multiblock")

local source = desks[1]
local target = desks[#desks]

while true do
  local _, _, deskId, _, _, _, value =
    os.pullEvent("cc_aeroworks_multiblock_input")

  if deskId == source.id and type(value) == "number" then
    console.setDeskDisplayNumber(target.id, "big", value, false)
  end
end
```

## Projektstatus

CC-Aeroworks befindet sich noch in einer frühen Integrationsphase. Compiler- und statische Prüfungen ersetzen keine vollständigen Ingame-Tests für Rendering, Persistenz, bewegte Konstruktionen oder reale Multiblocks.
