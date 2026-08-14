# ControlDesk I/O overview

CC-Aeroworks uses one shared cockpit I/O model for Aeroworks controls, programmable displays, Create Display Link information sources and ComputerControlDesk wire outputs. The model is server-authoritative and references the existing subsystem owners instead of persisting a second copy of their state.

## Configuration entry

Sneak + empty-hand right-click on any ControlDesk in a network with an embedded ComputerControlDesk opens the CC-Aeroworks I/O overview. The client sends only the desk position; the server validates reach, interaction permission and the active multiblock before returning a compact snapshot.

Networks without an embedded computer retain Aeroworks' native configuration flow.

The overview has four categories:

- `control`: normal Aeroworks input modules such as yokes, levers and throttle quadrants;
- `display`: normal and radar display modules plus their content/input binding;
- `information`: Create Display Link telemetry sources received by the ComputerControlDesk;
- `output`: persistent ComputerControlDesk wire/redstone channels.

Display modules are never reported as controls merely because large-display pointer movement internally reuses Aeroworks ControlChannels.

Clicking a control delegates back to Aeroworks' native `ConsoleScreenOpener`, so the existing 0/1/many control behaviour and ModuleScreen configuration remain authoritative. Display rows open the CC-Aeroworks display routing editor. Information and output rows are diagnostic status entries.

## Compact client snapshot

The GUI does not receive the complete telemetry payload. Item/fluid lists can contain many entries and belong to the `telemetry` Lua API, not to a menu packet. The client snapshot contains only fields needed to render and configure the overview:

- stable object identity and category;
- module/member/socket identity for controls and displays;
- current control values;
- display content/input binding and available radar ingress choices;
- information source type, freshness and a short value summary;
- wire value, backend, enabled state and connection count.

The packet is capped at 256 KiB and is refreshed explicitly after configuration changes or with the Refresh button.

## Lua inventory API

Only the embedded ComputerControlDesk exposes the global `deskio` table. The same API is available as `require("cc_aeroworks.deskio")`.

```lua
local snapshot = deskio.getSnapshot()
print(snapshot.state, snapshot.active, snapshot.revision)

for _, object in ipairs(snapshot.objects) do
    print(object.category, object.kind, object.label)
end
```

Methods:

- `deskio.list() -> table`
- `deskio.find(category) -> table`
- `deskio.getSnapshot() -> table`

`getSnapshot()` returns category counts in `counts.control`, `counts.display`, `counts.information` and `counts.output`.

## Stable object IDs

Mounted modules use the stable desk UUID plus socket index:

```text
module:<desk-id>:<socket>
```

Display Link telemetry uses its existing stable telemetry source ID:

```text
telemetry:<source-id>
```

Wire outputs use the persistent wire-channel UUID:

```text
wire:<channel-id>
```

Renaming a telemetry alias or wire channel therefore does not manufacture a different I/O object.

## Orthogonal display bindings

Display configuration has two independent axes:

- `content`: who supplies what is visible on the display;
- `input`: how pointer/touch events are routed.

Content sources:

- `default`: manual/API content for normal displays or the local radar snapshot for Radar Displays;
- `radar_source`: a selected radar ingress from the same desk multiblock;
- `script_source`: a Lua controller module for a normal large Desk Display.

Input bindings:

- `raw`: only normal CC-Aeroworks display/touch events;
- `lua_handler`: automatically dispatch pointer events to a configured Lua module.

Large normal displays support both script content and Lua input handlers. Large Radar Displays keep local/remote radar content and now independently support a Lua input handler. Small non-interactive displays retain their fixed input behaviour.

Existing saves using the old one-of `radar_source` / `lua_handler` NBT format migrate when read. Lua callers using the old top-level `type`, `source` or `path` fields continue to receive those fields when only one non-default axis is active. New code should inspect `binding.content` and `binding.input`.

The local ControlDesk API additionally exposes:

```lua
desk.setDisplayScriptSource("big", "/ui/main.lua")
desk.setDisplayTouchScript("big", "/ui/touch.lua")
desk.setRadarSource("big", sourceId)
```

Passing an empty script path restores `default` content or `raw` input respectively.

## CraftOS display controller runtime

`rom/autorun/cc_aeroworks_display_runtime.lua` is installed only when the embedded `deskio` and `peripherals` APIs exist. It hooks the CraftOS event-pull path instead of blocking the foreground shell or creating one process per display.

A `script_source` file is a Lua module which returns a table. Supported callbacks are:

```lua
return {
  onStart = function(ctx)
    ctx.desk.clearDisplayPixels(ctx.socket)
  end,

  render = function(ctx)
    ctx.desk.setDisplayPixel(ctx.socket, 1, 1, true)
  end,

  onEvent = function(ctx, event, ...)
    -- React to telemetry, timers or other normal CraftOS events.
  end,

  onStop = function(ctx)
  end,
}
```

`ctx` contains at least `id`, `memberId`, `memberIndex`, `socket`, `socketName`, `moduleId`, `binding` and the resolved ControlDesk handle as `desk` when available.

A configured `lua_handler` module may implement `onTap(ctx, event)`, `onDoubleTap(ctx, event)` and/or `onPointer(ctx, event)`. The event table contains the desk/socket/module identifiers, action, 1-based display cell, width and height.

Callbacks are synchronous event callbacks and must not call `os.pullEvent` themselves or perform long blocking work. They may update the display through the supplied Desk handle and react to telemetry events. Binding changes publish `cc_aeroworks_display_binding_changed`, causing the dispatcher to refresh active controller modules.

The runtime also exposes:

```lua
display_sources.list()
display_sources.refresh()
```

for inspection and manual binding refresh.

## Wire outputs in the overview

The output category reads the existing `WireChannelBank`. Channel creation, deletion and rename remain hardware configuration through the bundled `wires` command; the I/O overview intentionally does not duplicate those mutating operations. Runtime programs continue to use `wires.set`, `wires.pulse`, `wires.reset` and related methods.

This keeps the contract explicit: the GUI describes cockpit I/O, the `wires` command defines virtual hardware, and Lua drives the configured outputs.
