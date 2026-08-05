# CC-Aeroworks Wiki

CC-Aeroworks verbindet die Steuerungspulte aus Create: Aeroworks mit CC:Tweaked. Die Mod ergänzt eingebettete Computer-Steuerungspulte, programmierbare zwei- und dreistellige Displays und eine kombinierte Maussteuerung für Lever, Joystick und Throttle Quadrants.

## Einstieg

- [[Bedienung]] erklärt Computerterminal, normale Steuerung, Steuerungseinstellungen und Ponder.
- [[Computer-Steuerungspulte]] erklärt Crafting, Multiblocks und den Schutz vor mehreren eingebetteten Computern.
- [[API-Schnellreferenz]] trennt direkte `aeroworks`-API und externes Peripheral.
- [[Programmierbare-Displays]] beschreibt Text-, Zahlen- und Pixelmodus.
- [[Kombinierte-Eingabe]] erklärt Einrichtung und Bedienung des Halte-zu-Steuern-Modus.

## Einen Zugriffsweg wählen

Ein Steuerungspult-Multiblock benötigt genau **einen Steuerungsweg**:

### Eingebetteter Computer

Ein normales oder erweitertes Computer-Steuerungspult verwaltet den gesamten Multiblock. Im Terminal steht die globale API `aeroworks` bereit:

```lua
local desks = aeroworks.getDesks()
assert(#desks > 0, "Kein Steuerungspult gefunden")

aeroworks.setDisplayText(desks[1].id, "big", "123")
```

Dafür sind weder Modem noch `peripheral.find` oder `peripheral.wrap` erforderlich.

### Externer Computer

Alternativ wird ein gewöhnlicher CC:Tweaked-Computer direkt oder über ein Wired Modem mit **einem beliebigen Pult** verbunden:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

for _, desk in ipairs(console.getDesks()) do
  print(desk.index, desk.id, desk.variant)
end
```

Eine einzige Verbindung reicht für den direkt verbundenen Multiblock. Weitere externe Computer dürfen dasselbe Peripheral-Netzwerk beobachten; die Ein-Computer-Regel betrifft eingebettete Computer-Steuerungspulte.

## Bedienung in drei Zeilen

| Aktion | Eingabe |
|---|---|
| Eingebetteten Computer öffnen | Schleichen + Rechtsklick mit leerer Haupthand auf ein beliebiges Pult |
| Montierte Steuerung bedienen | Normaler Rechtsklick auf das Modul |
| Steuerungseinstellungen öffnen | Create-Schraubenschlüssel + Rechtsklick auf eine horizontale Pultseite |

Im Inventar oder Rezeptbetrachter kann über beiden Computer-Steuerungspultvarianten **W gehalten** werden, um die Create-Ponder-Erklärung zu öffnen.

## Multiblock-Grundregeln

Gleich ausgerichtete Steuerungspulte verbinden sich unmittelbar links und rechts zu einem linearen Multiblock. Normale Aeroworks-Pulte und beide Computer-Steuerungspultvarianten dürfen gemischt werden.

- maximal 64 Mitglieder
- keine automatische Chunk-Nachladung
- teilweise geladene Netzwerke werden abgelehnt
- höchstens ein eingebetteter Computer
- ein versehentlich in Survival platziertes zweites Computerpult wird zu einem normalen Pult; der zusätzliche Computer wird mit ID und Label ausgeworfen
- in Creative wird eine konfliktverursachende Platzierung abgebrochen, damit keine Computer-ID dupliziert wird
- Konflikte aus Altwelten, Befehlen oder Strukturwerkzeugen bleiben als diagnostizierbarer Sicherheitszustand erhalten

## Wichtige Begriffe

| Begriff | Bedeutung |
|---|---|
| Desk | Ein einzelnes Steuerungspult im Multiblock |
| Socket | Ein Modulplatz: `left`, `right` oder `big` |
| Desk-ID | Stabile UUID eines Pults; robuster als der Netzwerkindex |
| Netzwerkindex | Aktuelle 1-basierte Position eines Pults im Multiblock |
| Direkte API | Globale `aeroworks`-API des eingebetteten Computers |
| Peripheral API | `cc_aeroworks_control_desk` für externe Computer |

## Projektstatus

CC-Aeroworks befindet sich noch in einer frühen Integrationsphase. Compiler- und statische Prüfungen ersetzen keine vollständigen Ingame-Tests für Rendering, Persistenz, Ponder, bewegte Konstruktionen oder reale Multiblocks.
