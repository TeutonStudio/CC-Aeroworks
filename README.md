# CC-Aeroworks

CC-Aeroworks verbindet Create: Aeroworks Control Desks mit CC:Tweaked. Die Mod ergänzt programmierbare zwei- und dreistellige Displays, frei beschreibbare Pixelraster, kombinierte Maussteuerung, Computer-Steuerungspulte und optional zwei Create:-Radars-Anzeigen.

## Steuerungspult-Multiblocks

Gleich ausgerichtete Steuerungspulte verbinden sich direkt links und rechts zu einem linearen Multiblock. Unterstützt werden normale Aeroworks-Steuerungspulte sowie die normalen und erweiterten Computer-Steuerungspulte aus CC-Aeroworks. Die Auflösung lädt keine Chunks nach und ist auf 64 Mitglieder begrenzt.

Ein externer CC:Tweaked-Computer oder ein Wired Modem muss nur mit **einem** beliebigen Mitglied verbunden werden. Das Peripheral `cc_aeroworks_control_desk` behält seine bisherigen Einzelpultmethoden und ergänzt deskbezogene Multiblockmethoden:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

for _, desk in ipairs(console.getDesks()) do
  print(desk.index, desk.id, desk.variant)
end

console.setDeskDisplayText(3, "big", "123")
```

`desk` ist ein 1-basierter Netzwerkindex oder die stabile Desk-ID.

## Computer-Steuerungspulte

Ein Aeroworks-Steuerungspult kann mit einem normalen oder erweiterten CC:Tweaked-Computer kombiniert werden. Das spezielle Rezept erhält die Aeroworks-Moduldaten und die CC:Tweaked-Computerkomponenten.

Der eingebettete Computer verwaltet den gesamten verbundenen Multiblock direkt. Seine globale API `aeroworks` wird **ohne Peripheral, Modem, `peripheral.find` oder `peripheral.wrap`** aufgerufen:

```lua
local network = aeroworks.getNetwork()

for _, desk in ipairs(aeroworks.getDesks()) do
  print(desk.index, desk.id, desk.variant)
end

