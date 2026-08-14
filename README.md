# CC-Aeroworks

CC-Aeroworks verbindet Create: Aeroworks Control Desks mit CC:Tweaked. Die Mod ergänzt adressierbare Pultadapter, ein netzwerkweites Peripheral-Verzeichnis für eingebettete Computer, Create-Display-Link-Telemetrie, programmierbare Displays, kombinierte Maussteuerung und optionale Create:-Radars-Anzeigen.

## Pultnetzwerke

Gleich ausgerichtete Steuerungspulte verbinden sich unmittelbar links und rechts zu einem linearen Netzwerk. Unterstützt werden normale Aeroworks-Steuerungspulte sowie normale und erweiterte Computer-Steuerungspulte aus CC-Aeroworks.

Jedes Pult bleibt ein eigenes Peripheral vom Typ `ControlDesk`. Es besitzt seine eigene Position, stabile Desk-ID, Module, Displays und benachbarten Geräte. Die Netzwerkauflösung lädt keine Chunks nach und ist auf 64 vollständig geladene Pulte begrenzt.

Ein externer CC:Tweaked-Computer oder ein Wired Modem verwendet den direkt verbundenen lokalen Adapter:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein Steuerungspult verbunden")

for _, module in ipairs(desk.getModules()) do
  print(module.socketName, module.id)
end

desk.setDisplayText("big", "123")
```

Die zusätzlichen Typnamen `control_desk`, `cc_aeroworks:control_desk` und `cc_aeroworks_control_desk` bleiben für die lokale Peripheral-Erkennung verfügbar. Netzwerkweite `getDesk...`-Methoden gehören nicht zum neuen Vertrag.

## Computer-Steuerungspulte

Ein Aeroworks-Steuerungspult kann mit einem normalen oder erweiterten CC:Tweaked-Computer kombiniert werden. Das Rezept erhält Aeroworks-Moduldaten und CC:Tweaked-Computerkomponenten.

Der eingebettete Computer darf an jeder Position der Pultreihe stehen. Er stellt die globale API `peripherals` bereit und indexiert:

- alle Pulte des Multiblocks,
- alle Module und Displays über Desk-Handles,
- alle CC:Tweaked-Peripherals an den sechs Seiten jedes Pults,
- Primärtypen und zusätzliche Peripheral-Typen,
- Pultposition, Anschlussseite und Zielposition jedes Geräts.

```lua
local network = peripherals.getNetwork()
print(network.state, network.deskCount, network.peripheralCount)

local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
desk.setDisplayText("big", "123")
```

`ControlDesk` liefert immer eine nach `x,y,z` adressierte Tabelle aller Pulte.

Für Inspektion und die Terminalansicht steht zusätzlich ein hierarchischer Snapshot bereit. Jeder Desk ist ein Root-Eintrag; seine direkt angrenzenden CC:Tweaked-Geräte liegen unter `peripherals` und sind nach Anschlussseite indiziert:

```lua
local tree = peripherals.getTree()
local desk = tree["12,64,-7"]
local monitor = desk.peripherals.north
print(desk.x, desk.y, desk.z, monitor.type)
monitor.handle.setTextScale(0.5)
```

Auf eingebetteten ComputerControlDesks zeigt der CraftOS-Befehl `peripherals` diese Baumansicht. Normale CC:Tweaked-Computer behalten das originale flache Programm.

### Eindeutige Peripheral-Suche

Kommt eine Peripheral-Gattung im gesamten Pultnetz genau einmal vor, liefert `peripherals.find` direkt ihr Methoden-Handle:

```lua
local modem = peripherals.find("endermodem")
assert(modem, "Kein EnderModem vorhanden")
modem.open(42)
```

Großschreibung, Unterstriche und kompakte Schreibweisen werden normalisiert. So kann ein gemeldeter Typ wie `advanced_peripherals:ender_modem` auch über `ender_modem` oder `endermodem` gefunden werden.

Bei mehreren Treffern liefert `find` eine nach Pultposition und Anschlussseite adressierte Tabelle:

```lua
for address, modem in pairs(peripherals.find("endermodem")) do
  print(address)
