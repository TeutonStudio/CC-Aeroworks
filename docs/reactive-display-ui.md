# Reactive Display UI

CC-Aeroworks provides a retained, event-driven UI runtime for large Desk Displays. It is intended for cockpit MFDs, telemetry dashboards and other displays whose configured pixel resolution may be much larger than the default raster.

The old `ControlDesk` text/pixel methods remain available as the **Raw Display API**. Reactive UI is an additional embedded-computer-only layer.

## Goals

The runtime follows four stages:

```text
state/source change
       |
       v
Composition -> Layout -> Draw -> Tile commit/sync
```

A read is registered against the phase in which it occurs. Invalidating a draw-only dependency therefore does not force a new composition or layout pass.

Runtime frames are transient. They are not written into the Aeroworks module custom name and they are not persisted to disk on every update.

## Two script levels

A large display can store two independent paths:

- **controller**: receives pointer events before the retained UI tree. It can perform global input routing or return another app path.
- **boot program**: initial Reactive UI application loaded by the supervisor.

Configure them from the display module screen or through the `ControlDesk` adapter:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]

desk.setDisplayApplication(
  "big",
  "/ui/controller.lua",
  "/ui/home.lua"
)
```

The compatibility method

```lua
desk.setDisplayTouchScript("big", "/ui/controller.lua")
```

now changes only the controller and preserves the boot program.

## Starting configured displays

The mod mounts its bundled Lua resources read-only at `/cc_aeroworks` on the embedded computer.

Start every configured boot application with:

```lua
shell.run("/cc_aeroworks/display_runtime.lua")
```

The program calls `ui.supervise()`. One supervisor owns all configured displays of that embedded computer instead of creating one polling loop per display.

## Minimal app

```lua
local ui = require("cc_aeroworks.ui")

return ui.app(function()
  ui.Column({ padding = 2, gap = 1 }, function()
    ui.Text("FUEL")
    ui.ProgressBar({ value = 0.75, height = 3 })
  end)
end)
```

An application file returns `ui.app(...)`. Screen modules loaded through `ui.Route` may also return a component function.

## State

```lua
local selected = ui.state("selected", "main")

ui.Button({
  text = "AUX",
  onTap = function() selected.set("aux") end,
})
```

`state.get()` records a dependency on the current restart scope. `state.set(value)` does nothing when the value is unchanged.

### Derived state

```lua
local percent = ui.derived("fuelPercent", function()
  local fuel = ui.telemetry.get("fuel")
  return math.floor(fuel.value.percent + 0.5)
end)
```

The derived calculation owns its own dependency scope. If the raw tank value changes while the rounded percentage remains equal, dependent UI scopes are not invalidated.

## Reactive telemetry

Use the UI wrapper when a telemetry read should become an automatic UI dependency:

```lua
local fuel = ui.telemetry.get("fuel")
```

The raw module remains available for ordinary programs:

```lua
local telemetry = require("cc_aeroworks.telemetry")
```

Telemetry change events are translated into source invalidations by the supervisor. The dependency key for a known source is `telemetry:<stable-source-id>`; collection reads also use the `telemetry:*` dependency.

For future non-telemetry data providers, `ui.source(key, getter)` exposes the same dependency mechanism. A producer/event handler must invalidate the same key when its value changes.

## Components

Current built-ins:

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

Reusable components are restart scopes:

```lua
local Gauge = ui.component("Gauge", function(props)
  ui.Column({ gap = 1 }, function()
    ui.Text(props.label)
    ui.ProgressBar({
      width = props.width,
      value = props.value,
    })
  end)
end)
```

Call it while composing:

```lua
Gauge({ label = "FUEL", width = 60, value = 0.75 })
```

## Modifiers

A modifier is immutable from the caller's perspective:

```lua
local modifier = ui.modifier()
  :fillWidth()
  :height(5)
  :padding(1)
