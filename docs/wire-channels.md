# ComputerControlDesk wire channels

The embedded ComputerControlDesk can expose user-defined redstone channels to Drive By Wire without consuming the six normal CC:Tweaked redstone sides.

## Configuration belongs to the desk, not to programs

Channel definitions are hardware configuration owned by the ComputerControlDesk. They can be managed either from the ComputerControlDesk channel tab or with the bundled shell command:

```text
wires
wires list
wires add landing_gear
wires add engine_start
wires rename landing_gear gear
wires info gear
wires remove gear
```

The computer UI adds a Channels tab beneath the existing Controls tab. It displays the server-authoritative channel list, current signal value, Drive By Wire backend state and connection count. Add, rename and remove operations are sent to the server and are applied to channels by their persistent UUID. Removing a channel with active Drive By Wire connections requires an explicit confirmation in the UI.

The `wires` alias is installed only when the embedded ComputerControlDesk APIs exist. A normal CC:Tweaked computer keeps its normal ROM environment and does not receive the alias.

The supported public Lua API deliberately has no `add`, `remove`, `create` or `rename` methods. Programs consume the configured hardware contract instead of recreating it during every boot. The shell command and graphical channel manager are administrative front ends over the same `WireChannelBank`; neither creates a second channel state.

Channel names must match `[a-z][a-z0-9_-]{0,31}` and a ComputerControlDesk may define at most 32 channels.

Each definition has a persistent UUID in addition to its name. Renaming keeps the UUID and migrates existing Drive By Wire connections. Removing a channel first drives it to zero and permanently removes its Drive By Wire connections. Recreating the same textual name creates a new UUID and does not restore the deleted connection.

## Runtime Lua API

The embedded computer exposes the global `wires` table:

```lua
local channels = wires.list()

if not wires.exists("landing_gear") then
    error("landing_gear is not configured")
end

wires.set("landing_gear", 15)
wires.set("landing_gear", 0)
wires.pulse("engine_start", 10)
wires.pulse("warning", 20, 8)

print(wires.get("landing_gear"))
print(wires.getBackend())
print(wires.isEnabled())

wires.reset("landing_gear")
wires.resetAll()
```

`wires.set(name, value)` accepts analog redstone values from 0 through 15. `wires.pulse(name, ticks, value)` accepts 1 through 15 and returns the channel to zero when the pulse expires. A normal `set` cancels an active pulse on that channel.

`wires.getInfo(name)` returns at least:

```lua
{
  id = "persistent-channel-uuid",
  name = "landing_gear",
  value = 0,
  backend = "drivebywire",
  connected = true,
  connections = 1,
  enabled = true,
}
```

## Fail-safe behavior

Only definitions are persistent. Runtime signal values and pending pulses are intentionally transient.

All virtual outputs are forced to zero when any of the following applies:

- the embedded computer is off or unavailable;
- the ControlDesk multiblock is not `ACTIVE`;
- the ComputerControlDesk is not the single owner of the active multiblock;
- the block entity is invalidated or unloaded;
- the server/computer is restarted.

A program must therefore establish its desired output state after boot. This prevents a previously active starter, release actuator or other output from silently returning at power-up.

## Drive By Wire 0.2.9 integration

Drive By Wire is optional. When it is present, CC-Aeroworks forwards each channel value to `WireNetworkManager` using the ComputerControlDesk position as the DBW source and the configured channel name as the DBW channel key.

Drive By Wire 0.2.9 normally asks the selected source block type for multi-channel names. ComputerControlDesk definitions are per block entity, so CC-Aeroworks intercepts the DBW client channel-selection step only for ComputerControlDesk sources and reads the synchronized definitions from that specific desk. Other Drive By Wire sources keep their original selection behavior.

Wire creation remains the normal Drive By Wire interaction:

1. configure one or more channels in the Channels tab or with `wires add <name>`;
2. hold a Drive By Wire wire and select the ComputerControlDesk as source;
3. scroll through that desk's configured channel names;
4. connect the desired sink face;
5. drive the channel from Lua with `wires.set` or `wires.pulse`.

If Drive By Wire is absent, definitions still persist and the Lua API remains available, but `getBackend()` reports `none` and no physical wire output is produced.

## Item and multiblock lifecycle

Wire definitions are stored in the `cc_aeroworks:wire_channels` data component and in ComputerControlDesk block-entity persistence.

They survive normal break/place cycles. They are also copied to a standalone CC computer when duplicate ComputerControlDesks are reconciled and one embedded computer is ejected. Because the ComputerControlDesk crafting recipe copies the computer item's components, combining that computer with a ControlDesk restores its configured wire definitions.

The output values themselves are never copied or restored: they always start at zero.
