# Erste Schritte

[[Deutsch|Erste-Schritte]] · [[English|English#first-program]]

![Gemeinsamer Geräteslot mit eingesetztem erweitertem Computer und montierten Displaymodulen.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-device-slot.png)


Für den ersten Versuch brauchst du ein normales Aeroworks-Steuerungspult, einen CC:Tweaked-Computer und ein CC-Aeroworks-Display. Ein normaler Computer genügt für Text und Pixel. Für Touch brauchst du ein erweitertes Display und ein erweitertes Slot-Gerät.

## 1. Computer einsetzen

1. Platziere das Pult.
2. Schleiche und rechtsklicke es mit leerer Haupthand.
3. Setze den Computer in den gemeinsamen Geräteslot der Modulübersicht.
4. Öffne mit dem Computerknopf das Terminal und schalte den Computer gegebenenfalls ein.

Für zusätzliche Pulte halte einen kompatiblen Pultblock und nutze die Platzierungspfeile am Reihenende. Schleichen beim Platzieren beginnt eine unabhängige Reihe. Der Computer darf an jeder Position der verbundenen Reihe sitzen.

## 2. Pulte entdecken

Öffne im CraftOS-Terminal mit `edit start.lua` eine Datei, füge folgenden Code ein, speichere und starte sie mit `start`:

```lua
local peripherals = require("cc_aeroworks.peripherals")
local network = peripherals.getNetwork()
print(network.state, network.deskCount)

for address, desk in pairs(peripherals.find("ControlDesk")) do
  print(address, desk.getInfo().id)
end
```

`ControlDesk` liefert immer eine Tabelle nach Adresse. Auch bei genau einem Pult musst du einen Eintrag daraus auswählen. Die Desk-ID bleibt bei Positionswechseln erhalten; die Adresse beschreibt den aktuellen Standort.

## 3. Erstes Display

Ein kleiner Monitor wird unter der mechanischen Presse zu einem kleinen Display. Für die große Variante erweitert ein Deployer es mit einer Eisenplatte (`create:iron_sheet`). Ein erweiterter Monitor erzeugt die erweiterte Displaystufe.

Montiere das Display in einem passenden Socket. Kleine Displays passen in `left`, `right` und `big`, große nur in `big`.

Dieses Programm findet ein Display im gesamten Pultnetz und schreibt `42`. Es verändert dessen Anzeige.

```lua
local peripherals = require("cc_aeroworks.peripherals")

for address, desk in pairs(peripherals.find("ControlDesk")) do
  for _, display in ipairs(desk.getDisplays()) do
    desk.setDisplayNumber(display.socketName, 42, false)
    print("Anzeige gesetzt:", address, display.socketName)
    return
  end
end

error("Kein Display montiert")
```

Kleine und große Displays unterstützen Text-, Zahlen- und Pixelanzeige. Beispiele und Rasterregeln stehen unter [[Programmierbare-Displays]].

## 4. Eingaben ansehen

Montiere ein Steuermodul und untersuche seine Werte:

```lua
for address, desk in pairs(peripherals.find("ControlDesk")) do
  print(address)
  print(textutils.serialize(desk.getModules()))
  print(textutils.serialize(desk.getInputs()))
end
```

Je nach Modul ist ein Eingang ein einzelner Wert oder eine Tabelle mehrerer Kanäle. Native Achsen können negative Werte liefern. Für benannte Richtungen und Automatisierung nutze [[Kanäle und Automatisierung|Kanaele-und-Automatisierung]].

## Alternative: externer Computer

Verbinde einen gewöhnlichen CC:Tweaked-Computer direkt oder über Wired Modems mit dem Pult. Aktiviere die verkabelten Modems wie bei einem normalen CC:Tweaked-Netz.

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein Pult verbunden")
print(textutils.serialize(desk.getInfo()))
```

Dieser Adapter beschreibt genau ein Pult. `peripheral` ist die normale CC:Tweaked-API; `peripherals` mit abschließendem **s** ist die zusätzliche Netzwerk-API des eingebetteten Computers.

## Weiter

- [[Bedienung]] für Einstellungen, Ponder und HUD.
- [[Peripheral-Netzwerk]] für mehrere Geräte und stabile Identitäten.
- [Ausführbare Beispiele](https://github.com/TeutonStudio/CC-Aeroworks/tree/master/examples/cc) für Displays, Eingabemonitor und Telemetrie.
- [[Fehlerbehebung]], wenn der Computer oder ein Gerät nicht gefunden wird.
