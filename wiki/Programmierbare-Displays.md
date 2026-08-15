# Programmierbare Displays

CC-Aeroworks ergänzt zwei programmierbare Displaymodule für Aeroworks-Steuerungspulte. Für einfache Instrumente bleibt die direkte **Raw Display API** bestehen. Das große Display kann zusätzlich als reaktive, touchfähige Anwendung mit einer Compose-artigen UI-Runtime betrieben werden.

## Displaytypen

| Display | Passende Sockets | Zeichen | Standardraster | Reactive UI |
|---|---|---:|---:|---|
| Zweistelliges Display | `left`, `right`, `big` | 2 | `7x5` | nein |
| Dreistelliges Display | nur `big` | 3 | `11x5` | ja |

Die Rastergröße ist serverseitig konfigurierbar und besitzt keine kleine künstliche Obergrenze. Programme müssen die wirksame Breite und Höhe lesen und dürfen die Standardwerte nicht fest voraussetzen.

## Raw Display API

Am eingebetteten Computer:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
```

An einem externen Computer:

```lua
local desk = peripheral.find("ControlDesk")
```

### Text und Zahlen

```lua
desk.setDisplayText("left", "-7")
desk.setDisplayText("big", "123")
desk.setDisplayNumber("big", 42, true)
```

### Pixel

Pixelkoordinaten beginnen bei `(1,1)` links oben:

```lua
local size = desk.getDisplaySize("big")
print(size.width, size.height)

desk.setDisplayPixel("big", 1, 1, true)
local enabled = desk.getDisplayPixel("big", 1, 1)
```

`setDisplayPixels` erwartet exakt `size.height` Strings aus `0` und `1`, jeweils genau `size.width` Zeichen breit.

Die Raw API speichert den aktuellen Text-/Pixelzustand weiterhin am Displaymodul und eignet sich für kleine, selten aktualisierte Instrumente.

## Reactive UI für das große Display

Für MFDs, Menüs, Tank-/Cargoanzeigen und andere datengetriebene Oberflächen sollte der eingebettete Computer die neue Bibliothek verwenden:

```lua
local ui = require("cc_aeroworks.ui")
```

Eine App beschreibt deklarativ den gewünschten Zustand:

```lua
local ui = require("cc_aeroworks.ui")

local percent = ui.derived("fuelPercent", function()
  local fuel = ui.telemetry.get("fuel")
  return math.floor(fuel.value.percent + 0.5)
end)

