# Lua API

[[Deutsch|API-Schnellreferenz]] · [[English|API-en]]

![In-game Ponder demonstration of output on a mounted display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-display_programming.png)


This page covers programming in **CC-Aeroworks 1.5.3**. The [[four displays and their recipe images|Displays-en]] have a separate hardware guide. Lua identifiers are the same in every language.

## Choose the access method

| Environment | Access | Scope |
|---|---|---|
| Embedded CC:Tweaked computer in the shared slot | `peripherals`, `channels`, `controls`, `wires`, `telemetry` | the active desk assembly |
| External CC:Tweaked computer, directly attached or connected through wired modems | standard `peripheral` API and local `ControlDesk` peripherals | each attached desk separately |
| CC: Terminals device in the slot | local `terminal` peripheral on an external computer | terminal output and input; no local CraftOS |

The embedded computer does not need a modem to discover devices directly attached to the assembly. A row must be fully loaded, have a single embedded owner and contain no more than 64 physical desk/control segments. The API does not load chunks.

## Discover desks

On the embedded computer:

```lua
local peripherals = require("cc_aeroworks.peripherals")
print(textutils.serialize(peripherals.getNetwork()))

for address, desk in pairs(peripherals.find("ControlDesk")) do
  print(address, desk.getInfo().id)
end
```

`ControlDesk` always returns an address-keyed table. An address such as `12,64,-7` is the current position; the desk UUID is its stable identity. Reacquire handles after topology changes.

For other types, `find(type)` returns `nil` for no match, a direct handle for one match, or an address-keyed table for several matches. `findAll(type)` always returns a table. Type lookup supports namespaced names and normalised short names; use the full type when short names collide.

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

A desk handle also provides `getPeripherals()`, `find(type)`, `findAll(type)` and `wrap(side)` for its own neighbours. Device handles delegate the real peripheral methods. `getPeripheralInfo()` supplies address, type, desk and side metadata unless the device already defines that method.

On an external computer:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "No ControlDesk connected")
print(textutils.serialize(desk.getInfo()))
```

Local type aliases are `control_desk`, `cc_aeroworks:control_desk` and `cc_aeroworks_control_desk`. A local adapter represents one desk, not the whole assembly.

## Common desk methods

These module and display methods exist on the local peripheral and the embedded desk handle:

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
setDisplayResolution(socket, ppb)
getDisplayPixel(socket, x, y)
setDisplayPixel(socket, x, y, enabled)
setDisplayPixelBatch(socket, points, enabled?)
setDisplayPixels(socket, rows)
clearDisplayPixels(socket)
getDisplayColor(socket)
setDisplayColor(socket, colour)
```

Sockets accept `left`, `right`, `big` or zero-based indices `0`, `1`, `2`. Inputs may be a number or a multi-channel table, depending on the module. Inspect `getModules()` and `getInputs()` before assuming a particular shape.

## Write to a display

This runnable embedded program changes the first discovered display:

```lua
local peripherals = require("cc_aeroworks.peripherals")
for _, desk in pairs(peripherals.find("ControlDesk")) do
  for _, panel in ipairs(desk.getDisplays()) do
    local socket = panel.socketName
    local size = desk.getDisplaySize(socket)
    print(size.width, size.height, size.ppb)
    desk.setDisplayText(socket, "42")
    return
  end
end
error("No display mounted")
```

Choose an explicit stable desk ID and socket for a specific instrument. Text/number methods select text mode; pixel methods select raster mode. The last write determines the mode. Reading an empty display socket with `getDisplay` raises a Lua error; use `getDisplays()` to discover available modules.

Pixel coordinates start at `(1,1)` in the upper-left corner. Always read the effective dimensions using `getDisplaySize(socket)`; do not hard-code the default resolution.

```lua
local size = desk.getDisplaySize("big")
local rows = {}
for y = 1, size.height do
  rows[y] = string.rep("1", size.width)
end
desk.setDisplayPixels("big", rows)
```

`setDisplayPixels` requires exactly `height` strings, each containing exactly `width` characters from `0` and `1`. For a few points, use one batch rather than transferring the complete raster:

```lua
desk.setDisplayPixelBatch("big", {
  { x = 1, y = 1 },
  { x = 2, y = 1 },
  { x = 3, y = 1 },
}, true)
```

