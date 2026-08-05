# Computer-Steuerungspulte

Computer-Steuerungspulte verbinden ein Aeroworks-Steuerungspult dauerhaft mit einem CC:Tweaked-Computer. Dadurch kann ein einziges Pult den gesamten verbundenen Multiblock ohne externes Peripheral oder Modem verwalten.

## Varianten

| Eingesetzter Computer | Ergebnis |
|---|---|
| Normaler CC:Tweaked-Computer | Computer-Steuerungspult |
| Erweiterter CC:Tweaked-Computer | Erweitertes Computer-Steuerungspult |

Die Computerfamilie bestimmt Terminal, Farbe und Kapazität. Beide Varianten verwenden dieselbe Multiblock- und Ponder-Logik.

## Crafting

Lege genau diese beiden Gegenstände gemeinsam in ein beliebiges Craftingfeld mit mindestens zwei Plätzen:

1. ein normales Aeroworks-Steuerungspult
2. einen normalen oder erweiterten CC:Tweaked-Computer

Die Position im Craftingfeld spielt keine Rolle. Zusätzliche Gegenstände machen das Rezept ungültig.

Beim Crafting bleiben die Komponenten beider Eingaben erhalten:

- Aeroworks-Modul- und Steuerungspultdaten
- Computer-ID und zugehöriges Dateisystem
- Computerlabel
- Terminalgröße
- Speicherkapazität

## Bedienung

| Aktion | Eingabe |
|---|---|
| Terminal öffnen | Schleichen + Rechtsklick mit leerer Haupthand auf ein beliebiges geladenes Multiblockmitglied |
| Montierte Steuerung bedienen | normaler Rechtsklick auf das Modul |
| Steuerungseinstellungen öffnen | Create-Schraubenschlüssel + Rechtsklick auf eine horizontale Pultseite |
| Pult drehen | Schraubenschlüssel auf Ober- oder Unterseite |
| Ponder öffnen | W über einem der beiden Computerpultitems halten |

Weitere Details stehen unter [[Bedienung]].

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

Bei der direkten API ist kein Peripheral beteiligt. `peripheral.find`, `peripheral.wrap` und ein Modem sind nicht erforderlich.

## Pulte adressieren

Fast alle direkten Methoden erwarten zuerst ein Pult. Zulässig sind:

- der aktuelle 1-basierte Netzwerkindex, zum Beispiel `1`
- die stabile Desk-ID aus `getDesks()`

Desk-IDs sind für dauerhafte Programme besser geeignet. Der Index kann sich nach Umbauten ändern.

```lua
local desks = aeroworks.getDesks()
local target = desks[#desks]

aeroworks.setDisplayNumber(target.id, "big", 42, true)
```

## Multiblocks

Ein Multiblock entsteht aus unmittelbar links und rechts angrenzenden, gleich ausgerichteten Steuerungspulten. Unterstützt werden:

- normale Aeroworks-Steuerungspulte
- Computer-Steuerungspulte
- erweiterte Computer-Steuerungspulte

Die Pulte bilden eine lineare Reihe. Das Netzwerk lädt keine fehlenden Chunks nach und ist auf 64 Mitglieder begrenzt.

```lua
local network = aeroworks.getNetwork()
print(network.state, network.memberCount, network.revision)
```

Typische Zustände:

| Zustand | Bedeutung |
|---|---|
| `active` | genau ein eingebetteter Computer verwaltet den Multiblock |
| `none` | kein eingebetteter Computer; externer Peripheral-Zugriff ist möglich |
| `conflict` | mehrere eingebettete Computer wurden gefunden |

## Genau ein eingebetteter Computer

Ein Multiblock benötigt höchstens ein Computer-Steuerungspult. Alle übrigen Pulte bleiben normal. Alternativ kann vollständig auf ein Computer-Steuerungspult verzichtet und ein externer Computer verwendet werden.

Mehrere **externe** Computer am selben Peripheral-Netzwerk bleiben erlaubt. Die Beschränkung betrifft eingebettete Computerpulte, deren Computer-ID und Dateisystem sonst nicht eindeutig zusammengeführt werden könnten.

## Versehentliche Doppelplatzierung

Wird in Survival ein weiteres Computer-Steuerungspult so platziert, dass der vollständig geladene Multiblock bereits einen eingebetteten Computer besitzt:

1. das bereits vorhandene Computer-Steuerungspult bleibt Besitzer,
2. das neu platzierte Pult wird zu einem normalen Aeroworks-Steuerungspult,
3. montierte Module, Displayzustände und Pulteinstellungen bleiben erhalten,
4. der zusätzliche CC:Tweaked-Computer wird als Item ausgeworfen,
5. Computer-ID, Label, Terminalgröße und Speicherkapazität bleiben am ausgeworfenen Computer erhalten.

Im Creative-Modus wird die Platzierung abgebrochen. Dadurch bleibt das kombinierte Item erhalten, ohne eine Computer-ID zu duplizieren.

Die automatische Trennung wird nur für normale Spielerplatzierung in einem vollständig auflösbaren Multiblock ausgeführt. Konflikte aus Altwelten, `/setblock`, Strukturwerkzeugen oder teilweise geladenen Netzwerken bleiben als `conflict` sichtbar und müssen manuell behoben werden.

## Externer Computer als Alternative

Ein gewöhnlicher CC:Tweaked-Computer oder ein Wired Modem muss nur mit einem beliebigen Pult verbunden werden:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

for _, desk in ipairs(console.getDesks()) do
  print(desk.index, desk.id, desk.variant)
end
```

Dafür wird kein Computer-Steuerungspult benötigt. Die vollständige Gegenüberstellung steht in [[API-Schnellreferenz]].

## Ereignisse

Der eingebettete Computer erhält Eingabeereignisse für den gesamten Multiblock:

```lua
while true do
  local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
    os.pullEvent("cc_aeroworks_console_input")

  print(deskIndex, socketName, moduleId, channel, value)
end
```

Wenn ein Kanal oder Modul entfernt wurde, ist `value` gleich `nil`.

Strukturänderungen erzeugen:

```lua
local _, state, memberCount, revision =
  os.pullEvent("cc_aeroworks_console_changed")
```

## Ponder-Erklärung

Beide Computerpultitems besitzen dieselbe Create-Ponder-Szene. Im Inventar oder Rezeptbetrachter **W halten**. Die Szene zeigt Aufbau, Bedienung, Einstellungen, externen Computer und Doppelplatzierung.

## Fehlerfälle

- mehr als 64 verbundene Pulte: Zugriff wird abgelehnt
- nicht vollständig geladener Multiblock: Zugriff wird abgelehnt
- unbekannte Desk-ID oder ungültiger Index: Lua-Fehler
- ungültiger Socket: Lua-Fehler
- bestehender Mehrcomputer-Konflikt: direkte API wird abgelehnt; externes Peripheral bleibt nutzbar