return ui.app(function()
  ui.Column({ padding = 2, gap = 1 }, function()
    ui.Text("FUEL")
    ui.ProgressBar({
      width = 80,
      height = 5,
      value = function() return percent.get() / 100 end,
    })
    ui.Text({
      width = 16,
      text = function() return percent.get() .. "%" end,
    })
  end)
end)
```

Die Runtime merkt sich, welcher UI-Scope welchen State beziehungsweise welche Datenquelle gelesen hat. Eine Änderung invalidiert dadurch nur abhängige Composition-, Layout- oder Draw-Scopes.

## Controller und Boot-Programm

Das große Display besitzt zwei getrennte Skriptebenen:

- **Controller**: verarbeitet Touch/Pointer vor dem UI-Baum und kann eine andere App auswählen.
- **Boot-Programm**: Reactive-UI-App, die beim Start des Display-Supervisors geladen wird.

Beides kann im Modulmenü des großen Displays oder per Lua gesetzt werden:

```lua
desk.setDisplayApplication(
  "big",
  "/ui/controller.lua",
  "/ui/home.lua"
)
```

Einzeln:

```lua
desk.setDisplayController("big", "/ui/controller.lua")
desk.setDisplayBootProgram("big", "/ui/home.lua")
```

Der alte Name bleibt kompatibel:

```lua
desk.setDisplayTouchScript("big", "/ui/controller.lua")
```

Er ändert nur noch den Controller und lässt ein bereits gesetztes Boot-Programm unverändert.

## Supervisor starten

Die Mod mountet ihre Lua-Bibliothek am eingebetteten Computer read-only unter `/cc_aeroworks`.

```lua
shell.run("/cc_aeroworks/display_runtime.lua")
```

Der Supervisor startet alle großen Displays mit konfiguriertem Boot-Programm und verwaltet sie in einem gemeinsamen Event-Loop. Es gibt daher nicht für jedes Instrument eine Polling-Schleife.

## Reaktive Datenquellen

Für Create-Display-Link-Telemetrie wird der UI-Wrapper verwendet:

```lua
local fuel = ui.telemetry.get("fuel")
```

Dadurch wird die Source beim Lesen automatisch als Dependency des aktuellen UI-Scopes registriert. Ändert sich ihre Revision, wird nur dieser abhängige Bereich invalidiert.

`ui.derived` verhindert zusätzlich unnötige Folgeupdates:

```text
74.91 % -> 74.83 % -> gerundet weiterhin 75 -> kein abhängiger Redraw
74.40 % -> gerundet 74 -> abhängige Scopes werden invalidiert
```

## Composition, Layout und Draw

Die UI-Runtime verfolgt Reads getrennt nach Phase:

| Read während | Änderung bewirkt |
|---|---|
| Composition | betroffenen Komponenten-Scope neu zusammensetzen |
| Layout | Position/Größe neu berechnen und Bereich zeichnen |
| Draw | nur betroffenen Zeichenbereich neu rasterisieren |
| `derived` ohne Ergebnisänderung | nichts |

Damit skaliert die Updatearbeit nicht zwangsläufig mit der gesamten konfigurierten Auflösung.

## Komponenten

Aktuell stehen unter anderem bereit:

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

Eigene Restart-Scopes:

```lua
local FuelGauge = ui.component("FuelGauge", function(props)
  ui.Text(props.label)
  ui.ProgressBar({ width = props.width, value = props.value })
end)
```

## Navigation und Touch

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

Interaktive Knoten werden über ihre Layout-Bounds getroffen. Die bestehenden Display-Pointer-Koordinaten verwenden weiterhin die tatsächlich konfigurierte Rasterauflösung.

Ein Controller kann ein Event vor dem UI-Baum behandeln und durch Rückgabe eines Pfads die aktive App ersetzen:

```lua
return {
  onDoubleTap = function(event, runtime)
    return "/ui/home.lua"
  end,
}
```

## Tiled Runtime-Framebuffer

Reactive UI schreibt nicht bei jedem Frame einen riesigen `0`/`1`-String in den Modulnamen. Runtime-Frames werden transient als sparse `64x64`-Tiles gespeichert.

Ein Commit:

1. verändert nur berührte Tiles,
2. vergleicht deren gepackte Bits mit dem bisherigen Snapshot,
3. verwirft visuell identische Änderungen,
4. synchronisiert nur tatsächlich geänderte Tiles an Clients.

Ein neu trackender Client erhält den aktuellen Runtime-Snapshot über das normale BlockEntity-Clientpaket. Der Runtime-Frame wird nicht dauerhaft in Welt-NBT geschrieben.

## Renderpriorität

Wenn für einen Socket ein Reactive-UI-Runtime-Frame existiert, wird dieser dargestellt. Die alte Raw-Anzeige bleibt darunter erhalten. Wird der Runtime-Frame entfernt, erscheint wieder der persistente Raw-Zustand.

## Diagnose

```lua
local ui = require("cc_aeroworks.ui")

for dependency, scopes in pairs(ui.dependencies()) do
  print(dependency, #scopes)
end
```

Damit lässt sich nachvollziehen, welche Datenquelle welche Composition-, Layout- oder Draw-Scopes invalidieren kann.

## Weitere Dokumentation

Die vollständige Architektur und API steht in `docs/reactive-display-ui.md`. Im Ingame-Handbuch gibt es unter **Displays > Reactive UI** Tutorials und unter **API-Referenz > ui** die Methodenübersicht.

## Häufige Fehler

- Rastermaße fest als `7x5` oder `11x5` annehmen,
- für Live-Anzeigen unnötig mit `sleep(0.1)` pollen,
- die rohe `telemetry.get`-Methode innerhalb einer Reactive UI statt `ui.telemetry.get` verwenden,
- veränderliche UI-Listen ohne stabile Keys aufbauen,
- Controller und Boot-Programm als denselben Lebenszyklus behandeln,
- das große Display mit der Raw-Pixel-API ständig komplett neu schreiben, obwohl nur ein kleiner Bereich geändert wurde.
