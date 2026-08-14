# ComputerControlDesk control overrides

The embedded ComputerControlDesk computer can take explicit server-side authority over continuous Aeroworks control channels in its active ControlDesk multiblock. This is intended for autopilots, autothrottle, stability augmentation and other computer-driven control systems.

The API is intentionally **not** exposed on the public `ControlDesk` peripheral. External CC:Tweaked computers may observe desk inputs, but only the embedded computer which owns the active multiblock can override its control channels.

## API

The global API is `controls`; the module form is:

```lua
local controls = require("cc_aeroworks.controls")
```

Methods:

```text
getChannels() -> table
getState(deskId, socket, channel) -> table
override(deskId, socket, channel, value) -> table
overrideBatch(commands) -> number
release(deskId, socket, channel) -> boolean
releaseAll() -> number
```

Sockets accept `left`, `right`, `big` or the corresponding zero-based index.

Control values are integers in `-15..15`. Out-of-range values are rejected instead of silently clamped.

## Supported controls

The first control-authority version supports continuous vehicle controls:

| Module | Channels |
|---|---|
| `aeroworks:lever` | `lever` |
| `aeroworks:joystick` | `x`, `y` |
| `aeroworks:wheel` | `wheel` |
| `aeroworks:yoke` | `turn`, `pitch` |
| `aeroworks:throttle_quadrant` | `red`, `amber`, `green`, `blue` |

CC-Aeroworks display pointer X/Y channels are deliberately excluded. They drive the local virtual finger and are not vehicle-control channels.

Binary button semantics are also not part of this first override contract.

## Discovering channels

```lua
for _, channel in ipairs(controls.getChannels()) do
  print(channel.desk, channel.socketName, channel.module, channel.channel, channel.value)
end
```

Each entry includes:

```lua
{
  desk = "stable-desk-uuid",
  deskIndex = 2,
  socket = 2,
  socketName = "big",
  module = "aeroworks:yoke",
  channel = "pitch",
  value = 0,
  overridden = false
}
```

When an override is active the table additionally contains `commanded`, `owner` and `mode`.

## HARD authority

`override` engages HARD authority for the addressed channel:

```lua
controls.override(yokeDeskId, "big", "pitch", -4)
```

While HARD authority is active, normal Aeroworks controller writes to that exact channel are ignored. The computer command is written through Aeroworks' normal `setChannelFromController` path, so the canonical MountedModule value, vehicle control value and synchronized visual control position stay aligned.

The control therefore visibly moves when a computer command changes it. The implementation does not maintain a second renderer-only control value.

Repeatedly commanding the same value does not rewrite the Aeroworks channel.

## Batch commands

Autopilots should update coupled axes together:

```lua
controls.overrideBatch({
  { desk = yokeDeskId, socket = "big", channel = "turn",  value = rollCommand },
  { desk = yokeDeskId, socket = "big", channel = "pitch", value = pitchCommand },
})
```

The complete batch is validated before any command is applied. Duplicate targets inside one batch are rejected. Valid commands are then applied in the same server-thread call.

Aeroworks currently synchronizes through its existing per-channel setter, so the batch contract is same-tick command grouping rather than a promise of one physical network packet.

## Ownership

A channel has at most one ComputerControlDesk owner. A second owner cannot silently steal it.

In a normal active ControlDesk multiblock the multiblock resolver already requires exactly one embedded ComputerControlDesk. The explicit owner check also protects against stale runtime state while networks are being reorganized.

## Releasing authority

Release one channel:

```lua
controls.release(yokeDeskId, "big", "pitch")
```

Release every channel owned by this embedded computer:

```lua
controls.releaseAll()
```

Release does not force the channel to zero. The last effective value stays in Aeroworks and normal input regains authority from that point onward.

Overrides are runtime state and are not persisted in NBT.

All owned overrides are automatically released when:

- the embedded computer is off,
- the ComputerControlDesk is invalidated/removed,
- its ControlDesk network is no longer active or owned by that computer,
- the target desk leaves the network,
- the target module/channel becomes invalid.

This prevents a saved or abandoned autopilot command from becoming persistent control authority after a restart or desk reconfiguration.

## Events

Engage/update:

```text
cc_aeroworks_control_override(
  action,
  deskId,
  deskIndex,
  socket,
  socketName,
  channel,
  commandedValue,
  mode
)
```

`action` is `engaged` or `updated`; the current mode is `hard`.

Release:

```text
cc_aeroworks_control_release(
  deskId,
  socket,
  socketName,
  channel,
  reason
)
```

Typical reasons include `released`, `computer_off`, `invalidated`, `network_invalid` and `target_invalid`.

Existing desk/console input events continue to describe effective MountedModule value changes and are not given incompatible extra arguments.

## Minimal autopilot pattern

```lua
local controls = require("cc_aeroworks.controls")

local function clamp(value)
  return math.max(-15, math.min(15, math.floor(value + 0.5)))
end

local ok, err = pcall(function()
  while true do
    local attitude = get_attitude_somehow()
    local pitch = clamp((targetPitch - attitude.pitch) * 0.6)
    local roll = clamp((targetRoll - attitude.roll) * 0.6)

    controls.overrideBatch({
      { desk = yokeDeskId, socket = "big", channel = "pitch", value = pitch },
      { desk = yokeDeskId, socket = "big", channel = "turn", value = roll },
    })
    sleep(0.05)
  end
end)

controls.releaseAll()
if not ok then error(err, 0) end
```

Production scripts should always release their authority on normal shutdown/error paths even though the server lifecycle also performs a fail-safe release.
