# ComputerControlDesk channels and Drive By Wire

The embedded ComputerControlDesk exposes one Channels tab for three related views: immutable physical control channels, user-defined wire channels, and persistent user groups. All user-facing channel signals are ordinary redstone strengths from `0` through `15`.

## Channels tab

The Channels tab is part of the native CC:Tweaked computer screen and is grouped into collapsible sections:

```text
CONTROL MODULES
  Yoke · Desk 1 · big
    left       0/15
    right      8/15
      -> 17, 82, -41  north

WIRE CHANNELS
  landing_gear  0/15
    -> 18, 82, -41  west

USER GROUPS
  flight
    roll_right -> right         8/15
    gear       -> landing_gear  0/15
```

Physical modules are discovered from the active Aeroworks ControlDesk multiblock and are not renameable or deletable. Bidirectional Aeroworks values stay signed internally, but the logical channel layer exposes the same directional redstone outputs as physical Drive By Wire: `left=12,right=0`, `left=0,right=7`, and neutral `left=0,right=0`.

Drive By Wire sink rows include sink coordinates and the connected block side. The GUI reads these from DBW's existing `WireNetworkSink` topology instead of maintaining a second connection database.

## User-defined wire channels

Wire definitions are hardware configuration owned by the ComputerControlDesk. They can be managed from the Channels tab or with the bundled `wires` shell command. The shell command and graphical channel manager are administrative front ends over the same `WireChannelBank`; neither creates a second channel state. The public `wires` API deliberately has no add/remove/rename functions.

Channel names match `[a-z][a-z0-9_-]{0,31}` and a ComputerControlDesk supports at most 32 wire channels. Every definition has a persistent UUID. Renaming keeps that UUID and migrates existing DBW connections. Removing a connected channel drives it to zero and removes its DBW connections.

The legacy runtime API remains available:

```lua
wires.set("landing_gear", 15)
wires.pulse("engine_start", 10)
print(wires.get("landing_gear"))
wires.reset("landing_gear")
wires.resetAll()
```

## Logical user groups

User groups are persistent logical wiring between cockpit hardware and software. A binding has a stable alias and targets either a canonical physical-control ID or persistent wire UUID:

```text
flight
  roll_left  -> control:<desk>:<socket>:<module>:turn:left
  roll_right -> control:<desk>:<socket>:<module>:turn:right
  gear       -> wire:<uuid>
```

A target may appear in more than one group. Temporarily losing a physical module does not delete the binding. The GUI keeps it and shows `MISSING`; it becomes available again when the same stable target returns.

Groups and bindings can be configured in the Channels tab or with the bundled `channels` command:

```text
channels ls /
channels ls /modules
channels ls /groups
channels group add flight
channels bind flight gear wire:<uuid>
channels unbind flight gear
channels group rename flight primary_flight
channels group remove primary_flight
```

## High-level `channels` Lua API

The logical API is additive. Existing `controls` and `wires` APIs remain available as low-level compatibility interfaces.

```lua
channels.ls("/")
channels.ls("/modules")
channels.ls("/groups/flight")
local info = channels.stat("/groups/flight/gear")
local value = channels.read("/groups/flight/gear")
```

Wire operations are explicit because setting redstone and taking control authority are intentionally different side effects:

```lua
channels.setWire("/groups/flight/gear", 15)
channels.pulseWire("/wires/engine_start", 10, 15)
channels.resetWire("/groups/flight/gear")
```

Physical control overrides use directional `0..15` values:

```lua
channels.override("/groups/flight/roll_left", 12)   -- native Aeroworks turn = -12
channels.override("/groups/flight/roll_right", 7)  -- native Aeroworks turn = +7
channels.release("/groups/flight/roll_right")
channels.releaseAll()
```

`channels.overrideBatch` resolves and validates every logical path before writing. A batch which addresses both directions of the same native Aeroworks axis is rejected. The legacy `controls.override` API remains signed `-15..15` for programs deliberately using native Aeroworks semantics.

## Drive By Wire multiblock selection

Drive By Wire 0.2.9 internally stores one physical selected source block. CC-Aeroworks keeps a separate logical selection session for an active ControlDesk multiblock. Clicking any real member selects the same logical desk network; scrolling traverses native channels from every member plus the ComputerControlDesk user-defined wire channels.

The logical selection remains anchored to the whole ControlDesk while the current physical endpoint is mirrored into DBW for its unchanged connection protocol. Native controls retain their real desk position as source; user-defined channels use the ComputerControlDesk owner position, matching `WireChannelBank` signal publication. The selected-source outline is the shared outer ControlDesk bounds.

Interactive CC-Aeroworks displays keep real Aeroworks `x/y` ControlChannels for Combined pseudo-finger configuration, but those channels are not vehicle DBW outputs. Display isolation is enforced in the Aeroworks channel catalogue and again at DBW's final `getCurrentSignal` lookup. The higher-priority lookup guard returns zero for display-pointer x/y while leaving the real Aeroworks module values intact for pseudo-finger movement.

## Persistence and fail-safe behavior

Wire definitions are stored in `cc_aeroworks:wire_channels`; logical groups are stored in `cc_aeroworks:channel_groups`. Both survive block/item transfer and duplicate embedded-computer ejection. Runtime wire values and pending pulses are transient.

Wire outputs are forced to zero when the embedded computer is off, the ControlDesk network is not `ACTIVE`, the ComputerControlDesk is not the single owner, or the block entity is invalidated/unloaded. Programs must establish desired outputs again after boot.
