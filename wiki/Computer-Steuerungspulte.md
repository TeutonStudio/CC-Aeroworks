# Computer-Steuerungspulte

Normale Aeroworks-Steuereinheiten besitzen einen gemeinsamen Computerslot pro verbundener Reihe. Der Computer verwaltet keinen monolithischen Multiblock-Peripheral mehr. Stattdessen indexiert er jedes verbundene Pult als eigenen Adapter und fasst alle benachbarten Peripherals in einem gemeinsamen Graphen zusammen.

## Varianten

| Eingesetzter Computer | Ergebnis |
|---|---|
| Normaler CC:Tweaked-Computer | normaler Computer im gemeinsamen Slot |
| Erweiterter CC:Tweaked-Computer | erweiterter Computer im gemeinsamen Slot |

Die Computerfamilie bestimmt Terminal, Farbe und Kapazität. Beide Varianten verwenden dieselbe Netzwerk- und Ponder-Logik.

## Einsetzen und Datenerhalt

Öffne mit Schleichen + Rechtsklick die Modulübersicht einer beliebigen Steuereinheit. Setze einen normalen oder erweiterten CC:Tweaked-Computer in den gemeinsamen Slot ein. Spielerinventar und Shift-Klick stehen direkt in der Übersicht zur Verfügung. Das frühere Kombinationsrezept entfällt; alte Blöcke werden beim Laden und alte Items beim Platzieren verlustfrei übernommen.

Erhalten bleiben:

- Aeroworks-Module und Pulteinstellungen,
- Computer-ID und Dateisystem,
- Computerlabel,
- Terminalgröße,
- Speicherkapazität.

## Bedienung

| Aktion | Eingabe |
|---|---|
| Übersicht und Slot öffnen | Schleichen + Rechtsklick auf eine Steuereinheit |
| Terminal öffnen | Computerknopf in der Übersicht |
| Montierte Steuerung bedienen | normaler Rechtsklick auf das Modul |
| Steuerungseinstellungen öffnen | Create-Schraubenschlüssel + Rechtsklick auf eine horizontale Pultseite |
| Pult drehen | Schraubenschlüssel auf Ober- oder Unterseite |
| Ponder öffnen | W über einem normalen Pult oder Computer halten |

Weitere Details stehen unter [[Bedienung]].

## Pultnetz

Eine Reihe wird durch ihre persistente `assemblyId` festgelegt, nicht mehr allein durch Nachbarschaft. Mit einem kompatiblen Block in der Hand erscheinen Create-Platzierungspfeile an den freien Reihenenden. Pfeilklick oder seitliches Platzieren übernimmt Ausrichtung, Familie, Skin und Reihen-ID; Schleichen beginnt eine neue Reihe. Zwei gleich ausgerichtete Reihen dürfen daher direkt aneinandergrenzen. Eine Platzierung zwischen zwei Reihen übernimmt ausschließlich die angeklickte Seite.

Native Create-Zugsteuerungen und Vorrichtungs-Steuerungen übernehmen bei seitlicher Platzierung an einem Bodenpult oder Steuerstand dessen Ausrichtung und Chassis. Das funktioniert in beiden Platzierungsreihenfolgen. Sie dürfen gemischt passende Pultabschnitte verbinden. Ohne verbleibendes passendes Ankerpult fällt ihre zusammenhängende Gruppe auf die normale Create-Darstellung zurück; Deckenpulte integrieren sie nicht.

Frame- und Panel-Material einer Copycat-Reihe werden atomar auf alle geladenen sichtbaren Segmente übertragen. Pro Segment und aktiver Zone wird ein Materialblock benötigt. Bei unvollständig geladener Reihe oder Materialmangel bleibt der Stil unverändert. Jedes Segment speichert seine Zahlung, damit Abbau, Montage, Demontage und Neuladen weder Material verlieren noch vervielfältigen.

Jedes Mitglied behält:

- eine stabile Desk-ID,
- seine aktuelle Weltposition,
- seine Module und Displays,
- Peripherals an seinen sechs Seiten.

Eine integrierte Zugsteuerung zählt als physisches Brückensegment, aber nicht als Pult: Sie besitzt keine Desk-ID, keine Desk-Adresse und keine Module. Modems, andere CC:Tweaked-Peripherals, Create:-Radars-Network-Filterer und Radar-Data-Links an diesem Segment werden trotzdem als Quellen des gemeinsamen Netzes gefunden.

Der Computer darf links, mittig oder rechts stehen. Seine Position verändert die erreichbaren Geräte nicht. Das Netzwerk lädt keine Chunks nach und ist auf 64 vollständig geladene physische Segmente einschließlich Zugsteuerungen begrenzt.

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

Ein Pultnetz darf höchstens einen eingesetzten Computer enthalten. Mehrere externe Computer an einem Wired-Modem-Netz bleiben davon unberührt.

Ein zweiter Computer kann nicht per Shift-Klick eingesetzt werden. Beim Zusammenführen belegter Reihen bleibt bevorzugt ein erweiterter Computer, danach der erste in Reihenfolge; weitere Computer werden mitsamt Daten ausgeworfen.

Beim Abbau des Trägers wandert der Computer ins größere verbleibende Reststück, bei Gleichstand nach links in der bisherigen Reihenordnung. Die letzte Steuereinheit droppt den Computer zusätzlich. Der Transfer verändert keine Module, Desk-IDs oder lokalen Einstellungen der verbleibenden Steuereinheiten. Chunkentladen verschiebt und droppt keinen Computer.

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

Normale Steuereinheiten und CC:Tweaked-Computer besitzen drei getrennte Storyboards:

1. Pultadapter-Netzwerk und beliebige Computerposition,
2. netzwerkweite Peripheral-Suche mit eindeutiger Rückgabe,
3. Diagnose von Mehrcomputer-, Lade- und Größenfehlern.

Alle Texte liegen auf Deutsch und Englisch vor.

## Fehlerfälle

- mehr als 64 verbundene physische Pult- und Zugsteuerungssegmente: globaler Zugriff wird abgelehnt,
- nicht vollständig geladenes Netzwerk: globaler Zugriff wird abgelehnt,
- mehrere eingebettete Computer: globaler Zugriff wird abgelehnt,
- unbekannte Koordinate oder fehlender Typ: `nil`,
- mehrere Geräte an derselben Position ohne Typ: Lua-Fehler,
- ungültiger Socket: Lua-Fehler.

Die vollständige Methodenübersicht steht in [[API-Schnellreferenz]].
