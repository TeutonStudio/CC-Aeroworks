# Docking-Telemetrie mit Create: Simulated und Sable

Wenn Create: Simulated 1.3.0 vorhanden ist, kann ein `simulated:docking_connector` als Create-Display-Link-Telemetrie-Endpunkt verwendet werden. Dadurch kann ein eigenständiges Sable-Modul seine Messwerte lokal sammeln und beim Andocken an einen ComputerControlDesk weiterreichen.

Simulated bleibt eine optionale Abhängigkeit. CC-Aeroworks lädt keine Simulated-Klasse direkt im Kern, sondern aktiviert den Adapter nur, wenn `simulated` vorhanden ist.

## Grundprinzip

Ein Display Link soll nicht quer zwischen zwei beweglichen Sable-Sublevels zeigen. Stattdessen endet die Telemetrie auf dem Docking Connector des jeweiligen Moduls:

```text
REMOTE SUBLEVEL

Tank
  |
Threshold Switch
  |
Display Link
  |
Remote Docking Connector
       ||
       || Simulated LOCKED
       ||
Vehicle Docking Connector
  |
ComputerControlDesk
  |
telemetry.getDock(...)
```

Die Sublevelgrenze liegt damit genau an der physischen Dockinggrenze.

Ein Anhänger, Tankpod oder Waffenmodul benötigt für Telemetrie keinen eigenen CC:Tweaked-Computer, kein Wired Modem und kein CC-Kabel. Create-Sensoren, Display Links und ein Docking Connector genügen.

## Erkennung lokaler Docks

Der eingebettete Computer ermittelt sein Sable-Sublevel und scannt ausschließlich dessen geladene Plot-Chunks nach `simulated:docking_connector`. Es wird weder die gesamte Dimension durchsucht noch das bestehende ControlDesk-Peripheral-Netz dafür missbraucht.

Die Scanrate wird mit `telemetry.dockScanIntervalTicks` konfiguriert.

## Lua-API

Zusätzliche Methoden der globalen `telemetry`-API:

- `telemetry.getDocks() -> table`
- `telemetry.getDock(nameOrId) -> dock|nil`
- `telemetry.renameDock(nameOrId, alias) -> table`
- `telemetry.clearDockName(nameOrId) -> table`

`getDocks()` liefert Handles unter ihrem eindeutigen Alias oder, falls kein Alias vorhanden ist, unter der stabilen Dock-ID.

```lua
for key, dock in pairs(telemetry.getDocks()) do
  local info = dock.getInfo()
  print(key, info.state, info.locked)
end
```

### Dock-Handle

Ein Dock-Handle besitzt:

- `dock.getInfo() -> table`
- `dock.listTelemetry() -> table`
- `dock.getTelemetry(nameOrId) -> table|nil`
- `dock.renameTelemetry(nameOrId, alias) -> table`
- `dock.clearTelemetryName(nameOrId) -> table`
- `dock.getTransferBuffers() -> table`

Remote-Telemetrie wird nur über einen verriegelten (`locked`) Connector bereitgestellt.

## Dockstatus

`getInfo()` liefert unter anderem:

```lua
{
  id = "stabile-dock-uuid",
  alias = "left_cargo",
  state = "locked",
  connected = true,
  locked = true,
  extended = true,
  retracted = false,
  subLevelId = "lokale-sable-uuid",
  remote = {
    subLevelId = "remote-sable-uuid",
    name = "Fuel Trailer",
    position = { x = 12, y = 64, z = -7 }
  },
  telemetryAvailable = true
}
```

CC-Aeroworks leitet den Zustand aus den öffentlichen Simulated-Methoden `isLocked`, `isExtended`, `isRetracted`, `hasOtherConnector` und `getOtherConnector` ab. Private Magnet-Maps oder interne Constraint-Tabellen werden nicht angefasst.

Mögliche `state`-Werte:

- `unpowered`
- `retracted`
- `extended`
- `locking`
- `locked`

## Remote-Telemetrie

Auf dem entfernten Modul können mehrere Display Links auf dessen Docking Connector zeigen:

