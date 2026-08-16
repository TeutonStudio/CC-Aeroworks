# ComputerControlDesk channels and Drive By Wire

The embedded ComputerControlDesk exposes one Channels tab for immutable physical control channels, user-defined wire channels, persistent user groups and a logical path layer over both channel kinds. All user-facing channel signals are ordinary redstone strengths from `0` through `15`.

## Channel identity versus logical path

Every channel has a stable canonical identity. A physical control uses an id derived from desk, socket, module, native channel and direction; a user wire uses `wire:<uuid>`. Bindings and persistent logical names always reference that stable id.

A channel also has a **logical path** used by the Channels UI and the high-level `channels` Lua API. Slash-separated segments become virtual collapsible groups automatically:

```text
CONTROL MODULES
  Yoke · Desk 1 · big
    flight/
      attitude/
        roll_left       0/15
        roll_right      8/15

WIRE CHANNELS
  systems/
    landing/
      deploy            0/15
```

Renaming a channel in the Channels tab changes only this logical path. It never changes the Aeroworks native control identity, the persistent wire UUID, the current signal value or Drive By Wire topology. Path overrides are stored by canonical id, so temporarily missing hardware keeps its configured name and regains it when the same target returns.

Logical path segments match `[a-z][a-z0-9_-]{0,31}`. Paths support up to eight segments and 192 characters. A leaf cannot also be the parent of another leaf in the same namespace: `flight` and `flight/roll` therefore cannot coexist, while `flight/roll` and `flight/pitch` can.

Control paths are scoped to their owning module. Wire paths share the `/wires` namespace. Synthetic slash groups are presentation nodes only: they have no UUID and are not persisted as user groups.

## Channels tab

The Channels tab is part of the native CC:Tweaked computer screen and keeps three semantic sections:

```text
CONTROL MODULES
  Yoke · Desk 1 · big
    flight/
      roll_left          0/15
      roll_right         8/15
        -> 17, 82, -41  north

WIRE CHANNELS
  gear/
    landing              0/15
      -> 18, 82, -41  west

USER GROUPS
  primary_flight
    roll_right -> flight/roll_right  8/15
    gear       -> gear/landing       0/15
```

Physical modules are discovered from the active Aeroworks ControlDesk multiblock. Their native channel ids remain immutable, but their logical path can be renamed. Bidirectional Aeroworks values stay signed internally; the logical layer exposes the same directional redstone outputs as physical Drive By Wire: `left=12,right=0`, `left=0,right=7`, and neutral `left=0,right=0`.

Drive By Wire sink rows include sink coordinates and connected block side. The GUI reads these from DBW's existing `WireNetworkSink` topology instead of maintaining a second connection database.

Selecting a control or wire pre-fills the editor with its current logical path. Rename applies to the selected channel path. Group and binding mutations keep their existing semantics, while virtual slash groups only expand and collapse.

## User-defined wire channels and backend names

Wire definitions are hardware configuration owned by the ComputerControlDesk. Every definition has a persistent UUID and a physical **backend name**. Backend names still match `[a-z][a-z0-9_-]{0,31}` and a ComputerControlDesk supports at most 32 wire channels.

The low-level `wires` API and `wires` shell command operate on the backend name because that name is used by the Drive By Wire network. A physical wire rename therefore intentionally migrates DBW connections. This is separate from a logical Channels rename.

For example, a wire can have:

```text
canonical id:  wire:2ae4...
backend name:  landing_gear
logical path:  systems/gear/landing
```

Changing `systems/gear/landing` in the Channels UI does not reconnect anything. Running the low-level wire rename from `landing_gear` to `gear_output` changes the DBW backend name and migrates its physical connections while the logical path remains attached to the same UUID.

The legacy runtime API remains available and flat:

```lua
wires.set("landing_gear", 15)
wires.pulse("engine_start", 10)
print(wires.get("landing_gear"))
wires.reset("landing_gear")
wires.resetAll()
```

## Logical user groups

User groups are persistent logical wiring between cockpit hardware and software. They remain distinct from virtual slash groups. A binding has a stable alias and targets either a canonical physical-control ID or persistent wire UUID:

