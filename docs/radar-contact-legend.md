# Large RadarDisplay contact legend

The large RadarDisplay adds a compact vertical legend to the right of Create: Radars' native radar circle. The small RadarDisplay remains unchanged.

## Displayed categories

The legend follows the Network Filterer's effective detection categories:

| Label | Create: Radars track category |
| --- | --- |
| `PLY` | `PLAYER` |
| `SHP` | `VS2` |
| `CTR` | `CONTRAPTION` |
| `MOB` | `MOB` and `HOSTILE` |
| `PRJ` | `PROJECTILE` |
| `ANI` | `ANIMAL` |
| `ITM` | `ITEM` |

`MISC` is not shown because the native `DetectionConfig` does not accept it. `HOSTILE` is grouped with `MOB` because the native Network Filterer uses the same mob switch for both categories.

Counts below 100 are rendered with two digits. Values from 100 upward are displayed as `99+` so the fixed-width legend remains inside the physical large-display surface.

## Data source

The legend does not query entities or rebuild the Network Filterer's rules. It reads the exact native `RadarTrack` payload already synchronized for the virtual Create: Radars monitor and delegates decoding to `RadarTrackUtil.deserializeListNBT(...)`. Each decoded track's native `getTrackCategory()` result is then aggregated into the seven legend rows.

This intentionally makes the legend describe the same filtered contact set that the native radar circle receives. The existing `RadarDisplaySnapshot.MAX_SYNCED_TRACKS` limit of 256 therefore also limits the legend count. If the server sees more accepted tracks than are synchronized, the legend still stays consistent with the rendered radar rather than reporting a different total.

## Optional-mod boundary

`RadarLegendRenderer` does not import Create: Radars classes. `RadarTrackUtil` and `RadarTrack` are resolved by class name only after the radar overlay is active. The exact pinned Create: Radars runtime contract already verifies both `RadarTrackUtil.deserializeListNBT(CompoundTag)` and `RadarTrack.getTrackCategory()`.

A contract failure suppresses the legend and logs a deduplicated warning instead of breaking normal CC-Aeroworks rendering.

## Rendering

The legend is rendered in the same `AFTER_BLOCK_ENTITIES` overlay pass as the native radar surface and only after `CreateRadarNativeMonitorRenderer.render(...)` reports a successful draw. It uses the Minecraft font at full brightness with polygon offset so the text sits slightly above the module surface without Z-fighting.

The seven rows are:

```text
PLY 00
SHP 00
CTR 00
MOB 00
PRJ 00
ANI 00
ITM 00
```

## Manual checks

At minimum verify the following in `runClient`:

1. A small RadarDisplay still shows only the native radar surface.
2. A large RadarDisplay shows the seven-row legend to the right of the circle.
3. A player increments `PLY`.
4. A hostile mob increments `MOB`, not a separate hostile row.
5. Animals, items, projectiles, contraptions and VS2/Sable ships increment their matching rows.
6. Disabling a Network Filterer category removes both the native contact and its legend count on the next synchronized update.
7. North, east, south and west desk orientations keep the legend aligned with the module.
8. Flywheel on and off produce the same legend because both paths use the shared overlay.
9. Removing the Data Link or stopping the radar removes the legend with the native surface.
10. A runtime without Create: Radars still starts without class-loading failures.
