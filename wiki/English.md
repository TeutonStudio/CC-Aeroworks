# CC-Aeroworks guide

[[Deutsch|Home]] · **English**

CC-Aeroworks turns **Create: Aeroworks** desks into programmable cockpits using **CC:Tweaked**. This guide covers setup, every user-facing feature and important limits. The detailed [[display guide|Displays-en]] and [[Lua API|API-en]] are also available in English.

The installation baseline below is the published **1.5.3** release. Local development **1.5.4** changes the TeutonLib baseline to 1.6.x. Follow the dependency ranges reported by your installed build; do not combine the two baselines.

## Installation

Install the mod and its dependencies on **both client and server**. Use Minecraft **1.21.1**, **NeoForge** and **Java 21**.

| Required component | Published 1.5.3 baseline |
|---|---|
| NeoForge | 21.1.234 or newer for Minecraft 1.21.1 |
| Create | 6.0.10; below 6.0.11 |
| Create: Aeroworks | 1.5.0; below 1.6.0 |
| Create: Aeronautics | 1.3.2; below 1.4.0 |
| Sable | 2.0.5 baseline; at least 2.0.1 in the 1.5.3 metadata |
| CC:Tweaked | 1.120.2 baseline; 1.119.0 to below 1.121.0 |
| TeutonLib | 1.5.x for 1.5.3; 1.6.x for local development 1.5.4 |
| Kotlin for Forge | 5.12.0 baseline; at least 5.11.0, below 6.0.0 |