```text
primary_flight
  roll_left  -> control:<desk>:<socket>:<module>:turn:left
  roll_right -> control:<desk>:<socket>:<module>:turn:right
  gear       -> wire:<uuid>
```

A target may appear in more than one user group. Temporarily losing a physical module does not delete the binding. The GUI keeps it and shows `MISSING`; it becomes available again when the same stable target returns. Group names and binding aliases can be renamed without changing their stable target reference.

Groups, bindings and logical channel paths can be configured with the bundled `channels` command:

```text
channels ls /
channels ls /modules
channels ls /wires
channels rename wire:<uuid> systems/gear/landing
channels reset-name wire:<uuid>
channels group add primary_flight
channels bind primary_flight gear wire:<uuid>
channels binding rename primary_flight gear landing_gear
channels unbind primary_flight landing_gear
channels group rename primary_flight flight
channels group remove flight
```

## High-level `channels` Lua API

The logical API is additive. Existing `controls` and `wires` APIs remain available as low-level compatibility interfaces. `channels.ls()` treats logical paths as a real hierarchy:

```lua
channels.ls("/")
channels.ls("/wires")
channels.ls("/wires/systems")
channels.ls("/modules/<desk>/2/flight")

local gear = channels.stat("/wires/systems/gear/landing")
local value = channels.read("/wires/systems/gear/landing")
```

Canonical ids can always be used directly, so automation may deliberately ignore user-facing names:

```lua
channels.read("wire:<uuid>")
channels.read("control:<desk>:<socket>:<module>:<channel>:<direction>")
```

For a user-group binding, `stat()` preserves the logical alias as `name` while still returning resolved target metadata. Missing targets remain discoverable through `ls()` and report `available=false`.

Wire operations are explicit because setting redstone and taking control authority are intentionally different side effects:

```lua
channels.setWire("/wires/systems/gear/landing", 15)
channels.pulseWire("/wires/engine/start", 10, 15)
channels.resetWire("/groups/flight/gear")
```

Physical control overrides use directional `0..15` values and accept nested module paths:

```lua
channels.override("/modules/<desk>/2/flight/attitude/roll_left", 12)
channels.override("/groups/flight/roll_right", 7)
channels.release("/groups/flight/roll_right")
channels.releaseAll()
```

`channels.overrideBatch` resolves and validates every logical path before writing. A batch which addresses both directions of the same native Aeroworks axis is rejected. The legacy `controls.override` API remains signed `-15..15` for programs deliberately using native Aeroworks semantics.

## Drive By Wire multiblock selection

Drive By Wire 0.2.9 internally stores one physical selected source block. CC-Aeroworks keeps a separate logical selection session for an active ControlDesk multiblock. Clicking any real member selects the same logical desk network; scrolling traverses native channels from every member plus the ComputerControlDesk user-defined wire channels.

The logical selection remains anchored to the whole ControlDesk while the current physical endpoint is mirrored into DBW for its unchanged connection protocol. Native controls retain their real desk position as source; user-defined channels use the ComputerControlDesk owner position, matching `WireChannelBank` signal publication. Starting a logical selection still invokes DBW's native network sync, and clearing it goes through DBW's own `clearSource()` so its mirrored network/cooldown state cannot outlive the selection. The selected-source outline is the shared outer ControlDesk bounds.

Interactive CC-Aeroworks displays keep real Aeroworks `x/y` ControlChannels for Combined pseudo-finger configuration, but those channels are not vehicle DBW outputs. Display isolation is enforced in the Aeroworks channel catalogue and again at DBW's final `getCurrentSignal` return.

## Persistence and fail-safe behavior

Wire definitions are stored in `cc_aeroworks:wire_channels`, user groups in `cc_aeroworks:channel_groups`, and logical path overrides in `cc_aeroworks:channel_paths`. All three survive block/item transfer and duplicate embedded-computer ejection. Runtime wire values and pending pulses are transient.

Wire outputs are forced to zero when the embedded computer is off, the ControlDesk network is not `ACTIVE`, the ComputerControlDesk is not the single owner, or the block entity is invalidated/unloaded. Programs must establish desired outputs again after boot.
