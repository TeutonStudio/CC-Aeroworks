# CC-Aeroworks

CC-Aeroworks connects **Create: Aeroworks Control Desks** with **CC:Tweaked**, turning a row of control desks into a programmable cockpit network. It adds embedded computer desks, addressable desk peripherals, network-wide Lua APIs, programmable displays with touch/draw input, Create telemetry, control channels, and optional Create: Radars integration.

## Core features

- Linear networks of normal Aeroworks desks and computer control desks.
- Normal or advanced CC:Tweaked computers embedded directly into a desk.
- Every desk remains an individual `ControlDesk` peripheral with its own position, modules, inputs, and displays.
- Embedded-computer APIs: `peripherals`, `channels`, `controls`, `wires`, and `telemetry`.
- Small and large programmable display modules supporting text, numbers, and pixel rasters.
- Touch and continuous draw input on large displays through the combined 3D pointer input mode.
- Create Display Links exposed as structured telemetry sources.
- Optional Create: Radars support isolated in the `cc_aeroworks_radarcompat` module.
- An in-game manual/API reference and localized Create Ponder scenes.

## Desk networks and peripherals

Aligned desks connect directly left and right into one control-desk network. The network resolver never force-loads chunks and accepts at most 64 fully loaded desks. A valid network contains at most one embedded computer desk.

Each desk is addressed by its canonical `x,y,z` position. Adjacent CC:Tweaked peripherals are discovered automatically and delegated through normal CC:Tweaked attach/detach, event, mount, and main-thread semantics.

The embedded computer exposes the `peripherals` API. Common entry points include:

- `peripherals.find(type)` for a unique handle or a keyed collection when several devices match;
- `peripherals.findAll(type)` when a collection is always required;
- `peripherals.wrap(...)` for position/side based lookup;
- `peripherals.getTree()` for the hierarchical desk/peripheral topology;
- `peripherals.getNetwork()` and `peripherals.getTypes()` for diagnostics.

Example: [`examples/cc/network-basics.lua`](examples/cc/network-basics.lua)

The previous global `aeroworks` API and the old network-wide desk/display facades are no longer part of the public contract.

## Control APIs

New cockpit automation should normally use `channels`, which provides stable high-level channel paths. `controls` exposes native Aeroworks control overrides in the signed `-15..15` range. `wires` manages user-defined Redstone/Drive By Wire outputs in the `0..15` range and only provides physical Drive By Wire output when the corresponding mod is installed.

Examples are collected in [`examples/cc/`](examples/cc/), including channel, control override, wire, dashboard, telemetry, pixel, and touch/draw programs.

## Programmable displays

CC-Aeroworks registers a small **Two Digit Display** and a large **Three Digit Display**. Display resolution is derived from `display.ppb` in **Parts per Block**, keeping logical pixels physically square at every supported aspect ratio. The default is `256 PPB`.

At 256 PPB the usable raster is:

- small display: `112 × 112` pixels (`7/16 × 7/16` block);
- large display: `160 × 112` pixels (`10/16 × 7/16` block).

Programs should query the current size through the display API rather than hard-coding these values. Pixel state is packed and rendered through cached dynamic textures, so high PPB values do not require one rendered model instance per logical pixel.

Large displays can bind an input script. The combined input mode moves a virtual 3D pointer across the display surface. Left click produces `tap`; holding the right mouse button produces a sequenced `draw` gesture with normalized coordinates, direction, speed, bounded path samples, and an explicit end event.

Useful Lua helpers include `touchdisplay.normalizedPosition(event)`, `drawSamples(event)`, and `drawStroke(event)`. See [`docs/display-touch.md`](docs/display-touch.md) and [`examples/cc/touch-test.lua`](examples/cc/touch-test.lua).

## Telemetry

Create Display Links targeting a computer control desk expose their source through `telemetry` as structured data rather than re-parsed formatted text. Supported sources include tank/fluid and inventory/item information. Multiple links may target the same computer and keep stable identities and revision/freshness metadata.

With optional Create: Simulated, docking connectors can relay telemetry from a separate Sable module without requiring another CC:Tweaked computer on the remote module.

## Optional Create: Radars integration

Radar-specific code is isolated under `de.teutonstudio.ccaeroworks.radarcompat` and activates only when Create: Radars is present. Supported radar displays are native Aeroworks desk modules and can use Create: Radars Data Link endpoints. Radar source, embedded computer, and display may be located on different desks of the same network.

See [`docs/create-radars-integration.md`](docs/create-radars-integration.md).

## Documentation

- Public Lua/peripheral API: [`docs/cc-peripheral-api.md`](docs/cc-peripheral-api.md)
- Peripheral tree: [`docs/peripheral-tree.md`](docs/peripheral-tree.md)
- Display touch/draw: [`docs/display-touch.md`](docs/display-touch.md)
- Telemetry: [`docs/telemetry.md`](docs/telemetry.md)
- Drive By Wire channels: [`docs/wire-channels.md`](docs/wire-channels.md)
- Runnable examples: [`examples/cc/README.md`](examples/cc/README.md)

## Development baseline

- Minecraft 1.21.1
- NeoForge 21.1.228+
- Java 21
- Kotlin 2.2.20 / KotlinForForge NeoForge 5.11.0
- Create 6.0.10
- Aeronautics/Aeroworks 1.3.0
- CC:Tweaked API baseline 1.119.0
- Sable 2.0.1 required
- Create: Simulated optional
- Create: Radars optional

Repository contract checks live under [`tools/`](tools/). The protected integration profile performs dependency verification, unit tests, a full Gradle build, and a dedicated-server smoke test before a release artifact is accepted.
