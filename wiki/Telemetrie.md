# Telemetrie

[[Deutsch|Telemetrie]] · [[English|English#telemetry-and-docking]]

Ein Pult mit eingebettetem Computer (`ComputerControlDesk`) kann Create-Display-Links als strukturierte Telemetrieeingänge verwenden. Pulte ohne diese Computerrolle behalten ihr normales Create-Display-Zielverhalten. Wähle als Telemetrieziel den Computerträger.

## Lokale Messwerte

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

Unterstützt werden strukturiert:

- `create:fill_level`
- `create:count_items`
- `create:list_items`
- `create:count_fluids`
- `create:list_fluids`

Unbekannte Create-Sources bleiben mit `supported=false` und ihrem `displayText` sichtbar. Zahlen werden nicht aus formatiertem Text zurückgeparst.

## Lua

```lua
local all = telemetry.list()
local fuel = telemetry.get("fuel")
local fills = telemetry.find("fill_level")
local status = telemetry.getStatus()
```

Alias setzen:

```lua
telemetry.rename(sourceId, "fuel")
```

Jede Source besitzt eine stabile ID, `revision`, `lastSeenTick`, `ageTicks` und `stale`. Auf Sable wird die ID aus Sublevel-UUID und lokaler Display-Link-Position gebildet und bleibt dadurch bei Fahrzeugbewegung erhalten.

## Simulated-Docks

Ist Create: Simulated vorhanden, können Display Links auch auf einen Docking Connector zeigen. Der Connector sammelt dann Telemetrie seines eigenen Moduls.

```text
Remote Tank -> Display Link -> Remote Dock
                              ||
                              || LOCKED
                              ||
Vehicle Dock <- ComputerControlDesk
```

Docks des eigenen Sable-Sublevels:

```lua
local docks = telemetry.getDocks()
local dock = telemetry.getDock("left_cargo")
```

Remote-Daten:

```lua
if dock and dock.getInfo().locked then
  local fuel = dock.getTelemetry("fuel")
  if fuel then print(fuel.value.percent) end
end
```

Dock-Handle:

```text
getInfo()
listTelemetry()
getTelemetry(nameOrId)
renameTelemetry(nameOrId, alias)
clearTelemetryName(nameOrId)
getTransferBuffers()
```

`getTransferBuffers()` beschreibt ausschließlich die Item-/Fluid-/Energiepuffer des Simulated Connectors. Es ist nicht der tatsächliche Tank- oder Frachtrauminhalt des Remote-Moduls.

## Events

```text
cc_aeroworks_telemetry_added
cc_aeroworks_telemetry_changed
cc_aeroworks_telemetry_removed
cc_aeroworks_dock_changed
cc_aeroworks_remote_telemetry_changed
```

Identische Display-Link-Refreshs erzeugen kein `changed`-Event.

## Mehr

Ausführliche Entwicklerdokumentation im Repository:

- [Telemetrievertrag](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/telemetry.md)
- [Docking-Telemetrie](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/docking-telemetry.md)
- [Telemetrie-Dashboard](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/examples/cc/telemetry-dashboard.lua)

## Messwert prüfen

Prüfe vor der Verwendung `available`, `supported` und `stale`. `ageTicks` beschreibt das Alter des letzten Refreshs. Eine fehlende oder veraltete Quelle sollte in einem Steuerprogramm nicht als gültiger Nullwert behandelt werden.

Aliase bleiben gespeichert. Die aktuellen Messwerte werden nach dem Neustart durch Display Links neu aufgebaut. Item-/Fluidlisten können mit `truncated=true` gekürzt sein; Gesamtmengen und `entryCount` bleiben vollständig.

Auf gewöhnlichen Create-Contraptions bietet `telemetry.get("contraption")` mobile Positions-, Rotations-, Geschwindigkeits- und Lagerdaten. Siehe [[Bewegte Konstruktionen|Bewegte-Konstruktionen]]. Grenzwerte und Aktualisierungsintervalle stehen unter [[Konfiguration]].

![Die reale Create-Display-Link-Quelle Diamantenlager ist im Quellenreiter bereit.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-verified-telemetry.png)

![Laufende Telemetrie über zwei tatsächlich verriegelte Docking Connector: Das entfernte Lager enthält 64 Items.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-dock-telemetry.png)
