# Native Create: Radars Data-Link flow analysis

This note records the compatibility contract used by CC-Aeroworks before the native desk endpoint implementation. The supported runtime artifact is `create_radar-0.4.9.4-1.21.1.jar`, published as CurseForge file `8227753`; the project dependency manifest and NeoForge metadata pin that version.

The public CI verifier downloads that exact release JAR and inspects it with `javap`. It does not infer the runtime contract from a later branch or from an older CC-Aeroworks implementation. The exact artifact confirms:

- the filterer class is `com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity`;
- the Data Link stores `SelectedFiltererPos` and clears `SelectedMountPos`, `SelectedYawPos`, `SelectedPitchPos`, and `SelectedFiringPos` when selecting a filterer;
- the private monitor target helper has the descriptor recorded below;
- that helper contains exactly one `INSTANCEOF MonitorBlockEntity` instruction;
- the native `useOn(...)` path calls the monitor registration methods in `NetworkData`;
- the monitor resolves its group, restores `DetectionConfig`, filters the radar stream, and uses a five-tick long modulo cycle;
- `DataLinkBlock.onRemove(...)` calls the native Data-Link and endpoint cleanup methods.

## `DataLinkBlockItem`

The native entry point remains `useOn(UseOnContext)`.

Filter-network selection stores `SelectedFiltererPos` in the item's `DataComponents.CUSTOM_DATA`. Selecting a filterer clears the mount/controller keys `SelectedMountPos`, `SelectedYawPos`, `SelectedPitchPos`, and `SelectedFiringPos`. Shift-use clears the in-progress custom data.

For the second click, the item calls the private helper

```text
getFilterTarget(
  BlockEntity,
  BlockState
) : DataLinkBlockItem$FilterTarget
```

with bytecode descriptor:

```text
(Lnet/minecraft/world/level/block/entity/BlockEntity;
 Lnet/minecraft/world/level/block/state/BlockState;)
Lcom/happysg/radar/block/datalink/DataLinkBlockItem$FilterTarget;
```

Its first classification is an `INSTANCEOF com/happysg/radar/block/monitor/MonitorBlockEntity`; when true, native code constructs the private `FilterTargetKind.MONITOR` value itself. The exact release JAR contains one and only one such instruction in this helper. This single type check is the missing extension point for an Aeroworks desk.

After classification, the original filterer-first path:

1. reads `SelectedFiltererPos`;
2. computes the physical link position with `clickedPos.relative(clickedFace, clickedState.canBeReplaced() ? 0 : 1)`;
3. checks both filterer-to-link and endpoint-to-link distance using `RadarConfig.server().radarLinkRange` and `PhysicsHandler` world coordinates;
4. obtains `NetworkData.get(serverLevel)` and `getOrCreateGroup(dimension, filtererPos)`;
5. calls `canAttachMonitor(group, clickedPos)` for a monitor target;
6. delegates placement to `BlockItem.useOn(...)`;
7. verifies that the placed block is a `DataLinkBlock` and applies the native `RADAR` link style;
8. calls `attachMonitor(serverLevel, group, clickedPos)` and `addDataLinkToGroup(group, placedPos, clickedPos)`;
9. emits the native success/failure messages and clears the item custom data.

The exact bytecode verifier checks the calls to `getOrCreateGroup`, `canAttachMonitor`, `attachMonitor`, `addDataLinkToGroup`, `BlockItem.useOn`, and the native link-range configuration inside `useOn(...)`.

CC-Aeroworks must therefore not replace `useOn(...)`, place a link block, perform a second range check, persist a controller position, or create the private filter target reflectively. The compatibility Mixin redirects only the native monitor `INSTANCEOF` instruction. Native code still creates and consumes its private target object.

## `NetworkData`

`NetworkData` is a `SavedData` instance keyed per server level. Its authoritative maps include:

- filterer key to `Group`;
- endpoint position to filterer key;
- physical Data-Link position to filterer key;
- physical Data-Link position to endpoint position.

A `Group` exposes `monitorEndpoints`, `radarPos`, `detectionTag`, and `selectedTargetId`.

The desk integration uses the public native calls:

```text
NetworkData.get(ServerLevel)
getFiltererForEndpoint(ResourceKey<Level>, deskPos)
getGroup(ResourceKey<Level>, filtererPos)
```

