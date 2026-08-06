# Computer-Steuerungspulte

Computer-Steuerungspulte verbinden ein Aeroworks-Steuerungspult dauerhaft mit einem CC:Tweaked-Computer. Der Computer verwaltet keinen monolithischen Multiblock-Peripheral mehr. Stattdessen indexiert er jedes verbundene Pult als eigenen Adapter und fasst alle benachbarten Peripherals in einem gemeinsamen Graphen zusammen.

## Varianten

| Eingesetzter Computer | Ergebnis |
|---|---|
| Normaler CC:Tweaked-Computer | Computer-Steuerungspult |
| Erweiterter CC:Tweaked-Computer | Erweitertes Computer-Steuerungspult |

Die Computerfamilie bestimmt Terminal, Farbe und Kapazität. Beide Varianten verwenden dieselbe Netzwerk- und Ponder-Logik.

## Crafting und Datenerhalt

Lege ein normales Aeroworks-Steuerungspult und einen normalen oder erweiterten CC:Tweaked-Computer gemeinsam in ein Craftingfeld. Zusätzliche Gegenstände machen das Rezept ungültig.

Erhalten bleiben:

- Aeroworks-Module und Pulteinstellungen,
- Computer-ID und Dateisystem,
- Computerlabel,
- Terminalgröße,
- Speicherkapazität.

## Bedienung

| Aktion | Eingabe |
|---|---|
| Terminal öffnen | Schleichen + Rechtsklick mit leerer Haupthand auf ein geladenes Pult des Netzwerks |
| Montierte Steuerung bedienen | normaler Rechtsklick auf das Modul |
| Steuerungseinstellungen öffnen | Create-Schraubenschlüssel + Rechtsklick auf eine horizontale Pultseite |
| Pult drehen | Schraubenschlüssel auf Ober- oder Unterseite |
| Ponder öffnen | W über einem Computerpultitem halten |

Weitere Details stehen unter [[Bedienung]].

## Pultnetz

Unmittelbar links und rechts angrenzende, gleich ausgerichtete Pulte bilden eine lineare Reihe. Unterstützt werden normale Pulte sowie normale und erweiterte Computerpulte.

Jedes Mitglied behält:

- eine stabile Desk-ID,
- seine aktuelle Weltposition,
- seine Module und Displays,
- Peripherals an seinen sechs Seiten.

Der Computer darf links, mittig oder rechts stehen. Seine Position verändert die erreichbaren Geräte nicht. Das Netzwerk lädt keine Chunks nach und ist auf 64 vollständig geladene Pulte begrenzt.

## Globale `peripherals`-API

Im eingebetteten Computer steht `peripherals` global zur Verfügung:

```lua
local network = peripherals.getNetwork()
print(network.state, network.deskCount, network.peripheralCount)
```

Alternativ:

```lua
local peripherals = require("cc_aeroworks.peripherals")
```

### Pulte adressieren

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]

print(desk.getInfo().id)
desk.setDisplayNumber("big", 42, true)
```

`ControlDesk` liefert immer eine Tabelle nach `x,y,z`. Die Position ist die aktuelle Adresse; die Desk-ID bleibt die stabile Identität.

### Eindeutige Geräte finden

Kommt ein Typ im gesamten Pultnetz genau einmal vor, wird direkt sein Methoden-Handle zurückgegeben:

```lua
local modem = peripherals.find("endermodem")
assert(modem, "Kein EnderModem vorhanden")
modem.open(42)
```

Bei mehreren Treffern liefert `find` eine nach Pultposition und Seite adressierte Tabelle. `findAll(type)` liefert immer eine Tabelle.

```lua
for address, modem in pairs(peripherals.findAll("endermodem")) do
  print(address, modem.getPeripheralInfo().side)
end
```

Typnamen werden ohne Beachtung der Großschreibung und zusätzlich in kompakter Form indexiert. Ein Typ wie `advanced_peripherals:ender_modem` ist daher auch über `ender_modem` und `endermodem` auffindbar.

### Zugriff über Koordinaten

```lua
local desk = peripherals.wrap(12, 64, -7)
local device = peripherals.wrap(12, 64, -8)
```

Mehrdeutige Gerätepositionen können durch einen Typ ergänzt werden.

## Lokaler Adapter für externe Computer

Ein externer CC:Tweaked-Computer oder ein Wired Modem sieht das direkt angeschlossene Pult als lokales `ControlDesk`-Peripheral:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein lokales Pult verbunden")

print(desk.getInput("left"))
desk.setDisplayText("big", "123")
```

Der lokale Adapter greift nur auf dieses Pult zu. Netzwerkweite `getDesk...`-Methoden werden nicht angeboten.

## Genau ein eingebetteter Computer

Ein Pultnetz darf höchstens ein eingebettetes Computer-Steuerungspult enthalten. Mehrere externe Computer an einem Wired-Modem-Netz bleiben davon unberührt.

Wird ein weiteres Computerpult normal platziert, bleibt an dieser Stelle ein normales Aeroworks-Pult zurück und der zusätzliche Computer wird mit ID, Label, Terminalgröße und Speicherkapazität ausgeworfen. Konflikte aus Altwelten, Befehlen oder Strukturwerkzeugen bleiben diagnostizierbar und sperren den globalen Graphzugriff.

## Peripheral-Ereignisse

Nach der ersten Graphauflösung meldet `peripherals.refresh()` neue oder entfernte Geräte:

```lua
local event, address, primaryType = os.pullEvent()

if event == "cc_aeroworks_peripheral_attached" then
  print("Verbunden", address, primaryType)
elseif event == "cc_aeroworks_peripheral_detached" then
  print("Getrennt", address, primaryType)
end
```

Lokale Pulteingaben an einem normalen `ControlDesk` verwenden weiterhin `cc_aeroworks_desk_input`.

## Ponder-Erklärungen

Die Computerpultitems besitzen drei getrennte Storyboards:

1. Pultadapter-Netzwerk und beliebige Computerposition,
2. netzwerkweite Peripheral-Suche mit eindeutiger Rückgabe,
3. Diagnose von Mehrcomputer-, Lade- und Größenfehlern.

Alle Texte liegen auf Deutsch und Englisch vor.

## Fehlerfälle

- mehr als 64 verbundene Pulte: globaler Zugriff wird abgelehnt,
- nicht vollständig geladenes Netzwerk: globaler Zugriff wird abgelehnt,
- mehrere eingebettete Computer: globaler Zugriff wird abgelehnt,
- unbekannte Koordinate oder fehlender Typ: `nil`,
- mehrere Geräte an derselben Position ohne Typ: Lua-Fehler,
- ungültiger Socket: Lua-Fehler.

Die vollständige Methodenübersicht steht in [[API-Schnellreferenz]].