```text
Fuel Threshold ---- Display Link ---+
                                     |
Cargo Observer ---- Display Link ---+--> Remote Dock
                                     |
Ammo Observer ----- Display Link ---+
```

Nach dem Verriegeln:

```lua
local dock = telemetry.getDock("left_cargo")
assert(dock, "Dock fehlt")

local info = dock.getInfo()
assert(info.locked, "Modul ist nicht verriegelt")

local fuel = dock.getTelemetry("fuel")
if fuel then
  print(("Fuel: %.1f%%"):format(fuel.value.percent))
end
```

Die Source-Aliase werden am Remote-Docking-Connector gespeichert. Ein Fuel-Pod kann deshalb seine Quelle dauerhaft `fuel` nennen und behält diese Bedeutung, wenn er später an ein anderes Fahrzeug gekoppelt wird.

## Mehrere Docks

Jeder lokale Docking Connector besitzt eine eigene Namensdomäne für seine Remote-Sources:

```lua
local left = telemetry.getDock("left")
local right = telemetry.getDock("right")

local leftStatus = left and left.getTelemetry("status")
local rightStatus = right and right.getTelemetry("status")
```

Zwei verschiedene Remote-Module dürfen also beide eine Source namens `status` oder `fuel` besitzen, ohne sich gegenseitig zu überschreiben.

## Dock-Aliase

Lokale Ports können benannt werden:

```lua
telemetry.renameDock(dockId, "left_cargo")
telemetry.renameDock(otherDockId, "refuel")
```

Entfernen:

```lua
telemetry.clearDockName("left_cargo")
```

Die Aliasdaten liegen persistent am jeweiligen Docking Connector.

## Events

Statusänderungen erzeugen:

```text
cc_aeroworks_dock_changed
```

Argumente:

```text
dockId, state, locked, remoteSubLevelId
```

Remote-Telemetrieänderungen erzeugen:

```text
cc_aeroworks_remote_telemetry_changed
```

Argumente:

```text
dockId, sourceId, action, revision
```

`action` ist `added`, `changed` oder `removed`.

Beim Abkoppeln werden keine Remote-Sources in den lokalen Source-Namensraum kopiert. Das nächste Modul am selben Port beginnt daher mit seinem eigenen Endpoint statt mit einem hübschen Gemisch aus Anhängern vergangener Tage.

## Transferpuffer sind keine Ladungsdaten

Simulated-Docking-Connectoren besitzen eigene Item-, Fluid- und Energieübertragungspuffer. `dock.getTransferBuffers()` stellt diese nur zur Diagnose separat bereit:

```lua
{
  item = {
    slots = 2,
    occupiedSlots = 1,
    count = 32
  },
  fluid = {
    tanks = 1,
    amount = 500,
    capacity = 1000,
    buckets = 0.5
  },
  energy = {
    stored = 1200,
    capacity = 10000
  }
}
```

Diese Werte sind ausdrücklich nicht der tatsächliche Inhalt eines angeschlossenen Tanks oder Frachtraums. Der reale Ladestatus muss über eine Create-Quelle und einen Display Link auf den Remote-Dock gelangen.

## Beispielaufbau: externer Tank

Remote Fuel Pod:

```text
Create Fluid Tank
      |
Threshold Switch  [Display Source: Fill Level]
      |
Display Link      [Target: Docking Connector]
      |
Simulated Docking Connector
```

Fahrzeug:

```text
ComputerControlDesk
      |
Sable-Sublevelweite Dock Discovery
      |
Simulated Docking Connector
```

Lua:

```lua
local dock = telemetry.getDock("refuel")
if dock and dock.getInfo().locked then
  local fuel = dock.getTelemetry("fuel")
  if fuel then
    print(math.floor(fuel.value.percent + 0.5) .. "%")
  end
end
```

## Fehlende Mods

Ohne Simulated bleibt die lokale Create-Display-Link-Telemetrie vollständig aktiv:

```lua
telemetry.getDocks() -- {}
telemetry.getStatus().simulatedDockingAvailable -- false
```

Es gibt keinen harten Simulated-Klassenimport im Telemetrie-Kern. Die Integration wird erst aktiviert, wenn der Mod geladen und die erwartete Docking-API vorhanden ist.