The batch returns the number of pixels actually changed. On an advanced display, `setDisplayColor(socket, 0x33AAFF)` changes its persistent 24-bit colour; normal displays reject this setter.

`setDisplayResolution` requires `display.useGlobalResolution=false` on the server. PPB must be an integer from 16 to `display.maxIndividualPpb`. Changing the effective resolution clears the pixel image; setting the same resolution preserves it. Removing the module clears its individual setting.

## Bindings and touch handlers

The following configuration methods belong to the **local peripheral**, not the embedded desk handle:

```text
getDisplayBinding(socket)
setDisplayTouchScript(socket, path)
clearDisplayBinding(socket)
setControlScript(socket, path)
```

The embedded computer's module settings provide script selection. Display input requires an advanced display and an advanced computer or advanced CC: Terminals device in the active assembly. Normal or absent slot devices disable touch, including events delivered to external computers.

Save this handler on the embedded computer and select it under **Display Control** in the module settings:

```lua
local touch = require("touchdisplay")
return {
  onTap = function(event)
    local u, v = touch.normalizedPosition(event)
    print(u, v)
  end,
  onDraw = function(event)
    touch.drawStroke(event)
  end,
}
```

Left-click produces `tap`; holding the right mouse button produces an ordered `draw` gesture. Normalised coordinates cover `0..1` across the physical surface, so controls can work independently of PPB. `drawStroke` interpolates the accepted samples and applies a pixel batch.

The embedded autorun runtime dispatches bound handlers. Handlers should return promptly. External computers receive events but do not automatically execute files stored on the embedded owner. An operator terminal does not run scripts locally.

`require("display")` provides event-based output helpers:

```text
resolve(event)
getSize(event)
setResolution(event, ppb)
getColor(event)
setColor(event, colour)
clear(event)
getPixel(event, x, y)
setPixel(event, x, y, enabled)
setPixelBatch(event, points, enabled?)
setPixels(event, rows)
setText(event, text)
setNumber(event, value, zeroPad?)
```

`require("touchdisplay")` inherits these and adds:

```text
isTap(event)
isDraw(event)
position(event)
normalizedPosition(event)
drawStart(event)
drawDelta(event)
drawDirection(event)
drawSpeed(event)
drawSamples(event)
drawStroke(event)
drawEnded(event)
drawIdentity(event)
```

## Logical channels: preferred automation API

Use `require("cc_aeroworks.channels")` for named controls, groups and wire outputs. Configure groups and bindings in the Channels tab first. Directional signals and wire values use **0..15**.

```lua
local channels = require("cc_aeroworks.channels")
print(textutils.serialize(channels.ls("/")))
print(channels.read("/groups/flight/roll_right"))
channels.setWire("/groups/flight/gear", 15)
```

The example paths require matching group bindings. Root paths include `/modules`, `/wires` and `/groups`.

```text
ls(path?)
stat(pathOrId)
read(pathOrId)
setWire(pathOrId, value)
pulseWire(pathOrId, ticks?, value?)
resetWire(pathOrId)
override(pathOrId, value)
overrideBatch(commands)
release(pathOrId)
releaseAll()
```

`override` takes HARD authority over a physical control channel and blocks normal input to that channel. `release` returns authority without forcing the last value to zero. A batch validates all targets before applying them and rejects both directions of the same native axis in one batch. Missing group targets remain discoverable and report `available=false`.

## Native controls and direct wires

`require("cc_aeroworks.controls")` uses native signed Aeroworks values **-15..15**, addressed by desk UUID, socket and channel:

```text
getChannels()
getState(deskId, socket, channel)
override(deskId, socket, channel, value)
overrideBatch(commands)
release(deskId, socket, channel)
releaseAll()
```

Continuous vehicle controls include lever, joystick, wheel, yoke and throttle quadrant. Display-pointer axes and binary buttons are excluded. Global control authority belongs to the embedded owner and is not exposed on external `ControlDesk` adapters.

`require("cc_aeroworks.wires")` addresses configured wire channels directly:

```text
list()
exists(name)
get(name)
set(name, value)
pulse(name, ticks?, value?)
reset(name)
resetAll()
getInfo(name)
getBackend()
isEnabled()
```

