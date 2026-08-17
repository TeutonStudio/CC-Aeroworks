# CC-Aeroworks

CC-Aeroworks verbindet **Create: Aeroworks Control Desks** mit **CC:Tweaked** und macht aus einer Reihe von Steuerungspulten ein programmierbares Cockpit-Netzwerk. Die Mod ergänzt Computer-Steuerungspulte, adressierbare Desk-Peripherals, eine netzwerkweite Lua-API, programmierbare Displays, Touch-/Draw-Eingabe, Create-Telemetrie, steuerbare Kanäle und optionale Create:-Radars-Anzeigen.

## Kernfunktionen

- lineare Netzwerke aus normalen Aeroworks-Pulten und Computer-Steuerungspulten;
- normaler oder erweiterter CC:Tweaked-Computer direkt im Pult;
- jedes Pult bleibt ein eigenes `ControlDesk`-Peripheral mit eigener Position, Modulen und Displays;
- globale APIs für eingebettete Computer: `peripherals`, `channels`, `controls`, `wires` und `telemetry`;
- kleine und große programmierbare Displaymodule mit Text-, Zahlen- und Pixelmodus;
- Touch-/Draw-Eingabe auf großen Displays über einen frei beweglichen 3D-Zeiger;
- Create Display Links als strukturierte Informationsquellen für Tank-, Lager- und weitere Telemetriedaten;
- optionale Create:-Radars-Kompatibilität mit nativen Data-Link-Endpunkten;
- integriertes Manual/API-Nachschlagewerk und lokalisierte Create-Ponder-Erklärungen.

## Pultnetzwerke

Gleich ausgerichtete Steuerungspulte verbinden sich unmittelbar links und rechts zu einem linearen Netzwerk. Unterstützt werden normale Aeroworks-Steuerungspulte sowie normale und erweiterte Computer-Steuerungspulte aus CC-Aeroworks.

Jedes Pult bleibt ein eigenes Peripheral vom Typ `ControlDesk`. Es besitzt seine eigene Position, stabile Desk-ID, Module, Displays und angrenzenden Geräte. Die Netzwerkauflösung lädt keine Chunks nach und ist auf 64 vollständig geladene Pulte begrenzt.

Ein externer CC:Tweaked-Computer oder ein Wired Modem arbeitet mit dem direkt angeschlossenen lokalen Pult:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein Steuerungspult verbunden")

for _, module in ipairs(desk.getModules()) do
  print(module.socketName, module.id)
end

desk.setDisplayText("big", "123")
```

Zusätzliche Typnamen für die lokale Erkennung sind `control_desk`, `cc_aeroworks:control_desk` und `cc_aeroworks_control_desk`.

## Computer-Steuerungspulte

Ein Aeroworks-Steuerungspult kann mit einem normalen oder erweiterten CC:Tweaked-Computer kombiniert werden. Das Rezept erhält sowohl Aeroworks-Moduldaten als auch CC:Tweaked-Computerkomponenten.

Der eingebettete Computer darf an jeder Position der Pultreihe stehen. Pro gültigem Netzwerk existiert höchstens ein eingebetteter Computer. Wird versehentlich ein zweites Computerpult platziert, bleibt dort ein normales Aeroworks-Pult zurück und der zusätzliche Computer wird mitsamt seinen Daten ausgeworfen.

Mit Schleichen und Rechtsklick bei leerer Haupthand lässt sich das eingebettete Terminal von jedem geladenen Mitglied des gültigen Pultnetzes öffnen. Ein normaler Rechtsklick bedient montierte Steuerobjekte; ein Create-Schraubenschlüssel auf einer horizontalen Pultseite öffnet die Steuerungseinstellungen.

## Lua- und Peripheral-API

Die öffentliche API ist in [`docs/cc-peripheral-api.md`](docs/cc-peripheral-api.md) dokumentiert. Das Ingame-Manual enthält zusätzlich einen aus dem Quellcode gepflegten API-Katalog. `tools/verify-api-reference.py` vergleicht diesen Katalog mit den tatsächlichen öffentlichen Lua-Oberflächen, damit Dokumentation und Code nicht still auseinanderlaufen.

| Oberfläche | Verfügbarkeit | Zweck |
|---|---|---|
| `ControlDesk` | lokales Peripheral | Module, Inputs, Displays und Display-Bindings eines einzelnen Pults |
| `peripherals` | eingebetteter Computer | gesamtes Pultnetz und angrenzende CC:Tweaked-Peripherals |
| `channels` | eingebetteter Computer | bevorzugte High-Level-Steuerung über stabile Pfade |
| `controls` | eingebetteter Computer | native Aeroworks-Control-Overrides im Bereich `-15..15` |
| `wires` | eingebetteter Computer | benutzerdefinierte Redstone-/Drive-By-Wire-Kanäle im Bereich `0..15` |
| `telemetry` | eingebetteter Computer | Create-Informationsquellen und Docking-Telemetrie |
| `display` | Display-Skript | Displayzugriff aus einem gebundenen Skript |
| `touchdisplay` | Display-Skript | Displayzugriff plus Tap-/Draw-Helfer |

Die frühere globale API `aeroworks` und alte netzwerkweite `getDesk...`-Fassaden gehören nicht mehr zum öffentlichen Vertrag.

### `peripherals`

Der eingebettete Computer stellt den vollständigen Pultgraphen bereit:

```lua
local peripherals = require("cc_aeroworks.peripherals")
local network = peripherals.getNetwork()
print(network.state, network.deskCount, network.peripheralCount)