```

Available modifier methods:

```text
width(value)
height(value)
fillWidth()
fillHeight()
fillMaxSize()
padding(value)
weight(value)
key(value)
```

Direct properties such as `width`, `height`, `padding`, `gap`, `fillWidth` and `fillHeight` may also be supplied in the component props table.

## Layout phases

The runtime records three restart phases:

### Composition

Controls which retained nodes exist.

```lua
local page = selected.get()
if page == "fuel" then
  FuelScreen()
else
  HomeScreen()
end
```

A state read here may require recomposing this component.

### Layout

Controls dimensions and positions.

```lua
ui.Text({
  text = function() return percent.get() .. "%" end,
})
```

If no explicit width is supplied, text content is read during layout because the 3x5 glyph length determines width.

### Draw

Controls pixels without changing bounds.

```lua
ui.ProgressBar({
  width = 100,
  height = 5,
  value = function() return percent.get() / 100 end,
})
```

The progress value is evaluated while drawing. A change can therefore redraw only the component bounds.

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

The navigator exposes:

```text
get()
go(route)
replace(route)
back()
```

## Pointer input

The existing large-display touch resolver still provides one-based pixel coordinates in the configured display resolution. Reactive UI performs hit testing against retained layout bounds.

Interactive nodes may define:

```text
onTap(event)
onDoubleTap(event)
onPointer(event)
```

A controller script can intercept the same event before UI hit testing:

```lua
return {
  onTap = function(event, runtime)
    if event.x <= 10 then
      return "/ui/home.lua"
    end
  end,
}
```

Returning a non-empty string replaces the active app on that display without restarting the computer.

## Lazy lists

`ui.LazyColumn` composes only the visible item window plus a small one-item buffer:

```lua
ui.LazyColumn({
  items = cargo,
  itemHeight = 6,
  viewportHeight = 60,
  key = function(item) return item.id end,
}, function(item)
  ui.Text(item.name)
end)
```

Stable keys preserve retained node identity as the visible range changes.

## Canvas escape hatch

```lua
ui.Canvas({
  width = 64,
  height = 32,
  draw = function(canvas)
    canvas.fillRect(1, 1, 20, 3, true)
    canvas.setPixel(30, 8, true)
    canvas.text("NAV", 1, 8)
  end,
})
```

The callback receives display-local operations but still participates in the node's draw restart scope and dirty-rectangle commit.

## Tiled framebuffer

Runtime frames use sparse `64x64` monochrome tiles. Each row of a tile is one packed 64-bit value.

Consequences:

- blank tiles consume no frame storage,
- a transaction compares changed tile data with the previous snapshot,
- visually identical writes produce no patch,
- changed tiles are sent only to players tracking the desk chunk,
- a new tracking client receives the current runtime snapshot through the normal block-entity client packet,
- runtime frames are not persisted into world NBT.

A draw-only invalidation clears and rerasterizes the affected node rectangle, not the complete configured display.

## Native API

`cc_aeroworks.ui_native` is the implementation layer used by `cc_aeroworks.ui`. Applications normally should not call it directly.

It supplies:

```text
beginScope(id, phase)
endScope()
read(dependency)
changed(dependency)
forgetScope(id)
consumeInvalidations()
getDependencies()
listDisplays()
beginFrame(deskId, socket)
clearFrame(deskId, socket)
```

`beginFrame` returns a transactional frame handle:

```text
getSize()
clear()
setPixel(x, y, enabled)
fillRect(x, y, width, height, enabled)
commit()
```

## Diagnostics

```lua
local ui = require("cc_aeroworks.ui")
for dependency, scopes in pairs(ui.dependencies()) do
  print(dependency, #scopes)
end
```

This reports the dependency graph known to the native runtime, including the phase of each restart scope.

## Raw API compatibility

The following APIs remain valid and are deliberately separate from transient Reactive UI frames:

```text
desk.setDisplayText
desk.setDisplayNumber
desk.setDisplayPixel
desk.setDisplayPixels
desk.clearDisplay
desk.clearDisplayPixels
```

When a Reactive UI runtime frame exists for a socket, the runtime frame is rendered. Clearing that runtime frame reveals the existing persistent/raw display state again.
