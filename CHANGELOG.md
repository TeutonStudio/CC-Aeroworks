# Changelog

## 1.4.7 — 2026-10-23

- Fixed channel, wire, group and binding name fields losing keyboard focus after opening the Channels tab. Text entry now works immediately and remains protected from the inventory hotkey.

## 1.4.6 — 2026-10-22

- Fixed the inventory key closing the computer screen when its character was typed into channel, wire, group or binding name fields. Active text fields now retain the character, while Escape continues to navigate back.

## 1.4.5 — 2026-10-22

- Fixed HARD computer overrides being restored only after Aeroworks had already sprung and published unattended controls. Overrides are now re-applied immediately before the Redstone Link network push, including controls hosted by another desk in the same console network.
- TeutonLib 1.3.0 is now required on clients and servers (supported range: >=1.3.0, <1.4.0).
- Small and large radar displays are back in the CC-Aeroworks section of the Aeroworks creative tab.
- Rebuilt all four display item models as flat mounted modules with active digit or radar surfaces and revised inventory/hand transforms.
- Redesigned all eight Ponder scenes around complete computer, display and radar workflows, including control override and fail-safe behaviour.

## 1.4.2 — 2026-10-21

- TeutonLib 1.2.2 is now required on clients and servers (supported range: >=1.2.2, <1.3.0).
- Added server settings `display.useGlobalResolution` (default true) and `display.maxIndividualPpb` (default 256, range 16–1024). Disable global resolution to configure each display slot through `setDisplayResolution(socket, ppb)` or `display.setResolution(event, ppb)`.
- Individual resolutions persist across saves and synchronize to clients. Changing density clears the pixel raster; setting the same effective density preserves it. Removing a module resets its slot setting.
- Small and large displays support touch and draw only with an advanced computer in the active console network. Normal or absent computers disable touch, including events delivered to external peripherals. Script bindings survive computer replacement.
- Display geometry, pixel APIs and module descriptions use the effective slot resolution. Stale draw gestures are rejected after resolution or computer changes. Radar displays retain their resolution logic.
- Fixed the Aeroworks placement preview crashing on existing or held CC-Aeroworks display modules because their partial models were registered after the initial model bake.
- Added integration coverage for mounted displays, persistence, independent slots, computer tiers and external event delivery, plus Lua helper tests. See [verification](docs/test-results/release-1.4.2.md).

## 1.4.1 — 2026-10-18

- TeutonLib 1.2.0 is now required on clients and servers, using a shared NeoForge 21.1.234 baseline.
- Computer desks use shared TeutonLib tabs and panels: Controls, Sources, Channels. Existing channel groups, DbW targets, information sources and script diagnostics are preserved.
- Menu and panel sessions isolate snapshots; desk persistence and Lua APIs remain owned by Aeroworks.
- Fixed the embedded control desk overview drawing an additional background gradient over the computer slot and player inventory.


## 1.4.0 — 2026-10-18

- Computers now use a shared inventory slot on normal control desks. Existing computer desks migrate automatically; removing desks preserves computer data during transfer and drops the computer separately when the last desk is removed.

- Normal control modules can bind Lua scripts in their settings and trigger them on input changes, including releases and signed axis values. See [control scripts](docs/control-scripts.md).
- Channels now offers a direct coordinate and target-face editor alongside the Cable workflow, including loading and removing existing targets and respecting cable consumption.
- Fixed Aeroworks channel cycling cancelling the logical multiblock handler, which caused a target click to restore the first channel after scrolling.

## 1.3.4 — 2026-10-11

- Radar and display diagnostics are disabled by default, including when the logger allows DEBUG output.
- Added independent `debug.radar` and `debug.display` switches to the server configuration. Enabled diagnostics appear at INFO on the server and connected clients without additional logger configuration.
- Radar trace messages, NBT dumps and reflective diagnostic summaries are evaluated only when needed. Periodic traces also defer message creation until their interval is due.
- Genuine warnings, errors, startup messages and Lua display-script diagnostics remain available. High-frequency display draw samples remain filtered.
- Fixed radar source selection on Sable ships by using projected interaction distances while preserving server-side ownership and reach checks.
- Radar source choices now show live connection status and the server-confirmed selection. Inactive radar surfaces explain missing links, missing radar assignments and stale data instead of remaining blank.
- Fixed category banners in the Aeroworks creative tab covering the lower slot border; the original slot bevel now remains visible beneath each category row.
- Documented the server config location and world-specific overrides, opt-in radar capture, and recovery from Gradle's modified immutable-workspace error.

Minecraft version and dependencies are unchanged from 1.3.3. No world-data migration is required. See [configuration](docs/configuration.md), [Gradle troubleshooting](docs/gradle-troubleshooting.md), and [release verification](docs/test-results/release-1.3.4.md).
