# Computer-Steuerungspulte

Computer-Steuerungspulte verbinden ein Aeroworks-Steuerungspult dauerhaft mit einem CC:Tweaked-Computer. Dadurch kann das Pult seinen gesamten verbundenen Multiblock ohne externes Peripheral oder Modem verwalten.

## Varianten

| Eingesetzter Computer | Ergebnis |
|---|---|
| Normaler CC:Tweaked-Computer | Computer-Steuerungspult |
| Erweiterter CC:Tweaked-Computer | Erweitertes Computer-Steuerungspult |

Die Computerfamilie bestimmt die Variante. Ein erweitertes Computer-Steuerungspult besitzt entsprechend die Eigenschaften des Advanced Computers.

## Crafting

Lege genau diese beiden Gegenstände gemeinsam in ein beliebiges Craftingfeld mit mindestens zwei Plätzen:

1. Ein normales Aeroworks-Steuerungspult
2. Einen normalen oder erweiterten CC:Tweaked-Computer

Die Position im Craftingfeld spielt keine Rolle. Zusätzliche Gegenstände machen das Rezept ungültig.

Beim Crafting bleiben die wichtigen Komponenten beider Eingaben erhalten:

- Aeroworks-Modul- und Steuerungspultdaten
- Computer-ID
- Computerlabel
- Terminalgröße und Kapazität

Das Rezept ist deshalb kein bloßer Austausch des Blocktyps. Ein bereits eingerichtetes Pult und ein bereits benannter Computer sollen ihre Daten behalten.

## Terminal öffnen

Bei einem gültigen Multiblock mit genau einem eingebetteten Computer:

1. Schleichen
2. Leere Haupthand verwenden
3. Ein beliebiges geladenes Mitglied des Multiblocks rechtsklicken

Das Terminal des eingebetteten Computers wird geöffnet. Der Computer muss also nicht direkt angeklickt werden.

## Direkte `aeroworks`-API

Im eingebetteten Computer steht `aeroworks` global zur Verfügung:

```lua
local network = aeroworks.getNetwork()
print("Pulte:", network.memberCount)

for _, desk in ipairs(aeroworks.getDesks()) do
  print(desk.index, desk.id, desk.variant, desk.owner)
end
```

Alternativ:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

Bei der direkten API ist kein Peripheral beteiligt. Diese Aufrufe gehören hier ausdrücklich nicht zum normalen Zugriffsweg:

```lua
peripheral.find(...)
peripheral.wrap(...)
peripheral.call(...)
```

## Pulte adressieren

Fast alle direkten Methoden erwarten zuerst ein Pult. Zulässig sind:

- der aktuelle 1-basierte Netzwerkindex, zum Beispiel `1`
- die stabile Desk-ID aus `getDesks()`, zum Beispiel `"4d0f..."`

Desk-IDs sind für dauerhafte Programme besser geeignet. Der Index kann sich nach Umbauten ändern.

```lua
local desks = aeroworks.getDesks()
local target = desks[#desks]

aeroworks.setDisplayNumber(target.id, "big", 42, true)
```

## Eigentümerpult finden

In der direkten API markiert `owner = true` das Pult, dessen eingebetteter Computer das Programm ausführt:

```lua
local owner

for _, desk in ipairs(aeroworks.getDesks()) do
  if desk.owner then
    owner = desk
    break
  end
end

assert(owner, "Eigentümerpult fehlt")
print("Eigene Sockets:", aeroworks.getSocketCount(owner.id))
```

## Multiblocks

Ein Multiblock entsteht aus unmittelbar links und rechts angrenzenden, gleich ausgerichteten Steuerungspulten. Unterstützt werden:

- normale Aeroworks-Steuerungspulte
- Computer-Steuerungspulte
- erweiterte Computer-Steuerungspulte

Die Pulte bilden eine lineare Reihe. Das Netzwerk lädt keine fehlenden Chunks nach und ist auf 64 Mitglieder begrenzt.

```lua
local network = aeroworks.getNetwork()

print(network.state)
print(network.memberCount)
print(network.revision)
```

Typische Zustände:

| Zustand | Bedeutung |
|---|---|
| `active` | Genau ein eingebetteter Computer verwaltet den Multiblock |
| `conflict` | Mehrere eingebettete Computer wurden gefunden |
| `none` | Kein eingebetteter Computer; relevant für externe Peripheral-Zugriffe |

## Konflikt mit mehreren Computern

Mehrere Computer-Steuerungspulte im selben Multiblock werden nicht automatisch zusammengeführt. Jeder Computer behält seine eigene Computer-ID und sein eigenes Dateisystem.

Im Konfliktzustand verweigert die direkte `aeroworks`-API den mehrdeutigen Zugriff. Ein externer Computer kann den Multiblock weiterhin über `cc_aeroworks_control_desk` verwenden.

Behebung:

1. Multiblock trennen, oder
2. alle bis auf ein Computer-Steuerungspult durch normale Pulte ersetzen

## Eingaben verarbeiten

Der eingebettete Computer erhält Eingabeereignisse für den gesamten Multiblock:

```lua
while true do
  local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
    os.pullEvent("cc_aeroworks_console_input")

  print(
    ("Desk %d, %s, %s, %s = %s"):format(
      deskIndex,
      socketName,
      moduleId,
      channel,
      tostring(value)
    )
  )
end
```

Wenn ein Kanal oder Modul entfernt wurde, ist `value` gleich `nil`.

Strukturänderungen erzeugen ein getrenntes Ereignis:

```lua
local _, state, memberCount, revision =
  os.pullEvent("cc_aeroworks_console_changed")
```

## Beispiel: Eingang auf Display spiegeln

```lua
local desks = aeroworks.getDesks()
assert(#desks > 0, "Leerer Multiblock")

local target = desks[#desks]

while true do
  local _, _, _, _, _, _, value =
    os.pullEvent("cc_aeroworks_console_input")

  if value == nil then
    aeroworks.clearDisplay(target.id, "big")
  elseif type(value) == "number" then
    aeroworks.setDisplayNumber(target.id, "big", value, false)
  end
end
```

## Fehlerfälle

- Mehr als 64 verbundene Pulte: Zugriff wird abgelehnt.
- Nicht vollständig geladener Multiblock: Zugriff wird abgelehnt.
- Unbekannte Desk-ID oder ungültiger Index: Lua-Fehler.
- Ungültiger Socket: Lua-Fehler.
- Mehrere eingebettete Computer: direkte API wird abgelehnt.