end
```

`peripherals.findAll(type)` liefert unabhängig von der Trefferzahl immer eine Tabelle. Das ist für Programme sinnvoll, die stets iterieren müssen, denn offenbar war eine einzige Rückgabeform zu langweilig.

Weitere Methoden:

```lua
peripherals.wrap(12, 64, -7)
peripherals.wrap({ x = 12, y = 64, z = -7 })
peripherals.getTree()
peripherals.getTypes()
peripherals.refresh()
```

Die Geräte-Handles delegieren echte CC:Tweaked-Methoden einschließlich Attach-/Detach-Lifecycle, Events, Mounts und Main-Thread-Begrenzung. Sie sind keine Tabellenattrappen mit optimistischem Namen.

Mit Schleichen und Rechtsklick bei leerer Haupthand lässt sich das Terminal von jedem Mitglied eines gültigen Einzelcomputer-Netzwerks öffnen. Ein normaler Rechtsklick bedient montierte Steuerungen; ein Create-Schraubenschlüssel auf einer horizontalen Pultseite öffnet die Steuerungseinstellungen.

Pro Netzwerk ist höchstens ein eingebetteter Computer vorgesehen. Wird versehentlich ein weiteres Computerpult platziert, bleibt dort ein normales Aeroworks-Pult zurück und der zusätzliche CC:Tweaked-Computer wird mit ID, Label und Komponenten ausgeworfen. Konflikte aus Altwelten, Befehlen oder Strukturwerkzeugen bleiben diagnostizierbar und sperren den globalen Graphzugriff.

## Create-Display-Link-Telemetrie

Der eingebettete Computer stellt zusätzlich die globale API `telemetry` bereit. Ein Create Display Link kann direkt auf einen `ComputerControlDesk` zeigen und dessen Create-Quelle als strukturierten Messwert einspeisen.

```text
Tank / Lager
    |
Threshold Switch / Smart Observer
    |
Display Link
    |
ComputerControlDesk
    |
telemetry
```

Strukturiert unterstützt werden `fill_level`, Item Count/List und Fluid Amount/List. CC-Aeroworks liest dafür die Create-Messwerte und Behaviours direkt; formatierte Texte wie `50%` werden nicht wieder in Zahlen zurückgeparst.

```lua
local fuel = telemetry.get("fuel")
if fuel then
  print(fuel.value.current, fuel.value.maximum, fuel.value.percent)
end
```

Mehrere Display Links dürfen denselben Computer als Ziel verwenden. Jede Quelle besitzt eine stabile ID, eine Revision und Frischeinformationen; eigene Aliase werden persistent am Endpoint gespeichert. Auf Sable basiert die Identität auf Sublevel-UUID und lokaler Linkposition, sodass ein fahrendes oder rotierendes Fahrzeug nicht ständig neue Sensoren erfindet.

Mit optionalem Create: Simulated kann ein Docking Connector selbst Telemetrie-Endpunkt eines separaten Sable-Moduls sein. Der Fahrzeugcomputer findet alle Docks seines eigenen Sublevels und kann nach dem Verriegeln die Quellen des gegenüberliegenden Connectors abfragen:

```lua
local dock = telemetry.getDock("left_cargo")
if dock and dock.getInfo().locked then
  local remoteFuel = dock.getTelemetry("fuel")
  if remoteFuel then print(remoteFuel.value.percent) end
