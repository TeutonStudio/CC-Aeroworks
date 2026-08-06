# CC-Aeroworks Wiki

CC-Aeroworks verbindet die Steuerungspulte aus Create: Aeroworks mit CC:Tweaked. Die Mod ergänzt adressierbare Pultadapter, ein netzwerkweites Peripheral-Verzeichnis für eingebettete Computer, programmierbare Displays, kombinierte Maussteuerung und optionale Create:-Radars-Anzeigen.

## Einstieg

- [[Bedienung]] erklärt Terminal, normale Steuerung, Einstellungen und Ponder.
- [[Computer-Steuerungspulte]] erklärt Crafting, Computerposition und Netzwerkregeln.
- [[Peripheral-Netzwerk]] erklärt Desk-Adapter, Typauflösung und Geräteadressen.
- [[API-Schnellreferenz]] listet alle Lua-Methoden und Rückgabeformen.
- [[Programmierbare-Displays]] beschreibt Text-, Zahlen- und Pixelmodus.
- [[Radar-Routing]] erklärt automatische Verbindungen zwischen Data Link und Radaranzeige.
- [[Kombinierte-Eingabe]] erklärt den Halte-zu-Steuern-Modus.

## Zwei Zugriffsebenen

### Eingebetteter Computer

Ein normales oder erweitertes Computer-Steuerungspult stellt die globale API `peripherals` bereit. Es darf an jeder Position der Pultreihe stehen.

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
assert(desk, "Zielpult fehlt")

desk.setDisplayText("big", "123")
```

Ein im gesamten Verbund genau einmal vorkommendes Gerät ist direkt über seinen Typ erreichbar:

```lua
local modem = peripherals.find("endermodem")
assert(modem, "Kein EnderModem vorhanden")
modem.open(42)
```

### Externer Computer

Ein gewöhnlicher CC:Tweaked-Computer oder ein Wired Modem verbindet sich mit jedem Pult als eigenem lokalen `ControlDesk`-Peripheral:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein lokales Steuerungspult verbunden")

print(desk.getInput("left"))
desk.setDisplayText("big", "123")
```

Der lokale Adapter sieht ausschließlich dieses Pult. Mehrere Pulte in einem Wired-Modem-Netz bleiben mehrere normale CC:Tweaked-Peripherals.

## Bedienung in drei Zeilen

| Aktion | Eingabe |
|---|---|
| Eingebetteten Computer öffnen | Schleichen + Rechtsklick mit leerer Haupthand auf ein geladenes Pult |
| Montierte Steuerung bedienen | normaler Rechtsklick auf das Modul |
| Steuerungseinstellungen öffnen | Create-Schraubenschlüssel + Rechtsklick auf eine horizontale Pultseite |

Über Computerpult-, Display- und Radaritem kann im Inventar oder Rezeptbetrachter **W gehalten** werden, um die passenden Ponder-Erklärungen zu öffnen.

## Netzwerkregeln

Gleich ausgerichtete Steuerungspulte verbinden sich unmittelbar links und rechts zu einer linearen Reihe. Normale Aeroworks-Pulte und beide Computerpultvarianten dürfen gemischt werden.

- maximal 64 Mitglieder,
- keine automatische Chunk-Nachladung,
- teilweise geladene Netzwerke werden abgelehnt,
- höchstens ein eingebetteter Computer,
- jeder Desk behält stabile ID, Position, Module, Displays und Anschlussseiten,
- ein versehentlich platziertes zweites Computerpult wird zu einem normalen Pult; der zusätzliche Computer wird mit seinen Daten ausgeworfen,
- Konflikte aus Altwelten, Befehlen oder Strukturwerkzeugen bleiben diagnostizierbar.

## Wichtige Begriffe

| Begriff | Bedeutung |
|---|---|
| Desk | Ein einzelnes adressierbares Steuerungspult |
| Desk-Handle | Lua-Objekt für Module, Displays und lokale Nachbargeräte eines Pults |
| Socket | Ein Modulplatz: `left`, `right` oder `big` |
| Desk-ID | Stabile UUID eines Pults |
| Desk-Adresse | Aktuelle Weltposition als `x,y,z` |
| Peripheral-Adresse | Desk-Adresse plus Anschlussseite, zum Beispiel `12,64,-7/north` |
| `peripherals` | Globale Graph-API des eingebetteten Computers |
| `ControlDesk` | Lokaler Peripheral-Typ jedes einzelnen Pults |

## Projektstatus

Die Repositoryprüfungen validieren API-Vertrag, Übersetzungen, Ponder-Struktur, Radarressourcen, Rezepte und Dokumentation. Ein vollständiger Modbuild und dedizierter Server-Smoke-Test benötigen das rechtmäßig bereitgestellte Abhängigkeitspaket des Projekts.
