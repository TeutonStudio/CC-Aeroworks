# Changelog

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
