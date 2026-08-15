# Logical channel registry

The embedded ComputerControlDesk exposes a high-level `channels` API in addition to the existing low-level `controls` and `wires` APIs.

The registry does not own physical values. Aeroworks remains authoritative for mounted control modules and `ControlOverrideManager` remains authoritative for HARD computer control. `WireChannelBank` remains authoritative for virtual Drive By Wire outputs. The registry supplies stable discovery paths and user-defined logical aliases across both systems.

## Hierarchy

```text
/
├── modules/
│   ├── desk_1_big_yoke/
│   │   ├── turn
│   │   └── pitch
│   └── desk_2_right_throttle_quadrant/
│       ├── red
│       ├── amber
│       ├── green
│       └── blue
├── wires/
│   ├── landing_gear
│   └── engine_start
└── groups/
    └── flight/
        ├── roll      -> control:...
        ├── pitch     -> control:...
        └── gear      -> wire:...
```

Module groups are generated automatically and cannot be deleted. User groups store symbolic names that refer to stable channel IDs.

A control-channel ID uses the stable desk UUID, socket, module ID and channel name:

```text
control:<desk-uuid>:<socket>:<module-id>:<channel>
```

A wire-channel ID uses the persistent WireChannelBank UUID:

```text
wire:<wire-uuid>
```

Renaming a wire therefore does not break a user group. If a referenced physical control is temporarily missing, the binding remains in the group with `available=false` instead of being silently deleted.

## Discovery API

```lua
local channels = require("cc_aeroworks.channels")

for _, node in ipairs(channels.ls("/")) do
  print(node.name, node.path, node.nodeType)
end

for _, node in ipairs(channels.ls("/groups/flight")) do
  print(node.name, node.channelKind, node.value, node.available)
end

local pitch = channels.stat("/groups/flight/pitch")
print(pitch.id, pitch.value, pitch.overridden)
print(channels.read("/groups/flight/pitch"))
```

`ls(path)` returns child nodes. `stat(pathOrId)` resolves one group or channel. `read(pathOrId)` returns the current numeric value of a resolved channel.

## User groups

```lua
channels.createGroup("flight")
channels.bind("flight", "pitch", "/modules/desk_1_big_yoke/pitch")
channels.bind("flight", "roll", "/modules/desk_1_big_yoke/turn")
channels.bind("flight", "gear", "/wires/landing_gear")
```

A binding stores the target's stable ID rather than the visible path. This allows wire renames and keeps physical cockpit addressing separate from program-facing names.

Management methods:

```text
createGroup(name)
renameGroup(groupNameOrId, newName)
removeGroup(groupNameOrId)
bind(groupNameOrId, alias, channelPathOrId)
unbind(groupNameOrId, alias)
```

Group and alias names use lowercase letters, digits, `_` and `-`, beginning with a letter. A ComputerControlDesk supports up to 32 user groups with 64 bindings per group.

Definitions are stored in the ComputerControlDesk block entity's persistent data. They survive world saves. They do not duplicate runtime values or override state.

## Wire output through logical paths

Wire operations require a target whose `channelKind` is `wire`:

```lua
channels.setWire("/groups/engine/starter", 15)
channels.pulseWire("/groups/engine/starter", 10, 15)
channels.resetWire("/groups/engine/starter")
```

These methods delegate to the existing `WireChannelBank`; its value range, failsafe state, pulse semantics and Drive By Wire backend remain unchanged.

## Standard-control override through logical paths

Taking computer authority remains explicit:

```lua
channels.override("/groups/flight/pitch", -4)
channels.release("/groups/flight/pitch")
```

Coupled controls should use a batch:

```lua
channels.overrideBatch({
  { channel = "/groups/flight/roll", value = rollCommand },
  { channel = "/groups/flight/pitch", value = pitchCommand },
})
```

These calls delegate to `ControlOverrideManager`. HARD authority, visible module movement, ownership checks, automatic lifecycle release and existing override events therefore stay identical to the low-level `controls` API.

There is deliberately no generic `channels.set()` because writing a redstone output and taking HARD flight-control authority are materially different operations.

## CraftOS command

The embedded ComputerControlDesk installs the `channels` alias when the API is present:

```text
channels ls /
channels ls /modules
channels ls /groups/flight
channels stat /groups/flight/pitch
channels read /groups/flight/pitch
channels group add flight
channels group rename flight primary_flight
channels group remove primary_flight
channels bind flight pitch /modules/desk_1_big_yoke/pitch
channels unbind flight pitch
```

## Information-source relationship

Channel grouping is independent from the information-source browser. The I/O overview now reports these information sources:

- Create Display Link telemetry;
- storage connections discovered from inventory/fluid peripherals attached to the ControlDesk network;
- radar network ingress endpoints;
- displays remain their own configuration category.

Storage rows are metadata-only. Inventory/fluid contents stay in their owning peripheral/API and are not copied into the GUI snapshot.
