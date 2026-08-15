# Reactive Display UI

Reactive Display UI ist die deklarative, ereignisgetriebene Oberfläche für das große CC-Aeroworks-Pultdisplay. Sie ergänzt die direkte Raw Display API und ist für MFDs, Menüs und Live-Anzeigen gedacht, deren Pixelauflösung serverseitig beliebig konfiguriert werden kann.

## Start

```lua
local ui = require("cc_aeroworks.ui")
```

Eine App gibt `ui.app(...)` zurück:

```lua
local ui = require("cc_aeroworks.ui")

return ui.app(function()
  ui.Column({ padding = 2, gap = 1 }, function()
    ui.Text("FUEL")
    ui.ProgressBar({ width = 80, height = 5, value = 0.75 })
  end)
end)
```

## Controller und Boot-Programm

```lua
desk.setDisplayApplication(
  "big",
  "/ui/controller.lua",
  "/ui/home.lua"
)
```

- Controller: verarbeitet Pointer/Touch vor dem UI-Baum und kann eine andere App auswählen.
- Boot-Programm: initiale Reactive-UI-App.

Alle konfigurierten Displays eines eingebetteten Computers starten gemeinsam über:

```lua
shell.run("/cc_aeroworks/display_runtime.lua")
```

## State und Datenquellen

```lua
local percent = ui.derived("fuelPercent", function()
  local fuel = ui.telemetry.get("fuel")
  return math.floor(fuel.value.percent + 0.5)
end)
```

`ui.telemetry.get` registriert die gelesene Source automatisch als Abhängigkeit des aktiven Restart-Scopes. `ui.derived` gibt Änderungen nur weiter, wenn sich sein Ergebnis wirklich verändert.

## Drei Restart-Phasen

| Phase | Aufgabe |
|---|---|
| Composition | UI-Knoten erzeugen/entfernen |
| Layout | Größen und Positionen berechnen |
| Draw | Pixel des betroffenen Bereichs erzeugen |

State-Reads werden der Phase zugeordnet, in der sie stattfinden. Eine reine Draw-Änderung löst daher nicht automatisch neue Composition oder neues Layout aus.

## Tiled Runtime-Framebuffer

Reactive Frames werden transient als sparse `64x64`-Bit-Tiles gespeichert. Geänderte Draw-Bounds werden partiell neu gerastert, identische Tile-Ergebnisse verworfen und nur tatsächlich geänderte Tiles an Clients übertragen.

Der persistente Raw-Displayzustand bleibt darunter erhalten und wird wieder sichtbar, wenn der Runtime-Frame entfernt wird.

## Komponenten

```text
ui.Box
ui.Row
ui.Column
ui.Spacer
ui.Text
ui.ProgressBar
ui.Button
ui.Canvas
ui.LazyColumn
```

Eigene Komponenten:

```lua
local Gauge = ui.component("Gauge", function(props)
  ui.Text(props.label)
  ui.ProgressBar({ width = props.width, value = props.value })
end)
```

## Navigation

```lua
local nav = ui.navigator("main", "home")

ui.Button({
  text = "FUEL",
  onTap = function() nav.go("fuel") end,
})

ui.Route(nav, {
  home = HomeScreen,
  fuel = "/ui/fuel.lua",
})
```

## Diagnose

```lua
for dependency, scopes in pairs(ui.dependencies()) do
  print(dependency, #scopes)
end
```

Damit wird sichtbar, welche Datenquelle welche Composition-, Layout- oder Draw-Scopes invalidieren kann.

## Details

- [[Programmierbare-Displays]] beschreibt Raw API, Displaykonfiguration und das Zusammenspiel beider Modi.
- [[API-Schnellreferenz]] listet die öffentliche `ui`-API.
- `docs/reactive-display-ui.md` enthält den vollständigen technischen Vertrag und weitere Beispiele.
