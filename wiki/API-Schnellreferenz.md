# API-Schnellreferenz

## Zugriffswege

| Eingebetteter Computer | Externer Computer |
|---|---|
| globale `peripherals`-, `controls`-, `telemetry`- und `ui`-APIs | lokales Peripheral `ControlDesk` |
| sieht alle Pulte, Nachbargeräte, lokale Telemetrie, Reactive UI und darf Steuerautorität übernehmen | sieht nur das direkt verbundene Pult |
| kein Modem erforderlich | direkt oder über Wired Modem |
| `cc_aeroworks_*`-Ereignisse | `cc_aeroworks_desk_input` / Display-Events |

Die alte globale `aeroworks`-API und die netzwerkweiten `getDesk...`-Methoden sind nicht Teil des aktuellen Vertrags.

## Lokales `ControlDesk`

```lua
local desk = peripheral.find("ControlDesk")
```

Zusätzliche Typnamen:

```text
control_desk
cc_aeroworks:control_desk
cc_aeroworks_control_desk
```

Methoden:

```text
getInfo()
getSocketCount()
getSockets()
getModules()
getModule(socket)
getInput(socket)
getInputs()
getDisplays()
getDisplay(socket)
setDisplayText(socket, text)
setDisplayNumber(socket, value, zeroPad?)
clearDisplay(socket)
clearDisplays()
getDisplaySize(socket)
getDisplayPixel(socket, x, y)
setDisplayPixel(socket, x, y, enabled)
setDisplayPixels(socket, rows)
clearDisplayPixels(socket)
getRadarSources()
getDisplayBinding(socket)
setRadarSource(socket, sourceId)
setDisplayTouchScript(socket, path) -- Legacy-Alias
setDisplayController(socket, path)
setDisplayBootProgram(socket, path)
setDisplayApplication(socket, controllerPath, bootProgramPath)
clearDisplayBinding(socket)
```

### Zwei Skriptebenen des großen Displays

```lua
desk.setDisplayApplication(
  "big",
  "/ui/controller.lua",
  "/ui/home.lua"
)
```

`controllerPath` verarbeitet Pointer-/Touch-Eingaben. `bootProgramPath` ist die Reactive-UI-Anwendung, die der Supervisor beim Start lädt. `setDisplayTouchScript` bleibt kompatibel und ändert nur den Controller.

## Globale `peripherals`-API

```lua
local peripherals = require("cc_aeroworks.peripherals")
```

Methoden:

```text
find(type)
findAll(type)
wrap(x, y, z, type?)
wrap(position, type?)
getDesks()
getTree()
getTypes()
getNetwork()
refresh()
```

## Rückgabe von `find`

| Trefferzahl | Ergebnis |
|---:|---|
| 0 | `nil` |
| 1 | direktes Methoden-Handle |
| 2 oder mehr | nach Pultposition und Seite adressierte Tabelle |

```lua
local modem = peripherals.find("endermodem")
```

`findAll(type)` liefert immer eine Tabelle.

```lua
for address, modem in pairs(peripherals.findAll("endermodem")) do
  print(address)
end
```

`ControlDesk` ist eine Ausnahme und liefert immer alle Pulte als Tabelle:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
```

## Typnormalisierung

Ein gemeldeter Typ wie

```text
advanced_peripherals:ender_modem
```

kann über folgende Namen gesucht werden:

```text
advanced_peripherals:ender_modem
ender_modem
EnderModem
endermodem
```

Primärtyp und zusätzliche CC:Tweaked-Typen werden indexiert. Bei kollidierenden Kurzformen sollte die vollständige namespaced ID verwendet werden.

## Desk-Handle

Zusätzlich zu den lokalen Modul- und Displaymethoden besitzt ein Desk-Handle:

```text
getPeripherals()
find(type)
findAll(type)
wrap(side)
```

Metadaten:

```lua
{
  id = "stabile-uuid",
  address = "12,64,-7",
  index = 2,
  x = 12,
  y = 64,
  z = -7,
  dimension = "minecraft:overworld",
  computer = false,
  variant = "control_desk",
  facing = "north",
  loaded = true
}
```

## Peripheral-Handle

Das Handle delegiert die echten Methoden des Ziel-Peripherals. Zusätzliche Metadaten stehen über `getPeripheralInfo()` bereit, sofern das Ziel nicht selbst eine Methode dieses Namens definiert.

## Netzwerkstatus

```lua
{
  state = "active",
  revision = 12,
  dimension = "minecraft:overworld",
  deskCount = 4,
  peripheralCount = 3
}
```

Globale Graphzugriffe werden abgelehnt bei mehreren eingebetteten Computern, teilweise geladenen Pultreihen, mehr als 64 Pulten oder einem Computer außerhalb des Besitzerverbunds.

# Globale `controls`-API

Nur der eingebettete Computer besitzt diese API:

```lua
local controls = require("cc_aeroworks.controls")
```

Methoden:

```text
getChannels()
getState(deskId, socket, channel)
override(deskId, socket, channel, value)
overrideBatch(commands)
release(deskId, socket, channel)
releaseAll()
```

Kontinuierliche Fahrzeugkanäle:

```text
aeroworks:lever             -> lever
aeroworks:joystick          -> x, y
aeroworks:wheel             -> wheel
aeroworks:yoke              -> turn, pitch
aeroworks:throttle_quadrant -> red, amber, green, blue
```

Display-Pointer-X/Y und binäre Buttons sind nicht Teil der Override-API. Werte sind ganzzahlig `-15..15`.

# Globale `telemetry`-API

Nur der eingebettete Computer besitzt diese API:

```lua
local telemetry = require("cc_aeroworks.telemetry")
```

Methoden:

```text
list()
get(nameOrId)
find(type)
rename(nameOrId, alias)
clearName(nameOrId)
getStatus()
getDocks()
getDock(nameOrId)
renameDock(nameOrId, alias)
clearDockName(nameOrId)
```

Strukturiert unterstützte Create-Display-Sources:

```text
create:fill_level   -> fill_level
create:count_items  -> item_count
create:list_items   -> item_list
create:count_fluids -> fluid_amount
create:list_fluids  -> fluid_list
```

Beispiel Füllstand:

```lua
local fuel = telemetry.get("fuel")
if fuel then
  print(fuel.value.current, fuel.value.maximum, fuel.value.percent)