Download CC-Aeroworks from [GitHub Releases](https://github.com/TeutonStudio/CC-Aeroworks/releases) or [Modrinth](https://modrinth.com/mod/cc-aeroworks). Put the JARs in the instance's `mods` directory and remove duplicate versions. Check each dependency's own requirements too.

Optional integrations are **CC: Terminals 0.1.1**, **Drive By Wire 0.2.9–0.3.x**, **Create: Radars 0.4.9.4+**, **Create: Train Functionality exactly 0.1.47**, and **Create: Simulated 1.3.2–1.3.x**. The radar runtime also needs its own dependencies, including Create Big Cannons and Ritchie's Projectile Library. See the [dependency manifest](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/libs/dependencies.json).

## First program

![Shared device slot with an advanced computer and mounted displays.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-device-slot.png)

1. Place an Aeroworks desk or stand. Sneak + right-click it to open the module overview.
2. Insert a normal or advanced CC:Tweaked computer into the shared device slot. Open it using the Computer button.
3. Mount a display in a compatible socket. Large displays require `big`; small displays also fit `left` and `right`.
4. Run the following on the embedded computer:

```lua
local peripherals = require("cc_aeroworks.peripherals")
print(textutils.serialize(peripherals.getNetwork()))
for address, desk in pairs(peripherals.find("ControlDesk")) do
  print(address, desk.getInfo().id)
  for _, display in pairs(desk.getDisplays()) do
    print(textutils.serialize(display))
  end
end
```

Use the reported socket of a mounted display with `desk.setDisplayText(socket, "42")`. Do not assume that every desk has a display in `big`.

On an **external** computer, use `peripheral.find("ControlDesk")` instead. The external adapter controls only its directly connected desk; it does not expose the embedded computer's global APIs.

## Using the desks

![Native in-game manual with topic navigation and API scopes.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-manual.png)

![Pixel editor with display-size and socket selection; the shown draft is empty.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-pixel-editor.png)

![Sources page; the visible radar ingress is not linked yet.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-sources.png)

| Action | Input |
|---|---|
| Open module overview and device slot | Sneak + right-click a desk |
| Open computer or terminal | Device button in the overview |
| Operate a mounted control | Right-click the control |
| Open module settings | Create wrench + right-click a horizontal desk side |
| Rotate the desk | Wrench on its upper or lower face |
| Open a Ponder explanation | Hold W over the corresponding item |

The control HUD shows the active module and configured inputs. The compact multiblock HUD keeps the row's module selection together. The **Sources**, **Controls** and **Channels** tabs organise information sources, control settings and logical channels. The in-game manual includes an API reference and a pixel editor. Choose a display size and socket, draw with the left mouse button and erase with the right. Clear, fill, invert and copy the generated Lua output using the editor tools. The editor itself does not change a display in the world.

The Sources tab groups peripherals, Display Links, storage, GPS and optional radar inputs. Expand entries to inspect availability and details; an unlinked input is not a valid reading.

## Desk assemblies

![Copycat desks with copper frame and wooden panel materials.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-copycat-style.png)

Desks form explicit linear assemblies with a persistent `assemblyId`. Use Create's placement arrow at a free end, or place against the matching side, to extend a row. Sneak while placing to start a separate row. Two aligned rows can touch without sharing their devices. Desks and stands are different geometry families; ordinary and Copycat variants can share their matching family.

A row supports one slot device and at most **64 fully loaded physical segments**. The resolver does not load chunks. A computer can be placed anywhere in the row without changing the reachable devices. Each real desk retains its own stable ID, sockets, modules and side peripherals.

Copycat frame and panel materials are applied across the loaded row. Each visible segment and active material zone needs its own material block. Insufficient materials or a partially loaded row leave the style unchanged. Paid materials remain associated with their segments and are returned once when removed; Creative mode produces no material returns.

When the computer's carrier is broken, its device moves into the larger remaining part, with the left side winning a tie. The final desk drops the device. Computer ID, files, label, terminal size and stored channel definitions are preserved. When occupied rows join, computers take priority over terminals, advanced devices over normal devices of the same kind, then row order; extra devices are ejected with their data.

## Peripheral network

![Ponder shows a wireless modem attached to the desk assembly.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-peripheral_search.png)

The embedded computer exposes `peripherals` globally and through `require("cc_aeroworks.peripherals")`.

```lua
for address, desk in pairs(peripherals.find("ControlDesk")) do
  print(address, desk.getInfo().id)
end
for address, device in pairs(peripherals.findAll("modem")) do
  print(address, textutils.serialize(device.getPeripheralInfo()))
end
print(textutils.serialize(peripherals.getTree()))
```

`find(type)` returns `nil`, a direct handle, or an address-keyed table for zero, one, or several matches. **`ControlDesk` always returns a table.** `findAll` always returns a table. Type aliases support namespaced and normalised short names; prefer a full type if names collide.

Use `wrap(x, y, z, type?)` for position lookup, `getTypes()` for discovery and `refresh()` to refresh the graph. Desk handles provide their own neighbouring-device lookup. Methods on device handles call the actual peripheral, including CC:Tweaked events and mounts. Attach/detach events report changes. Reacquire handles after topology changes; position addresses can change, while desk IDs remain stable.

## Displays, pixels and touch

![All four display variants mounted on a row.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-four-displays.png)

![A programmed cyan pixel pattern at 32 PPB on an advanced large display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-pixels-colour-resolution.png)

![Draw example: the real touchdisplay.drawStroke Lua helper renders a curve from predefined test points onto the display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-draw-stroke.png)

All **four** display variants support text, numbers and pixels. Small and large sizes each have normal and advanced tiers. Normal displays have a fixed signal colour; advanced displays add a saved `0xRRGGBB` colour and touch/draw input. See [[Displays and recipes|Displays-en]] for sockets, dimensions and the four recipes.

Use `getDisplaySize(socket)` to query the effective raster. At the default **256 Parts per Block**, small displays are **112 × 112** pixels and large displays are **160 × 112**. Pixel coordinates start at `(1,1)`. The [[Lua API|API-en]] documents text, numbers, pixel batches, frames, colours and clearing.

When `display.useGlobalResolution=false`, `setDisplayResolution(socket, ppb)` chooses a slot's density up to `display.maxIndividualPpb`. Changing the raster clears incompatible pixels; draw the image again. Density settings persist, including while global mode is enabled, and are removed when that module is dismounted.

For touch, install an **advanced display** and an **advanced computer or advanced CC: Terminals terminal** in the active row. Normal or missing slot devices disable touch, including on external computers. In the module settings, select the display controller and configure pointer inputs. Enter console-control mode: left click sends `tap`, holding the right button produces a sequenced `draw` gesture. Draw data includes normalised coordinates, direction, speed, path samples and an explicit end event.

A display has one exclusive controller: a compatible Lua script or a radar source. Embedded computers execute assigned display handlers; external computers receive events and run their own programs. Changing a computer or resolution cancels an active gesture. A terminal does not execute local Lua files.

## Combined input

![Combined input enabled on a lever: configured activation key plus mouse Y.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-combined-input.png)

Choose **Combined** as the input source in a supported module's settings. Configure each axis's activation key or mouse button and enable the intended directions. The combined mode supports levers, joysticks, yokes, throttles, pedals and display-pointer axes where offered by the actual module.

Enter the desk's normal control mode, point at the intended control and hold its configured activation key while moving the mouse. Joystick axes can share a key to move together; assign separate keys if you want independent movement. Throttle axes can likewise share activation. Axis inversion and sensitivity affect direction and response; do not confuse a display-pointer axis with a vehicle output.

Escape, leaving control mode, losing a valid target or changing the active device stops the relevant input ownership. The camera follows the configured focus behaviour; it is not frozen indefinitely when a control becomes invalid. If Lua holds a HARD override on the same channel, release it before expecting manual input to take effect.

## Channels and automation

![Channels page with two configured software outputs, currently at zero.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-channels.png)

![Channel page with the flight group and software outputs.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-channel-groups.png)

![Native Drive By Wire target editor with block coordinates and input side.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-dbw-target-editor.png)

The **Channels** tab combines physical controls, software wire channels and named groups. Physical controls are discovered automatically. Define wire outputs and groups in the tab or using the shipped CraftOS `wires` and `channels` commands. Wire names follow `[a-z][a-z0-9_-]{0,31}`, with up to 32 per computer. Renaming retains a wire's UUID and connections; deleting it clears its output and removes its connections.

Logical `channels` values and wire strengths use **0–15**. Native `controls` values use **-15–15**. Define the example paths before running this code:

```lua
local channels = require("cc_aeroworks.channels")
print(textutils.serialize(channels.ls("/")))
print(channels.read("/groups/flight/roll_right"))
channels.setWire("/groups/flight/gear", 15)
channels.pulseWire("/wires/engine_start", 10, 15)
channels.resetWire("/groups/flight/gear")
```

`channels.override(path, value)` gives software HARD authority over a physical channel; ordinary input is suppressed until `channels.release(path)`. Release restores input authority but **does not reset the last value to zero**. Decide whether to neutralise first, and release your overrides on exit or failure. `overrideBatch` validates all targets first; do not address both directions of one axis in a single batch.

With **Drive By Wire**, use a cable or select a channel's **Target…** entry. Enter integer block coordinates and the target input side, then connect or disconnect. The target must be loaded, editable and in the same dimension. On Sable, use stored sublevel block coordinates and local block sides, not global GPS coordinates. Cable consumption and refunds follow the optional mod's rules; Creative mode is exempt.

Definitions and groups persist. Pulses, active values and overrides are transient: power-off, unloading or an invalid row clear wire outputs and release overrides. Missing group targets remain marked `MISSING` without losing their saved assignments.

## Control scripts

![The existing /gear.lua script is assigned to the lever module.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-bound-control-script.png)

A lever, button or another supported control can run a Lua handler when its value changes. Save a non-empty `.lua` file on the embedded computer, then select it as the module's **Script source**. Choose no source to remove the assignment.

```lua
local event = ...
if event.value > 0 and (event.previousValue or 0) <= 0 then
  print("Pressed: " .. event.channel)
end
```

Handlers receive the originating desk, socket, module, channel, current and previous values and handler path. The event is also available as `controlEvent`. A file may return a function or a table with `onInput`. It is reloaded for each invocation, so edits apply to the next change. Release and negative values also trigger changes; unchanged input does not repeatedly invoke a handler.

Return promptly. Run long loops and waiting event loops in a main program. The computer must be on and running its normal CraftOS event loop. Errors are printed without permanently disabling future input. Stored owner-local handler paths are not automatically executed by external computers. The script catalogue is limited to 256 Lua files, 64 KiB each and eight subdirectory levels.

## Operator terminals

![Example output in an actual advanced operator terminal installed in a desk.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-operator-terminal.png)

With **CC: Terminals 0.1.1**, the shared slot accepts a terminal instead of a computer. The program runs on a separate protected computer, connected directly or through **TeutonLib Peripheral Link**. The desk appears as an additional `terminal` peripheral. Draw with the normal terminal API and handle the corresponding `terminal_*` events on that computer.

A normal terminal provides monochrome output and keyboard input. An advanced terminal adds colour and mouse input. Terminals have no local CraftOS, editable program files or local script execution. Ctrl+T at a terminal does not terminate the remote program. Device tier expands input/output; it does not grant additional permissions.

An advanced terminal also satisfies the slot-device requirement for advanced-display touch. External programs still handle the events themselves. The row keeps a single slot device and uses the same transfer and conflict rules as computers.

## Telemetry and docking

![The real Diamantenlager Create Display Link source is ready in the Sources page.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-verified-telemetry.png)

![Live telemetry through two actually locked docking connectors: the remote inventory contains 64 items.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-dock-telemetry.png)

Target the desk carrying the embedded computer with Create **Display Links**. CC-Aeroworks exposes structured `fill_level`, `count_items`, `list_items`, `count_fluids` and `list_fluids` sources through `telemetry`. Other sources remain visible with `supported=false` and their `displayText`; numeric values are not reverse-parsed from formatted text.

```lua
local telemetry = require("cc_aeroworks.telemetry")
print(textutils.serialize(telemetry.list()))
local source = telemetry.get("fuel") -- a previously assigned alias
if source and source.available and source.supported and not source.stale then
  print(textutils.serialize(source.value))
end
```

Aliases persist; live values are rebuilt by the links after a restart. Inspect `revision`, `ageTicks` and `stale`; missing data is not a valid zero. Truncated lists retain their totals and entry counts. On Sable, source identity uses the sublevel UUID and local link position.

With **Create: Simulated**, links can feed a remote docking connector. A locked dock exposes the other module's telemetry without a second computer on that module. Use `getDocks()`, `getDock(nameOrId)`, the dock handle's `getTelemetry(nameOrId)` and the documented rename methods. `getTransferBuffers()` describes only the connector's item/fluid/energy buffers, **not the remote module's entire storage**. Unlocking invalidates remote availability.

Events report added, changed and removed sources, dock changes and remote telemetry changes. Identical refreshes do not emit redundant changed events.

## Radar

![Native view of an operating radar with real test-world contacts. A cow contact is selected in the native network.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-radar-selected-contact.png)

![Ponder demonstrates the native radar Data Link setup.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-radar_direct.png)

Install the supported **Create: Radars** runtime and dependencies. Configure its native radar, Data Link and Network Filterer first. A display dropdown does **not** create the native filterer-to-radar connection.

An advanced small or large display offers **Radar** as a controller in the same selector as Lua scripts. It can consume a native Data Link endpoint or a Network Filterer visible to the embedded computer's assembly. The source, computer and display may sit on different desks in one active row. A physical Data Link block still belongs to the native setup; the integration does not replace Create: Radars' NetworkData or DetectionConfig.

Create: Radars retains detection, filtering and native monitor endpoint rules. CC-Aeroworks routes the selected data into the display, draws contacts and a contact legend, and supports the documented pointer target selection. Source status and stale/invalid connections remain visible. Multiple native network controllers require an unambiguous source or the supported local monitor endpoint.

Select a different controller or clear the radar binding to return display ownership to Lua. Detailed native setup and diagnostic contracts are in the [radar integration reference](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/create-radars-integration.md). Use the [[English API|API-en]] for methods and events.

## Trains and contraptions

![Native Create train and contraption controls between desk segments.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-native-create-controls.png)

![Dashboard Controller ready in panel mode; the lever is explicitly bound to throttle.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-bound-dashboard.png)

Place native **Create Train Controls** or **Contraption Controls** against a floor desk or stand. Matching segments inherit the row's orientation, chassis and skin in either placement order. They can bridge compatible desk sections, count toward the 64-segment limit and expose neighbouring sources, but have no desk ID or module sockets. Ceiling consoles do not integrate these controls. With no matching anchor desk remaining, the connected group returns to its native Create appearance.

Integrated Contraption Controls expose `ContraptionControl`. The `actors` and `doors` modes support `isEnabled`, `setEnabled` and `toggle`; `elevator` provides floor discovery and `selectFloor(indexOrName)`. All provide `getInfo`, `getState` and `getFilter`. Calling an unsupported mode-specific method raises a Lua error. An empty filter means all actors. Changes emit `cc_aeroworks_contraption_control`.

With **Create: Train Functionality exactly 0.1.47**, place a **Dashboard Controller** directly below a row segment and select it as a module source. Bind channels explicitly: axes can supply throttle/steering, axes or pedals can brake, and buttons can brake or request the next-station stop. New assignments start empty. Multiple controllers, partial loading or redstone frequency mode suspend saved assignments without deleting them.

The controller is also a `DashboardController` peripheral:

```lua
local train = peripheral.find("DashboardController")
assert(train, "No Dashboard Controller connected")
print(textutils.serialize(train.getState()))
```

`setThrottle(value)` and `setSteer(value)` accept finite values from -1 to 1; `setBrake(boolean)` and `setStation(boolean)` control the other inputs. Setters are unavailable in frequency mode. Lua and explicitly bound desk inputs are merged deterministically, then neutralised on the documented disconnect, unload and restart transitions. These methods can move the train.

## Moving constructions

![A desk assembled on a running Create bearing contraption.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-create-mobile-desk.png)

![A running program on an actual Sable desk: gps.locate reports global coordinates 12, 80, 0 and the network reports sable.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-sable-gps.png)

On ordinary **Create contraptions**, the default-enabled mobile runtime restarts the embedded computer with its ID, files, family, terminal size and power state preserved. Programs must rediscover devices and re-establish outputs after boot. `peripherals.getNetwork().space` reports `contraption`; metadata includes entity UUID, local position and projected world position.

Only peripherals with a supported `MobilePeripheralAdapter` work in this runtime; arbitrary stationary third-party devices are not automatically mobile. Stationary world targets report unavailable. `telemetry.get("contraption")` provides position, rotation, velocity and mounted-storage occupancy. Treat an unavailable reading separately from a genuine zero.

**Sable** continues to use real sublevel block entities. TeutonLib measures normal wireless range in visible world coordinates, so a successful `gps.locate()` and the GPS information source report global world position. This still requires a directly detectable wireless modem and valid GPS hosts; hosts in genuinely different dimensions are ignored. Drive By Wire targets instead use stored sublevel coordinates and local sides.

`mobileContraptions.enabled=false` closes the ordinary Create mobile computers, adapters and outputs. It does not replace Sable's sublevel model.

## Configuration

Client input settings live in `config/cc_aeroworks-client.toml`. Server settings live in `config/cc_aeroworks-server.toml`; a world's `serverconfig/cc_aeroworks-server.toml` takes priority where present. Restart the world/server after installing a world override.

| Client input key | Default |
|---|---:|
| `combinedLeverSensitivity` | 0.15 |
| `combinedLeverInvertY` | false |
| `combinedLeverPacketRate` | 20 updates/second |
| `displayPointerSensitivity` | 0.0025 |
| `freezeCameraOnlyWithValidTarget` | true |

```toml
[display]
ppb = 256
useGlobalResolution = true
maxIndividualPpb = 256

[mobileContraptions]
enabled = true

[debug]
radar = false
display = false
```

Individual density is limited to whole numbers from 16 through the configured maximum; `maxIndividualPpb` ranges from 16 to 1024. At 32 PPB, small and large displays are 14 × 14 and 20 × 14 pixels. Old separate X/Y resolution keys are no longer read.

Telemetry defaults are 128 sources per endpoint, 128 list entries, stale after 220 ticks, validation every 20 ticks and dock scanning every 40 ticks. These limits do not change Create's native link range or update interval. Enable radar/display debug logging temporarily when diagnosing issues; ordinary script errors remain visible without it. See the [configuration reference](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/configuration.md).

## Migration

Since 1.4.0, the computer sits in the shared slot of a normal console. The old combined-block recipe is retired; legacy desks/items are adopted on load/placement. The four display tiers arrived in 1.5.0 and old display IDs use registry aliases. Radar uses the advanced display's controller selector rather than separate radar-display items.

The removed `cc_aeroworks:train_controls` item is not migrated; use native Create Train Controls. Old `aeroworks` globals and network-wide `getDesk...` facades are not the current API. Use `peripherals` on embedded computers and local `ControlDesk` on external computers.

## Troubleshooting

| Symptom | Check |
|---|---|
| Loader rejects the mod | Exact dependency ranges for your installed release; duplicate JARs |
| No computer interface | Sneak + right-click, insert a computer, then use its device button |
| `peripherals` is missing | Embedded computer only; external computers use `peripheral` |
| Neighbouring desks are missing | Explicit assembly membership, matching family, full loading and 64-segment limit |
| Unexpected `find` result | Use `findAll` for iteration; `ControlDesk` always returns a table |
| Blank display | Mounted socket, raster dimensions and exclusive radar/Lua controller |
| Touch does nothing | Advanced display plus advanced slot device, input assignment and console-control mode |
| Radar has no data | Native Data Link/filterer setup, source uniqueness and loaded radar group |
| Manual control is suppressed | Input activation, focus and any HARD Lua override |
| Telemetry is absent/stale | Source refresh, availability, alias and locked remote dock |

When reporting an [issue](https://github.com/TeutonStudio/CC-Aeroworks/issues), include the relevant mod versions, client/server environment, stationary/Sable/Create runtime, a minimal setup, reproduction steps, expected/actual behaviour and relevant logs or a small Lua program.

[Downloads](https://github.com/TeutonStudio/CC-Aeroworks/releases) · [[Displays|Displays-en]] · [[Lua API|API-en]] · [Example programs](https://github.com/TeutonStudio/CC-Aeroworks/tree/master/examples/cc)

The new in-game captures show local development 1.5.4 with TeutonLib 1.6.0. Recipe graphics are from 1.5.3. Screenshots use the German game interface; captions and instructions are provided in both languages.
