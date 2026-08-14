# ControlDesk I/O inventory

CC-Aeroworks is moving from an Aeroworks-only control list toward one shared cockpit I/O model. The model is server-authoritative and intentionally references the existing subsystem owners instead of duplicating their state.

The first implementation stage exposes the inventory through the embedded ComputerControlDesk as the read-only `deskio` API. The graphical overview will consume the same model in a later stage.

## Categories

Every object belongs to one of four categories:

- `control`: normal Aeroworks input modules such as yokes, levers and throttle quadrants;
- `display`: normal and radar display modules, including their current display binding;
- `information`: Create Display Link telemetry sources received by the ComputerControlDesk;
- `output`: user-defined ComputerControlDesk wire/redstone channels.

Display modules are never reported as controls merely because their pseudo-finger Combined input is implemented using Aeroworks ControlChannels internally.

## Lua API

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

`getSnapshot()` also returns category counts:

```lua
{
  state = "active",
  active = true,
  revision = 42,
  counts = {
    control = 3,
    display = 2,
    information = 4,
    output = 3
  },
  objects = { ... }
}
```

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

This means renaming a telemetry alias or wire channel does not turn it into a different I/O object.

## Display bindings

Display configuration now has two independent axes:

- `content`: who supplies what is visible on the display;
- `input`: how pointer/touch events are routed.

The currently implemented content sources are `default` and `radar_source`. The currently implemented input bindings are `raw` and `lua_handler`.

Existing saves using the old one-of `radar_source` / `lua_handler` NBT format are migrated when read. Lua callers using the old top-level `type`, `source` or `path` fields continue to receive those fields when the binding only uses one non-default axis. New callers should inspect the nested `content` and `input` objects.

This separation is the prerequisite for a later real `script` content source. A script content source must not replace or overload the touch-handler setting.

## Next stage

The next implementation stage will add a synchronized client snapshot and replace the Aeroworks-only overview path when non-control I/O objects are present. That UI will group the same inventory into Controls, Displays, Information and Outputs instead of manufacturing fake Aeroworks modules for Display Links or wire channels.