local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
desk.setDisplayText("big", "123")
```

`peripherals.find("ControlDesk")` liefert immer alle Pulte als nach `x,y,z` adressierte Tabelle. Bei normalen Peripheral-Typen liefert `find` bei genau einem Treffer direkt das Handle und bei mehreren Treffern eine Tabelle. Wer immer eine Sammlung benötigt, verwendet `findAll(type)`.

Für Diagnose und Navigation stehen unter anderem `getTree()`, `getTypes()`, `getNetwork()`, `wrap(...)` und `refresh()` zur Verfügung. Geräte-Handles delegieren echte CC:Tweaked-Methoden einschließlich Attach-/Detach-Lifecycle, Events, Mounts und Main-Thread-Aufrufen.

### `channels`, `controls` und `wires`

Neue Cockpit- und Automatisierungsskripte sollten bevorzugt `channels` verwenden:

```lua
local channels = require("cc_aeroworks.channels")

print(channels.read("/groups/flight/roll"))
channels.override("/groups/flight/roll", 7)
channels.setWire("/groups/flight/gear", 15)
channels.pulseWire("/groups/flight/flaps", 10, 15)
channels.release("/groups/flight/roll")
```

`controls` bietet die niedrigere native Aeroworks-Sicht mit signierten Werten `-15..15`. `wires` verwaltet benutzerdefinierte Redstone-/Drive-By-Wire-Ausgänge mit `0..15`. Overrides sind Laufzeitzustand und werden bei ungültigem Netzwerk oder Computer-Aus fail-safe freigegeben.

Details:

- [`docs/wire-channels.md`](docs/wire-channels.md)
- [`docs/control-overrides.md`](docs/control-overrides.md)

## Programmierbare Displays

CC-Aeroworks registriert zwei normale Displaymodule:

- **Two Digit Display:** passt in kleine und große Sockets;
- **Three Digit Display:** passt ausschließlich in den großen Socket und unterstützt interaktive Eingabe.

Ein normaler CC:Tweaked-Monitor wird unter einer mechanischen Presse zum kleinen Display, ein erweiterter Monitor zum großen Display.

Die Displays unterstützen Text, Zahlen und frei beschreibbare Pixelraster. Die Rasterdichte wird über `display.ppb` in **Parts per Block** festgelegt. `16 PPB` entspricht der üblichen Minecraft-Texturdichte; Standard sind `256 PPB`.

Die nutzbare Oberfläche misst:

- klein: `7/16 × 7/16` Block, bei 256 PPB also `112 × 112` Pixel;
- groß: `10/16 × 7/16` Block, bei 256 PPB also `160 × 112` Pixel.

Dadurch bleiben Pixel unabhängig vom Seitenverhältnis physisch quadratisch. Programme sollten die aktuelle Größe trotzdem immer abfragen:

```lua
local size = desk.getDisplaySize("big")
print(size.width, size.height, size.ppb)
```

Pixelzustände werden bitgepackt zusammen mit ihrer Rastergröße gespeichert. Ändert sich `display.ppb`, wird ein inkompatibler alter Rasterzustand als leeres Pixelraster behandelt und nicht versehentlich als Text interpretiert.

### Display-Skripte und Touch

Große Displays können ein Skript als Eingabe-/Touch-Handler binden. Der kombinierte Display-Eingabemodus friert die Kamera ein und bewegt einen halbtransparenten 3D-Zeiger orthogonal über die Displayfläche.

Aktuell erzeugt die Mausbedienung:

- Linksklick: `tap`;
- gehaltene rechte Maustaste: geordnete `draw`-Geste mit Start, Samples und Ende.

`touchdisplay.normalizedPosition(event)` liefert auflösungsunabhängige Koordinaten im Bereich `0..1`. Für Draw stehen zusätzlich unter anderem `drawStart`, `drawDelta`, `drawIdentity` und `drawEnded` bereit.

Details:

- [`docs/display-touch.md`](docs/display-touch.md)
- [`wiki/Programmierbare-Displays.md`](wiki/Programmierbare-Displays.md)
- [`examples/cc/touch-test.lua`](examples/cc/touch-test.lua)

## Create-Display-Link-Telemetrie

Ein Create Display Link kann direkt auf einen `ComputerControlDesk` zeigen und seine Quelle als strukturierten Messwert an `telemetry` übergeben.

```text
Tank / Lager
    |
