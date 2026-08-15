# Sable ControlDesk Targeting

## Problem

A ControlDesk on a Sable SubLevel exists simultaneously in different coordinate spaces:

- the player eye, camera and visible ship position are in main-level/world coordinates;
- the desk `BlockPos`, `BlockEntity` and neighbouring desks of one ControlDesk multiblock are stored
  in Sable plot coordinates;
- the client renders a moving ship with an interpolated `ClientSubLevel.renderPose()`, which can be
  different from the tick-level `logicalPose()` between ticks.

Sable 2.0.1 already makes Vanilla block picking SubLevel-aware. Its `BlockGetter#clip` replacement
inverse-projects the world ray into every intersected SubLevel and returns the winning plot-local
`BlockHitResult`. During `GameRenderer#pick`, Sable temporarily supplies the current render pose so
Vanilla targets the ship where it is actually drawn.

The CC-Aeroworks bug was not that Vanilla could not raycast a Sable desk. The bug was that later
ControlDesk code mixed the spaces again: fallback view-ray scans used world coordinates to query
plot-local BlockEntities, reach checks compared a world-space player directly with plot positions,
and server permission checks passed plot positions into Vanilla APIs expecting world positions.

## Design rule

Every operation has one authoritative coordinate space. Conversions happen only at explicit
boundaries.

| Operation | Coordinate space |
| --- | --- |
| Sable/Vanilla `BlockHitResult.blockPos` for a SubLevel block | plot |
| `Level#getBlockEntity(deskPos)` | plot |
| `ConsoleMultiblockManager.resolve()` and desk adjacency | plot |
| packet desk identity (`BlockPos`) | plot |
| camera/player eye and initial view ray | world |
| client custom gaze against a moving ship | world -> render-pose plot |
| client/server interaction distance | Sable mixed-space helper |
| Vanilla permission check (`Level#mayInteract`) | projected world |
| rendering/visual interpolation | render pose |

The plot `BlockPos` is never replaced with a world position as the desk identity. A world projection
is temporary and exists only for an API which explicitly expects world coordinates.

## Implementation

### 1. Server-safe gameplay bridge

`compat/sable/SableSpatial.kt` contains only operations that are valid on both physical sides:

- `worldBlockPos(level, pos)` projects a plot position out of its SubLevel for Vanilla permission
  checks;
- `distanceSquared(level, first, second)` delegates to Sable's
  `distanceSquaredWithSubLevels`, allowing either endpoint to be plot-local.

It intentionally does not reference `ClientSubLevel` or render interpolation.

### 2. Client render-pose bridge

`compat/sable/SableClientSpatial.kt` owns custom client targeting conversions:

- `localRay(blockEntity, from, to)` inverse-projects one world ray into the current render pose of
  the SubLevel containing a known desk;
- `raySpaces(level, from, to)` returns the normal main-level ray plus a render-pose inverse projection
  for every intersected client SubLevel;
- `belongsTo(...)` prevents a plot-local corridor scan from accepting a block from another plot.

This code uses `ClientSubLevel.renderPose()` rather than `logicalPose()`. That matches Sable's own
frame-time Vanilla picking and avoids visible/interactive divergence while a ship translates or
rotates between game ticks.

### 3. Preserve Sable's Vanilla hit semantics

`CombinedInputContext.directCandidate()` and the primary path in `resolveNetwork()` keep using
`minecraft.hitResult.blockPos` directly.

This is intentional. For a Sable block that position is already plot-local, which is exactly what
`Level#getBlockEntity` and `ConsoleMultiblockManager` require. Projecting it to world coordinates
would destroy the valid Vanilla hit instead of fixing it.

Aeroworks' own `ConsoleBlockEntity.nearestMount(...)` remains the socket/module tie breaker after a
Desk has been identified. No global Minecraft or Sable raycast mixin is added.

### 4. Repair the visual-module fallback

Some large display geometry does not necessarily own Vanilla collision. The narrow fallback corridor
therefore still exists, but it no longer scans only world coordinates.

