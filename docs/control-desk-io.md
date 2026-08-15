# ControlDesk I/O overview

CC-Aeroworks uses one shared cockpit I/O model for Aeroworks controls, programmable displays, information sources and ComputerControlDesk wire outputs. The model is server-authoritative and references the existing subsystem owners instead of persisting duplicate runtime state.

## Configuration entry

Sneak + empty-hand right-click on any ControlDesk in a network with an embedded ComputerControlDesk opens the CC-Aeroworks I/O overview. The client sends only the desk position; the server validates reach, interaction permission and the active multiblock before returning a compact snapshot.

Networks without an embedded computer retain Aeroworks' native configuration flow.

The overview has four categories:

- `control`: Aeroworks control modules plus user-defined logical channel groups;
- `display`: normal and radar display modules plus their content/input binding;
- `information`: Display Link telemetry, storage connections and radar-network ingress sources;
- `output`: persistent ComputerControlDesk wire/redstone channels.

Display modules are never reported as controls merely because large-display pointer movement internally reuses Aeroworks ControlChannels.

Physical control rows delegate back to Aeroworks' native `ConsoleScreenOpener`, so its module configuration remains authoritative. User channel groups are logical aliases and are displayed separately from physical modules. Display rows open the CC-Aeroworks display routing editor. Information and output rows are compact status entries.

## Channel hierarchy

Controls are grouped by their mounted control module. The snapshot additionally contains a `channelTree` with three roots:

```text
/modules   automatic, read-only control-module groups
/wires     configured WireChannelBank outputs
/groups    user-created logical groups
```

User groups may contain both control and wire channels. A binding stores a stable target ID, not its visible label. Missing hardware is reported with `available=false`; the binding is retained so temporary unloading or cockpit work does not silently rewrite configuration.

The high-level `channels` Lua API and CraftOS `channels` command expose the same tree. See [`channels.md`](channels.md).

## Information sources

### Display Links

Create Display Link telemetry remains owned by `TelemetryRuntime`. The GUI receives only metadata such as source type, alias, freshness and a short summary. Item/fluid lists are not copied into the GUI packet.

### Storage connections

The active ControlDesk peripheral graph is scanned for peripherals advertising `inventory` and/or `fluid_storage`. Each physical attachment becomes a `storage_connection` information source with stable desk/side identity, peripheral types, position and capability flags.

The overview does not copy inventory contents. Programs continue to access the actual peripheral through the existing hierarchical `peripherals` API.

### Radar networks

Every radar ingress visible through `RadarSourceRegistry` is also exposed as an information source with its Data-Link/desk ingress, radar position when known and current link status. The existing Create: Radars network remains authoritative.

## Compact client snapshot

The GUI does not receive complete telemetry or storage payloads. The client snapshot contains only fields needed for selection, status and configuration:

- stable object identity and category;
- module/member/socket identity for controls and displays;
- grouped control-channel names and current values;
- user-group members and availability;
- display content/input binding and available radar ingress choices;
- information-source type, status and short summary;
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

`getSnapshot()` also returns the logical `channelTree` used by the GUI and `channels` discovery model.

## Stable object IDs

Mounted modules use the stable desk UUID plus socket index:

```text
module:<desk-id>:<socket>
```

Individual control channels use:

```text
control:<desk-id>:<socket>:<module-id>:<channel>
```

Display Link telemetry uses its existing stable telemetry source ID:

```text
telemetry:<source-id>
```

Wire outputs use the persistent wire-channel UUID:

```text
wire:<channel-id>
```

Storage attachments use stable desk identity plus attachment side:

```text
storage:<desk-id>:<side>
```

## Orthogonal display bindings

Display configuration keeps two independent axes:

- `content`: who supplies what is visible on the display;
- `input`: how pointer/touch events are routed.

Content sources are `default`, selected `radar_source`, or `script_source` for the programmable large normal display. Input bindings are `raw` or `lua_handler`.

Large normal displays support both script content and Lua input handlers. Large Radar Displays keep local/remote radar content and independently support a Lua input handler. Small non-interactive displays retain their fixed input behaviour.

Existing saves using the former one-of radar/script-handler binding format migrate when read.

## CraftOS display controller runtime

`rom/autorun/cc_aeroworks_display_runtime.lua` remains responsible for configured display controller modules and touch handlers. It consumes the shared `deskio` model and refreshes when `cc_aeroworks_display_binding_changed` is published.

## Wire outputs

Wire definitions remain owned by `WireChannelBank`; the existing `wires` command still creates, renames and removes virtual hardware channels. Runtime code may use either the low-level `wires` API or the logical `channels.setWire` / `channels.pulseWire` / `channels.resetWire` methods.

Standard Aeroworks controls remain owned by the existing control-authority layer. Logical paths call `channels.override`, `channels.overrideBatch`, `channels.release` and `channels.releaseAll`, which delegate to `ControlOverrideManager` rather than maintaining a second control value.