Wire values use **0..15**. Adding, removing and renaming channels is configuration through the Channels tab or bundled `wires` shell command, not this runtime API. Definitions and group bindings persist; values, pulses and overrides are transient. Computer shutdown, invalid networks and lifecycle changes reset wire outputs and release control authority. Re-establish required outputs after boot.

## Telemetry

`require("cc_aeroworks.telemetry")` exposes structured Create Display Link sources:

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

Supported source kinds include fill levels, item counts/lists and fluid amounts/lists. Check `available`, `supported`, `stale` and `ageTicks` before using a value. Unknown sources retain `displayText`; formatted strings are not parsed back into numbers. Aliases persist, but live readings are rebuilt after restart.

With optional Create: Simulated, dock handles provide `getInfo`, `listTelemetry`, `getTelemetry`, `renameTelemetry`, `clearTelemetryName` and `getTransferBuffers`. Remote telemetry requires a locked dock. Transfer buffers describe connector buffers, not the complete remote tank or cargo contents.

On ordinary moving Create contraptions, `telemetry.get("contraption")` provides position, rotation, velocity and mounted-storage occupancy. `peripherals.getNetwork().space` distinguishes `world`, `sable` and `contraption`. Mobile peripheral metadata includes entity, local position and projected world position. Only explicitly adapted devices are available in the ordinary mobile runtime.

## Optional integrations

**Create: Radars:** local radar desk peripherals add `getRadarSources()` and `setRadarSource(socket, id)`. An advanced display selects a radar ingress in the same binding selector as Lua scripts. Radar owns the output exclusively while selected; touch remains available when its device requirements are met. A tap on a visible contact selects it through the native Network Filterer flow. Source selection does not create the filterer-to-radar Data Link.

**CC: Terminals 0.1.1:** inserting a terminal adds the `terminal` type and standard terminal methods such as `write`, `getSize`, `setCursorPos`, `blit`, colour and palette methods. Input arrives as `terminal_key`, `terminal_char`, `terminal_paste` and, on advanced terminals, `terminal_mouse_*`. `getInfo().device` identifies the slot device; `programmable` is true only for an embedded computer.

**ContraptionControl:** integrated Create contraption controls provide `getInfo`, `getState`, `getFilter`, `isEnabled`, `setEnabled`, `toggle`, `getFloors`, `getSelectedFloor` and `selectFloor`. Enable/toggle methods apply to `actors` and `doors`; floor methods apply to `elevator`. Methods for the wrong mode raise a Lua error.

**Create: Train Functionality 0.1.47:** `DashboardController` provides `getState`, `getInfo`, `setThrottle`, `setSteer`, `setBrake` and `setStation`. Throttle and steer accept finite values from -1 to 1. Setters are unavailable in frequency mode. Lua commands are neutralised on relevant disconnect/unload/restart transitions.

## Events

| Event family | Purpose |
|---|---|
| `cc_aeroworks_desk_input` | effective input changes on a local desk |
| `cc_aeroworks_console_input` | input changes for the embedded owner and control scripts |
| `cc_aeroworks_desk_display_input`, `cc_aeroworks_console_display_input` | display pointer/tap/draw input |
| `cc_aeroworks_peripheral_attached`, `cc_aeroworks_peripheral_detached` | graph discovery changes |
| `cc_aeroworks_control_override`, `cc_aeroworks_control_release` | control authority changes |
| `cc_aeroworks_telemetry_added`, `_changed`, `_removed` | source lifecycle (each uses the full `cc_aeroworks_telemetry` prefix) |
| `cc_aeroworks_dock_changed`, `cc_aeroworks_remote_telemetry_changed` | dock state and remote telemetry |
| `cc_aeroworks_contraption_control` | actor, door and lift-control changes |

For example, on an external computer:

```lua
local _, name, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_desk_input")
print(name, socketName, channel, value)
```

Control scripts run on value changes, including release and signed-axis changes. A bound script receives an event as its first argument or returns an `onInput` handler. The embedded computer must be running its normal CraftOS event loop.

See the [full API contract](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/cc-peripheral-api.md), [display input details](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/display-touch.md), [channel configuration](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/wire-channels.md) and [executable examples](https://github.com/TeutonStudio/CC-Aeroworks/tree/master/examples/cc) for complete payloads and operational examples.