A valid desk snapshot additionally requires the returned group to contain `deskPos` in `monitorEndpoints`.

`canAttachMonitor(...)` permits a free endpoint or one already owned by the same group. `attachMonitor(...)` adds the endpoint and updates the endpoint-to-filterer index. Because an Aeroworks desk is not a `MonitorBlockEntity`, its endpoint remains the clicked desk position, which is also the position recorded by `addDataLinkToGroup(...)`.

`DataLinkBlock.onRemove(...)` calls `removeDataLinkAndCleanup(...)` and then `onEndpointRemoved(...)`. The first method removes the physical link mapping and monitor endpoint; the second is a safety cleanup for the supporting endpoint. CC-Aeroworks does not mirror this state. On the next five-tick desk refresh, `getFiltererForEndpoint(...)` returns no filterer and the synchronized snapshot is cleared.

## `MonitorBlockEntity`

The native monitor refreshes on the server when `level.getGameTime() % 5 == 0`. In the release bytecode this appears as `getGameTime`, the long constant `5L`, and `lrem`, rather than an integer modulo sequence.

The native update path:

1. resolves its group through `getFiltererForEndpoint(...)` and `getGroup(...)`;
2. copies `radarPos`, `DetectionConfig.fromTag(group.detectionTag)`, and `group.selectedTargetId`;
3. resolves the `IRadar` at `radarPos`;
4. obtains `radar.getTracks()` and filters the stream with the monitor predicate;
5. writes client packet NBT.

The exact artifact contains the typed method `DetectionConfig.test(RadarTrack)` together with the Java compiler's `test(Object)` bridge. The desk adapter deliberately invokes the typed native behavior through the runtime object and does not reproduce its filtering rules.

Client packet NBT contains the radar position, filter state, serialized tracks, and selected-target state. The desk adapter follows the same global five-tick phase and writes its snapshot through the existing `ConsoleBlockEntity` client-update NBT path before calling `notifyUpdate()`.

## `NetworkFiltererBlockEntity`

The exact 0.4.9.4 release places the class at:

```text
com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity
```

This differs from paths used by some development branches. The runtime class exposes the filter state, radar association, and selected target consumed by `NetworkData.Group`. CC-Aeroworks does not reference this class directly; it reads the authoritative group state, which avoids making the optional compatibility boundary depend on the filterer's package name.

## Radar and filter data

`IRadar` supplies `getTracks()`, `getRange()`, `isRunning()`, and `getWorldPos()`. The monitor's world-space center uses `PhysicsHandler` for movable-world support. The desk adapter uses the exact `PhysicsHandler.getWorldVec(Level, BlockPos): Vec3` overload.

`DetectionConfig.fromTag(...)` restores player, Sable/VS2, contraption, mob, projectile, animal, item, and allow/deny-list settings. Each native `RadarTrack` is accepted only when `DetectionConfig.test(track)` returns true. A synchronized track retains its native ID, position, velocity, and category so the existing desk renderer can select the corresponding player, projectile, entity, or contraption sprite and compare the ID with `selectedTargetId`.

The exact artifact is checked for the `RadarTrack` accessors `getId`, `getPosition`, `getVelocity`, and `getTrackCategory`, plus the required `IRadar` methods.

## Optional-mod boundary

The Mixin is `@Pseudo`, targets Create: Radars by class name, and keeps Create: Radars classes out of handler signatures. Runtime API access is isolated behind class-name based reflection and is entered only after `ModList` confirms `create_radar` is loaded. Starting without Create: Radars must not resolve any Create: Radars class.

## Verification boundary

The exact JAR inspection proves the bytecode contract used by the Mixin and adapter. It does not prove that the transformed development client starts, that the optional-mod-free client starts, or that rendered contacts are visible in game.

Before the draft PR can be marked ready, the protected dependency build and development-client matrix must still demonstrate:

- successful startup with and without Create: Radars;
- successful Data Link placement on each supported desk type;
- `NetworkData` endpoint registration and native cleanup;
- visible filtered player, entity, projectile, contraption, and optional VS2/Sable tracks;
- filter changes and selected-target rendering;
- classic and Flywheel rendering without loss of Aeroworks controls.