Create-Informationsquelle
    |
Display Link
    |
ComputerControlDesk
    |
telemetry
```

Strukturiert unterstützt werden unter anderem Füllstände, Item Count/List und Fluid Amount/List. CC-Aeroworks liest Create-Messwerte und Behaviours direkt; formatierte Texte wie `50%` werden nicht wieder in Zahlen zurückgeparst.

```lua
local telemetry = require("cc_aeroworks.telemetry")
local fuel = telemetry.get("fuel")

if fuel then
  print(fuel.value.current, fuel.value.maximum, fuel.value.percent)
end
```

Mehrere Display Links dürfen denselben Computer als Ziel verwenden. Jede Quelle besitzt eine stabile ID, Revision und Frischeinformationen. Auf Sable basiert die Identität auf Sublevel-UUID und lokaler Linkposition.

Mit optionalem Create: Simulated kann ein Docking Connector Telemetrie-Endpunkt eines separaten Sable-Moduls sein. Dadurch kann ein Fahrzeugcomputer nach dem Verriegeln Sensoren eines Anhängers oder Tankpods auslesen, ohne dass das Remote-Modul einen eigenen CC:Tweaked-Computer benötigt.

Details:

- [`docs/telemetry.md`](docs/telemetry.md)
- [`docs/docking-telemetry.md`](docs/docking-telemetry.md)
- [`examples/cc/telemetry-dashboard.lua`](examples/cc/telemetry-dashboard.lua)

## Optionale Create:-Radars-Kompatibilität

Die Radar-Integration ist vollständig unter `de.teutonstudio.ccaeroworks.radarcompat` isoliert und wird nur aktiviert, wenn Create: Radars geladen ist. Damit bleibt der Basismod unabhängig von Radar-Klassen und Radar-Registrierungen.

Mit der unterstützten Create:-Radars-Laufzeit werden eine kleine und eine große Radaranzeige freigeschaltet. Die Anzeige wird als echtes Aeroworks-Pultmodul gerendert und kann einen nativen Create:-Radars-Data-Link-Endpunkt verwenden.

Radarquelle, eingebetteter Computer und Anzeige dürfen an verschiedenen Pulten desselben Pultnetzes liegen. Die konkrete Quellenwahl kann über den Radar-`ControlDesk`-Adapter ausgelesen und gesetzt werden.

Details stehen in [`docs/create-radars-integration.md`](docs/create-radars-integration.md).

## Ponder-Erklärungen

CC-Aeroworks liefert acht lokalisierte Create-Ponder-Storyboards:

1. Pultnetz aufbauen;
2. Peripherals netzwerkweit finden;
3. Netzwerkfehler diagnostizieren;
4. Displays herstellen;
5. Displays montieren;
6. Displays programmieren;
7. Radar/Data Link verbinden;
8. Radar-Endpunkt verwenden und trennen.

Die Ponder-Szenen verwenden für montierte Pultmodule **keine Itemmodelle als Ersatzdarstellung**. Ein Display-Item wird nur dort eingeblendet, wo tatsächlich ein Item hergestellt oder eingesetzt wird. Nach der Montage befindet sich das Modul im echten `ConsoleBlockEntity` des Pults und wird über die normale Aeroworks-Modul-/Display-Pipeline dargestellt.

Die Display-Szenen zeigen außerdem echte Zustandsänderungen am montierten Modul, beispielsweise Text, Pixelmuster und leeren Zustand. Die Socket-Kompatibilität wird visuell demonstriert, einschließlich einer ungültigen großen Anzeige auf einem kleinen Socket.

Basis- und Radar-Ponder bleiben getrennt: Radar-Storyboards liegen im `radarcompat`-Paket und werden nur bei aktiver Create:-Radars-Kompatibilität registriert. Ponder-Texte liegen auf Deutsch und Englisch vor; die Schlüssel und Storyboard-Verträge werden durch `tools/verify-guide.py` geprüft.

## Ingame-Manual und API-Referenz

Das integrierte Manual verwendet eine gemeinsame `MANUAL / API`-Oberfläche. Neben erklärenden Seiten enthält es einen kanonischen API-Katalog mit Verfügbarkeit, Modulnamen und öffentlichen Methoden. Optionale APIs werden nur angezeigt, wenn die zugehörige Mod geladen ist.

Die ausführliche externe Referenz liegt unter [`docs/cc-peripheral-api.md`](docs/cc-peripheral-api.md).

## Entwicklungsumgebung

- Minecraft 1.21.1
- NeoForge 21.1.228+
- Java 21
- Kotlin 2.2.20 / KotlinForForge NeoForge 5.11.0
- Create 6.0.10 mit Ponder API 1.0.82
- Aeronautics/Aeroworks 1.3.0
- CC:Tweaked API-Baseline 1.119.0
- Sable 2.0.1
- Create: Simulated 1.3.0 optional
- Create: Radars 0.4.4-1.21.1 optional
- Create Big Cannons 5.11.7 optional
- Ritchie's Projectile Library 2.1.2 für die unterstützte Radar-Laufzeit

## Repositoryprüfung

Repositorydateien ohne Fremd-JARs lassen sich mit den eingecheckten Prüfwerkzeugen validieren:

```bash
python3 tools/verify-repository.py
python3 tools/verify-guide.py
python3 tools/verify-api-reference.py
python3 tools/verify-display-bindings.py
python3 tools/verify-display-combined-input.py
python3 tools/verify-display-ppb.py
python3 tools/verify-peripheral-network.py
python3 tools/verify-peripheral-tree.py
python3 tools/verify-wire-channels.py
python3 tools/verify-control-overrides.py
python3 tools/verify-telemetry.py
python3 tools/verify-radar-compat-isolation.py
python3 tools/verify-radar.py
python3 tools/verify-radar-link.py
python3 tools/verify-display-recipes.py
./gradlew verifyDependencyManifest
```

Die Fremdmod-JARs werden nicht mitgeliefert und dürfen nicht eingecheckt werden. Versionen und Dateimuster stehen in [`libs/dependencies.json`](libs/dependencies.json), Beschaffungs- und Prüfanweisungen in [`libs/README.md`](libs/README.md).

Nach dem rechtmäßigen Bereitstellen der Baseline-JARs:

```bash
./gradlew verifyModDependencies
./gradlew clean test build
./gradlew runClient
```

Ein alternatives Mod-Verzeichnis kann mit `-Pmod_dependency_dir=/pfad/zu/mods` angegeben werden.

## Dokumentation

- [Lua- und Peripheral-API](docs/cc-peripheral-api.md)
- [Programmierbare Displays](wiki/Programmierbare-Displays.md)
- [Display-Touch](docs/display-touch.md)
- [Wire-Channels](docs/wire-channels.md)
- [Control Overrides](docs/control-overrides.md)
- [Create-Display-Link-Telemetrie](docs/telemetry.md)
- [Docking-Telemetrie](docs/docking-telemetry.md)
- [Hierarchische Peripheral-Ansicht](docs/peripheral-tree.md)
- [Konfiguration](docs/configuration.md)
- [Create: Radars Integration](docs/create-radars-integration.md)
- [Runtime-Testmatrix](docs/runtime-test-matrix.md)
- [Manueller Testplan](docs/manual-test-plan.md)
- [Computerpult-, Display- und Ponder-Testplan](docs/computer-desk-guide-test-plan.md)
- [Lua-Beispiele](examples/cc/)

`.github/workflows/verify.yml` prüft bei Push und Pull Request unter anderem Repositoryvertrag, Sprachen, Manual/Ponder, API-Katalog, Displayverträge, Peripheral-Graph, Wire-Channels, Control-Overrides, Telemetrie, Radar-Kompatibilität, Rezepte und Itemmodelle.

Repository: `TeutonStudio/CC-Aeroworks`