end
```

Ein Remote-Tankpod oder Anhänger benötigt dafür keinen eigenen CC:Tweaked-Computer. Seine Create-Sensoren und Display Links enden am Remote-Docking-Connector. Item-, Fluid- und Energiepuffer des Connectors werden über `getTransferBuffers()` separat als Diagnosewerte geführt und niemals als tatsächlicher Cargo-/Tankfüllstand ausgegeben.

Details:

- [`docs/telemetry.md`](docs/telemetry.md)
- [`docs/docking-telemetry.md`](docs/docking-telemetry.md)
- [`docs/telemetry-test-plan.md`](docs/telemetry-test-plan.md)
- [`examples/cc/telemetry-dashboard.lua`](examples/cc/telemetry-dashboard.lua)

## Programmierbare Displays

Die Displays unterstützen Text, Zahlen und frei beschreibbare Pixelraster. Standardmäßig besitzt das kleine Pultdisplay `7x5` und das große Pultdisplay `11x5` Pixel. Breite und Höhe beider Größen können in `cc_aeroworks-server.toml` auf jede positive Ganzzahl eingestellt werden.

Vor Pixelzugriffen muss deshalb die wirksame Auflösung gelesen werden:

```lua
local size = desk.getDisplaySize("big")
print(size.width, size.height)
```

Das kleine Pultdisplay passt in kleine und große Sockets, das große Pultdisplay ausschließlich in den großen Socket. Ein normaler CC:Tweaked-Monitor wird unter einer mechanischen Presse zum kleinen Pultdisplay, ein erweiterter Monitor zum großen Pultdisplay.

Große Raster werden beim Rendern auf die Modulfläche skaliert. Speicherbedarf und Verarbeitungsaufwand wachsen dennoch mit der Pixelzahl, weil auch ein Konfigurationswert irgendwann auf Physik trifft.

## Optionale Create:-Radars-Anzeigen

Mit Create: Radars 0.4.4 für Minecraft 1.21.1 werden eine kleine und eine große Radaranzeige freigeschaltet. Die unterstützte Laufzeit verwendet zusätzlich Create Big Cannons 5.11.7 und Ritchie's Projectile Library 2.1.2. Die Abhängigkeiten bleiben optional für CC-Aeroworks.

Radarquelle, Computer und Anzeige dürfen an verschiedenen Pulten desselben Netzwerks liegen. Der Data-Link-Gegenstand wird zuerst auf einen verbundenen Radar-Monitor und danach auf eine freie Seite eines beliebigen Pults im Zielnetz rechtsgeklickt. CC-Aeroworks platziert den originalen Data-Link-Block und routet dessen Snapshot automatisch zur Radaranzeige.

Für automatisches Routing muss im Netz genau eine Radaranzeige vorhanden sein. Keine Anzeige erzeugt eine fehlende Route; mehrere Anzeigen sind mehrdeutig und werden nicht zufällig ausgewählt. Ein Lua-Programm ist für die eindeutige Standardroute nicht erforderlich.

Alle fünf Ticks werden Radarzentrum, Reichweite, ausgewähltes Ziel und höchstens 256 Tracks übertragen. Nach 20 Ticks ohne Aktualisierung zeigt das Display `X`.

Details stehen in [`docs/create-radars-integration.md`](docs/create-radars-integration.md).

## Ponder-Erklärungen

Die bisherige Sammlung langer Einzelanimationen wurde durch acht lokalisierte Storyboards ersetzt:

- Pultnetz aufbauen,
- Peripherals netzwerkweit finden,
- Netzwerkfehler diagnostizieren,
- Displays herstellen,
- Displays montieren,
- Displays programmieren,
- Radar automatisch routen,
- Create:-Radars-Data-Link als Quelle verwenden.

Alle Erklärtexte liegen auf Deutsch und Englisch vor. Feste Pixelgesamtzahlen, die alte zentrale Multiblock-API und der Data Link als ausschließlich lokales Displaykabel werden nicht mehr erklärt, weil falsche Dokumentation erstaunlicherweise selten hilft.

Create-Telemetrie ist im API-Handbuch, Wiki und Beispielprogramm dokumentiert; die bestehende Ponder-Sammlung bleibt unverändert, damit deren exakt synchronisierter Sprach-/Storyboardvertrag nicht für ein nicht funktional erforderliches Erklärbild aufgeweitet wird.

## Kombinierte Eingabe

Für Lever, Joystick und Throttle Quadrants kann im Aeroworks-Modulbildschirm der Input Type `Kombiniert` gewählt werden. Anschließend wird im mittleren Eingabefeld die Aktivierungstaste erfasst und beim Steuern gehalten.

Desk-Sockets heißen in Lua `left`, `right` und `big`; kompatible Indizes sind `0`, `1` und `2`.

## Entwicklungsumgebung

- Minecraft 1.21.1, NeoForge 21.1.228 und Java 21
- Kotlin 2.2.20 mit KotlinForForge NeoForge 5.11.0
- Create 6.0.10 mit Ponder API 1.0.82
- Aeronautics/Aeroworks 1.3.0
- CC:Tweaked API-Baseline 1.119.0; Metadatenbereich bis vor 1.121
- Create: Simulated 1.3.0 optional für Docking-Telemetrie
- Sable 2.0.1 für Sublevel-Integration
- Create: Radars 0.4.4-1.21.1 optional
- Create Big Cannons 5.11.7 optional
- Ritchie's Projectile Library 2.1.2 als CBC-Laufzeitbibliothek

## Repositoryprüfung

Der eingecheckte Bootstrap benötigt Java 21. Repositorydateien ohne Fremd-JARs prüfen:

```bash
python3 tools/verify-repository.py
python3 tools/verify-guide.py
python3 tools/verify-peripheral-network.py
python3 tools/verify-peripheral-tree.py
python3 tools/verify-telemetry.py
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

Ein alternatives Verzeichnis wird mit `-Pmod_dependency_dir=/pfad/zu/mods` angegeben.

## Dokumentation und Tests

- [Peripheral-Netzwerk und Lua-API](docs/cc-peripheral-api.md)
- [Create-Display-Link-Telemetrie](docs/telemetry.md)
- [Docking-Telemetrie](docs/docking-telemetry.md)
- [Telemetrie-Testplan](docs/telemetry-test-plan.md)
- [Hierarchische Peripheral-Ansicht](docs/peripheral-tree.md)
- [Einführung zur Programmierung](docs/peripheral-programming.md)
- [Konfiguration](docs/configuration.md)
- [Create: Radars integration](docs/create-radars-integration.md)
- [Runtime-Testmatrix](docs/runtime-test-matrix.md)
- [Manueller Testplan](docs/manual-test-plan.md)
- [Multiblock-Testplan](docs/multiblock-test-plan.md)
- [Computerpult-, Display- und Ponder-Testplan](docs/computer-desk-guide-test-plan.md)
- [Lua-Beispiele](examples/cc/)

`.github/workflows/verify.yml` prüft bei Push und Pull Request Repositoryvertrag, Sprachen, Buch, Ponder, Peripheral-Graph, hierarchische Peripheral-Ansicht, Telemetrie/Docking, Radar, Data Link, Rezepte und Itemmodelle. Der geschützte Vollbuild benötigt rechtmäßig bereitgestellte Mod-JARs über die Repository-Secrets `MOD_DEPENDENCY_URL` und `MOD_DEPENDENCY_SHA256`.

Repository: `TeutonStudio/CC-Aeroworks`
