# Reactive Display UI

CC-Aeroworks large Desk Displays can run retained, dependency-tracked Lua UIs on the embedded `ComputerControlDesk` computer.

The runtime is conceptually similar to Jetpack Compose:

```text
source/state change
      -> dependency invalidation
      -> composition/layout/draw restart scope
      -> dirty rectangle
      -> changed 64x64 framebuffer tiles only
      -> tracking clients
```

The raw `ControlDesk` display methods remain available. A reactive runtime frame temporarily takes rendering ownership of its socket; `clearFrame`/runtime disposal reveals the raw display state again.

## Display application

A large display stores two independent script paths:

- **controller**: optional pointer/input routing script.
- **boot program**: initial retained UI application.

Both can be selected in the module UI. Lua may configure them through a `ControlDesk` handle:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]

desk.setDisplayApplication(
  "big",
  "/ui/controller.lua",
  "/ui/cargo.lua"
)
```

`setDisplayTouchScript` remains as a compatibility alias for changing only the controller path.

The embedded computer automatically starts one non-blocking supervisor for all configured displays. Foreground CraftOS programs continue receiving normal events.

## Minimal application

```lua
local ui = require("cc_aeroworks.ui")

return ui.app(function()
  ui.Column({ padding = 2, gap = 1 }, function()
    ui.Text("CARGO")
    ui.Text({ text = "ready" })
  end)
end)
```

## State and derived state

```lua
local selected = ui.state("selected", "main")
selected.get()        -- records the current restart-scope dependency
selected.set("cargo") -- invalidates only when the value actually changes
```

Derived values own their own dependency scope:

```lua
local percent = ui.derived("fuelPercent", function()
  local fuel = ui.telemetry.get("fuel")
  return math.floor((fuel.value.percent or 0) + 0.5)
end)
```

If raw telemetry changes but the rounded percentage remains equal, downstream UI scopes are not invalidated.

## Automatic reactive sources

A getter read from a retained UI scope defines the dependency. The application does **not** call `render()` and does not need its own polling loop.

### Event-backed telemetry

```lua
local fuel = ui.telemetry.get("fuel")
```

Telemetry has native revisions/events, so no polling observer is required.

### Generic observed getter

```lua
local altitude = ui.observe("flight:altitude", function()
  return readAltitude()
end)

ui.Text({
  text = function()
    return tostring(altitude.get())
  end,
})
```

`ui.source(key, getter)` is an alias of `ui.observe` in the reactive runtime.

For sources without native change events, CC-Aeroworks creates one lazy observer per dependency key. The observer is shared by every display which reads that key and is removed when no retained scope depends on it anymore.

### Inventory peripheral

```lua
local cargo = ui.inventory("12,64,-7/north")

ui.Text({
  width = 32,
  text = function()
    return "IRON " .. cargo.count("minecraft:iron_ingot")
  end,
})
```

Available reads include:

```text
cargo.exists()
cargo.list() / cargo.contents()
cargo.size()
cargo.getItemDetail(slot) / cargo.item(slot)
cargo.getItemLimit(slot) / cargo.limit(slot)
cargo.count("minecraft:iron_ingot")
```

`cargo.count(item)` uses a fine-grained dependency key for that item. Adding a diamond therefore does not redraw a widget which only reads the iron count.

If exactly one inventory peripheral exists, the address may be omitted:

```lua
local cargo = ui.inventory()
```

Information-source IDs beginning with `storage:` are accepted too.

### Fluid storage

```lua
local fuel = ui.fluidStorage("12,64,-7/east")
local tanks = fuel.tanks()
```

### Generic peripheral adapter

For arbitrary read-only peripheral methods:

```lua
local source = ui.reactivePeripheral(
  "some_type",
  "12,64,-7/up",
  { "getStatus", "getValue" }
)

local value = source.getValue("main")
```

Method arguments become part of the dependency key. Only methods explicitly listed as reads are wrapped, so the runtime never decides on its own that a mutating method is safe to poll. Humanity has already produced enough accidental feedback loops.

## Restart phases

The runtime distinguishes three dependency phases:

- `composition`: controls which retained nodes exist.
- `layout`: controls node dimensions/positions.
- `draw`: controls pixels without changing layout.

A draw-only change therefore redraws only the affected node bounds. Composition/layout invalidations recalculate only the necessary retained subtree and resulting dirty bounds.

## Frame transport

Reactive runtime frames are transient and are not saved into world NBT on each update.

- framebuffer storage is sparse 64x64 tiles;
- identical writes produce no patch;
- dirty draws clear and rerasterize only affected rectangles;
- only changed tiles are sent to players tracking the desk chunk;
- a newly tracking client gets the current runtime snapshot in the normal block-entity client packet;
- a deliberately blank runtime frame remains distinct from removing the runtime frame.

## Diagnostics

```lua
local ui = require("cc_aeroworks.ui")
for dependency, scopes in pairs(ui.dependencies()) do
  print(dependency, #scopes)
end
```

The map reports which restart scopes currently depend on each source/state key.