For each acquisition edge it now:

1. builds the world-space eye-to-reach ray;
2. obtains the main-level ray and render-pose inverse projections for intersected Sable SubLevels;
3. samples the same narrow 3x3x3 corridor in each ray space;
4. verifies that a sampled position belongs to exactly that ray's plot space;
5. resolves the first `ConsoleBlockEntity` and then the canonical multiblock.

The scan remains outside the hot mouse-delta path.

### 5. Repair display surface ray coordinates

`DisplayCombinedInputController` previously passed player world coordinates directly into
`DeskDisplayGeometry.resolveRay`, whose desk geometry is plot-local. The ray is now converted with
`SableClientSpatial.localRay` first. Normal-level desks are unchanged because the helper returns the
original ray when no SubLevel exists.

### 6. Repair reach validation

All Combined client watchdogs and all corresponding server payload handlers now compare the player
to multiblock members with `SableSpatial.distanceSquared`.

This preserves the existing rule that standing near any member of the same desk multiblock keeps the
session valid, while making that rule work for plot-local desk positions.

### 7. Repair server permission checks

Packet payloads continue to use the plot-local desk position for:

- chunk-loaded validation;
- `BlockEntity` lookup;
- socket/module lookup;
- multiblock resolution.

Only the `Level#mayInteract` argument is projected to a visible world `BlockPos`, because Vanilla
spawn/world permission logic is defined in world space.

## Explicit non-goals

- Do not overwrite or mix into global `BlockGetter#clip`. Sable already owns that compatibility
  boundary and correctly supplies its render pose during Vanilla picking.
- Do not convert stored desk addresses to world coordinates. A moving ship would make such identities
  unstable.
- Do not run SubLevel enumeration during mouse movement. Acquisition remains edge-driven and cached.
- Do not use client render interpolation for server authorization. The server uses logical gameplay
  state through Sable's public helper functions.

## Regression matrix

### Normal world

1. Create a 3+ desk ControlDesk multiblock on the main level.
2. Enter control mode by targeting the left, middle and right desk separately.
3. Select ambiguous Combined bindings by gaze.
4. Use a large display pointer and tap/double-tap.

Expected: behaviour remains identical to the pre-Sable fix.

### Static Sable SubLevel

1. Put the same multiblock on a stationary Sable structure.
2. Target every desk member from several angles.
3. Confirm that the Vanilla outline, selected desk and selected module agree.
4. Move within reach of a different member while keeping the Combined session active.

Expected: the plot-local desk identity resolves correctly and reach remains valid across the entire
multiblock.

### Translation

1. Move the Sable structure continuously in a straight line.
2. Sweep the crosshair over each desk and activate Combined controls repeatedly.
3. Activate the large display pointer near the left/right edges of its visible surface.

Expected: acquisition follows the rendered ship without a tick-lag offset; display pointer start
coordinates correspond to the visible surface.

### Rotation

1. Rotate the Sable structure continuously while the player remains in interaction range.
2. Repeat desk and display targeting on every multiblock member.

Expected: no world/plot axis confusion, no stale logical-pose targeting and no selection of an
unrelated plot coordinate.

### Translation plus rotation

Repeat the above with simultaneous motion and rotation. This is the strongest regression case because
`logicalPose()` and `renderPose()` diverge most visibly between ticks.

### Server authorization

For normal and Sable desks:

1. send normal Combined samples and display taps while in range;
2. move beyond range and confirm rejection;
3. stand near a different member of the same multiblock and confirm acceptance;
4. confirm spawn/world permission restrictions are evaluated at the visible world position.

## Automated contract

`tools/verify-sable-desk-targeting.py` statically enforces the architectural boundaries above. It
specifically rejects regressions back to raw `player.distanceToSqr(plotPos)`, raw plot positions in
`Level#mayInteract`, client targeting based on `logicalPose()`, and projection of Sable's valid
Vanilla hit before BlockEntity/multiblock lookup.