print(aeroworks.getSocketCount(1))
aeroworks.setDisplayText(1, "big", "123")
```

Alternativ steht dasselbe Objekt als Modul bereit:

```lua
local aeroworks = require("cc_aeroworks.aeroworks")
```

Mit Schleichen und Rechtsklick bei leerer Haupthand lässt sich das Terminal von jedem Mitglied eines gültigen Einzelcomputer-Multiblocks öffnen. Ein normaler Rechtsklick bedient montierte Steuerungen; ein Create-Schraubenschlüssel auf einer horizontalen Pultseite öffnet die Steuerungseinstellungen.

Pro Multiblock ist höchstens ein eingebetteter Computer vorgesehen. Wird versehentlich ein weiteres normales oder erweitertes Computerpult platziert, bleibt an dieser Stelle ein normales Aeroworks-Pult zurück und der zusätzliche CC:Tweaked-Computer wird mit ID, Label und Komponenten ausgeworfen. Das gilt in Survival und Creative. Konflikte aus Altwelten, Befehlen oder Strukturwerkzeugen bleiben als diagnostizierbarer Sicherheitszustand erhalten.

Beide Computerpultvarianten besitzen eine Create-Ponder-Erklärung, die über die übliche Ponder-Taste `W` geöffnet wird.

## Programmierbare Displays

Die Displays unterstützen Ziffern sowie frei beschreibbare Pixelraster. Standardmäßig besitzt das zweistellige Display `7x5` und das dreistellige `11x5` Pixel. Breite und Höhe beider Größen können in `cc_aeroworks-server.toml` auf jede positive Ganzzahl eingestellt werden; die früheren künstlichen Grenzen von `64x32` wurden entfernt. Die wirksamen Werte stehen in Lua über `getDisplaySize`, `pixelWidth`, `pixelHeight`, `PIXEL_WIDTH` und `PIXEL_HEIGHT` bereit. Das zweistellige Display passt in kleine und große Slots, das dreistellige ausschließlich in große Slots.

Ein normaler CC:Tweaked-Monitor wird unter einer mechanischen Presse zum zweistelligen Display, ein erweiterter Monitor zum dreistelligen Display. Beide Displayitems besitzen eine gemeinsame Ponder-Erklärung für Herstellung und Socket-Anordnung. Zwei kleine Displays bieten zusammen mehr Pixel als ein großes Display, sofern die Standardauflösungen verwendet werden.

Große Raster werden beim Rendern räumlich auf die Modulfläche skaliert. Speicherbedarf und Verarbeitungsaufwand wachsen dennoch mit der Pixelzahl, weil Mathematik leider keinen Respekt vor ambitionierten TOML-Dateien hat.

## Optionale Create:-Radars-Anzeigen

Mit Create: Radars 0.4.4 für Minecraft 1.21.1 werden eine kleine und eine große Radaranzeige freigeschaltet. Die unterstützte Laufzeit verwendet zusätzlich Create Big Cannons 5.11.7 und dessen Pflichtbibliothek Ritchie's Projectile Library 2.1.2. Create: Radars und Create Big Cannons bleiben optionale CC-Aeroworks-Abhängigkeiten.

Die kleine Variante passt in kleine und große Slots, die große nur in den großen Slot. Beide verwenden dieselben konfigurierten Rastergrößen wie die entsprechenden programmierbaren Displays.

Zum Verbinden wird der Create:-Radars-Data-Link-Gegenstand zuerst auf einen verbundenen Radar-Monitor und danach auf eine freie Seite des Steuerungspults mit eingesetzter Radaranzeige rechtsgeklickt. CC-Aeroworks platziert dort den originalen Data-Link-Block, richtet seine Quellseite auf das Pult und setzt den Monitorcontroller als Ziel. Schleichen und Rechtsklick löscht eine begonnene Auswahl. Die übrigen Data-Link-Verbindungsarten von Create: Radars bleiben unverändert.

Der Monitor liefert Radarzentrum, Reichweite, Kontakte und ausgewähltes Ziel. Ohne frische Verbindung zeigt das Display ein `X`. Beide Radaritems besitzen eine eigene Ponder-Erklärung, die den funktionierenden Monitor-zuerst-Ablauf zeigt.

`./gradlew runClient` lädt Create: Radars, Create Big Cannons und Ritchie's Projectile Library automatisch als lokale Runtime-Abhängigkeiten. Details und Testfälle stehen in [`docs/create-radars-integration.md`](docs/create-radars-integration.md).

## Kombinierte Eingabe

Für Lever, Joystick und Throttle Quadrants kann im Aeroworks-Modulbildschirm der Input Type `Kombiniert` gewählt werden. Anschließend wird im mittleren Eingabefeld die Aktivierungstaste erfasst und beim Steuern gehalten.

Desk-Sockets heißen in Lua `left`, `right` und `big`; kompatible Indizes sind `0`, `1` und `2`.

## Entwicklungsstand

Das Projekt ist eine frühe Integrationsversion. Build-, Repository- und Testinfrastruktur sind vorhanden, die vollständige interaktive Laufzeitmatrix ist jedoch noch nicht ausgeführt. Der absichtlich blockierte Baselinebericht liegt unter [`docs/test-results/baseline-1.0.md`](docs/test-results/baseline-1.0.md). Ein Compilerlauf ersetzt weiterhin keine Prüfung von Rendering, Persistenz, Sable, Create: Radars oder realen Multiblocks.

## Entwicklungsumgebung

- Minecraft 1.21.1, NeoForge 21.1.228 und Java 21
- Kotlin 2.2.20 mit KotlinForForge NeoForge 5.11.0
- Create 6.0.10 mit Ponder API 1.0.82
- Aeronautics/Aeroworks 1.3.0
- CC:Tweaked API-Baseline 1.119.0; Metadatenbereich bis vor 1.121
- Create: Radars 0.4.4-1.21.1 optional
- Create Big Cannons 5.11.7 optional
- Ritchie's Projectile Library 2.1.2 als CBC-Laufzeitbibliothek

## Frischer Clone

Der eingecheckte Bootstrap benötigt Java 21. Repositorydateien ohne Fremd-JARs prüfen:

```bash
python3 tools/verify-repository.py
python3 tools/verify-guide.py
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

## Tests und CI

Die unterstützten Profile und Release-Gates stehen in [`docs/runtime-test-matrix.md`](docs/runtime-test-matrix.md). Interaktive Basistests stehen in [`docs/manual-test-plan.md`](docs/manual-test-plan.md), zusätzliche Multiblockfälle in [`docs/multiblock-test-plan.md`](docs/multiblock-test-plan.md) und die Bedienungs-, Display- und Ponder-Fälle in [`docs/computer-desk-guide-test-plan.md`](docs/computer-desk-guide-test-plan.md).

`.github/workflows/verify.yml` prüft bei Push und Pull Request den Repositoryvertrag sowie Buch-, Sprach-, Wiki-, Ponder-, Radar-, Data-Link-, Rezept- und Itemmodell-Ressourcen. Der geschützte Vollbuild benötigt rechtmäßig bereitgestellte Mod-JARs über die Repository-Secrets `MOD_DEPENDENCY_URL` und `MOD_DEPENDENCY_SHA256`.

## Dokumentation und Beispiele

- [Peripheral- und direkte API](docs/cc-peripheral-api.md)
- [Einführung zur Programmierung](docs/peripheral-programming.md)
- [Konfiguration](docs/configuration.md)
- [Create: Radars integration](docs/create-radars-integration.md)
- [Runtime-Testmatrix](docs/runtime-test-matrix.md)
- [Manueller Testplan](docs/manual-test-plan.md)
- [Computerpult-, Display- und Ponder-Testplan](docs/computer-desk-guide-test-plan.md)
- [Lua-Beispiele](examples/cc/)

Repository: `TeutonStudio/CC-Aeroworks`