end
```

Für Reactive UI denselben Wert über den Wrapper lesen:

```lua
local ui = require("cc_aeroworks.ui")
local fuel = ui.telemetry.get("fuel")
```

Nur der UI-Wrapper registriert den Telemetrie-Read automatisch als Restart-Scope-Abhängigkeit.

# Globale `ui`-API

Nur der eingebettete Computer besitzt die Compose-artige Displaybibliothek:

```lua
local ui = require("cc_aeroworks.ui")
```

Die Mod-Ressourcen werden read-only unter `/cc_aeroworks` gemountet. Alle konfigurierten Boot-Anwendungen starten über:

```lua
shell.run("/cc_aeroworks/display_runtime.lua")
```

## App und State

```text
ui.app(root, options?)
ui.component(name, content)
ui.state(key, initial)
ui.derived(key, calculation, equals?)
ui.source(key, getter)
ui.telemetry.get(nameOrId)
ui.telemetry.list()
ui.telemetry.find(type)
```

## Layout und Draw

```text
ui.modifier()
ui.Box(props, content)
ui.Row(props, content)
ui.Column(props, content)
ui.Spacer(props)
ui.Text(text | props)
ui.ProgressBar(props)
ui.Button(props)
ui.Canvas(props)
ui.LazyColumn(props, itemContent)
ui.key(key, content)
ui.WithConstraints(content)
```

## Navigation und Runtime

```text
ui.navigator(key, initialRoute)
ui.Route(navigator, routes)
ui.mount(display, app)
ui.run(display, app)
ui.listDisplays()
ui.dependencies()
ui.supervise()
```

### Minimale App

```lua
local ui = require("cc_aeroworks.ui")

return ui.app(function()
  ui.Column({ padding = 2, gap = 1 }, function()
    ui.Text("FUEL")
    ui.ProgressBar({ value = 0.75, width = 80, height = 5 })
  end)
end)
```

### Reaktiver Tankwert

```lua
local percent = ui.derived("fuelPercent", function()
  local fuel = ui.telemetry.get("fuel")
  return math.floor(fuel.value.percent + 0.5)
end)

ui.ProgressBar({
  width = 100,
  height = 5,
  value = function() return percent.get() / 100 end,
})
```

State-Reads werden nach Composition, Layout und Draw getrennt verfolgt. Ein Draw-only-Wert kann deshalb nur seine Bounds neu rasterisieren. `derived` propagiert keine Invalidierung, solange sein Ergebnis gleich bleibt.

Runtime-Frames werden transient als sparse `64x64`-Bit-Tiles gehalten. Ein Commit synchronisiert nur geänderte Tiles; visuell identische Tile-Writes erzeugen keinen Patch.

# Ereignisse

Peripheral-Netz:

```text
cc_aeroworks_peripheral_attached(address, primaryType)
cc_aeroworks_peripheral_detached(address, primaryType)
```

Lokales Pult und Display:

```text
cc_aeroworks_desk_input(peripheralName, socket, moduleId, value, channel, socketName)
cc_aeroworks_desk_display_input(peripheralName, socket, socketName, moduleId, action, x, y, width, height, controllerPath)
```

Embedded Display:

```text
cc_aeroworks_console_display_input(deskId, deskIndex, socket, socketName, moduleId, action, x, y, width, height, controllerPath)
cc_aeroworks_ui_invalidated()
```

Control-Authority:

```text
cc_aeroworks_control_override(action, deskId, deskIndex, socket, socketName, channel, value, mode)
cc_aeroworks_control_release(deskId, socket, socketName, channel, reason)
```

Telemetrie:

```text
cc_aeroworks_telemetry_added(sourceId, revision)
cc_aeroworks_telemetry_changed(sourceId, revision)
cc_aeroworks_telemetry_removed(sourceId)
cc_aeroworks_dock_changed(dockId, state, locked, remoteSubLevelId)
cc_aeroworks_remote_telemetry_changed(dockId, sourceId, action, revision)
```

# Displayvertrag

Raw API:

- Text: zwei beziehungsweise drei Zeichen.
- Zahlen: zweistellig `-9..99`, dreistellig `-99..999`.
- Pixelursprung: `(1,1)` links oben.
- Rastergröße: über `getDisplaySize` lesen, nicht fest annehmen.
- `setDisplayPixels`: exakt `height` Strings aus `0` und `1`, jeweils exakt `width` Zeichen.

Reactive UI:

- nur für das große Desk Display,
- Controller und Boot-Programm werden unabhängig gespeichert,
- Runtime-Frame überlagert den persistenten Raw-Zustand,
- Restart-Scopes für Composition, Layout und Draw,
- sparse `64x64` Runtime-Tiles und kompakte Tile-Patches,
- zentrale Verwaltung mehrerer Displays durch `ui.supervise()`.

## Weiterführend

- [[Programmierbare-Displays]]
- [[Telemetrie]]
- `docs/reactive-display-ui.md`
- `docs/telemetry.md`
- `docs/control-overrides.md`
