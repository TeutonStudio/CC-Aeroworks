# Native Create: Radars Data-Link flow analysis

This note records the compatibility contract used by CC-Aeroworks before the native desk endpoint implementation. The supported runtime artifact is `create_radar-0.4.9.4-1.21.1.jar`; the project dependency manifest and NeoForge metadata pin that version. The method and nested-class descriptors below were checked against the 1.21.1 source line used by the artifact and must also be verified from the resolved runtime JAR during `runClient`.

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

Its first classification is an `INSTANCEOF com/happysg/radar/block/monitor/MonitorBlockEntity`; when true, native code constructs the private `FilterTargetKind.MONITOR` value itself. This single type check is the missing extension point for an Aeroworks desk.

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

The native monitor refreshes on the server when `level.getGameTime() % 5 == 0`:

1. `syncFromNetwork(...)` resolves its group through `getFiltererForEndpoint(...)` and `getGroup(...)`;
2. it copies `radarPos`, `DetectionConfig.fromTag(group.detectionTag)`, and `group.selectedTargetId`;
3. `updateCacheServerOrClient()` resolves the `IRadar` at `radarPos`;
4. it filters `radar.getTracks()` with `DetectionConfig.test(RadarTrack)`;
5. `sendData()` writes client packet NBT.

Client packet NBT contains `HasRadarPos`, `radarPos`, `Filter`, and serialized `tracks`, together with selected target state. The desk adapter follows the same global five-tick phase and writes its snapshot through the existing `ConsoleBlockEntity` client-update NBT path before calling `notifyUpdate()`.

## Radar and filter data

`IRadar` supplies `getTracks()`, `getRange()`, `isRunning()`, and `getWorldPos()`. The monitor's world-space center uses `PhysicsHandler` for movable-world support.

`DetectionConfig.fromTag(...)` restores player, Sable/VS2, contraption, mob, projectile, animal, item, and allow/deny-list settings. Each native `RadarTrack` is accepted only when `DetectionConfig.test(track)` returns true. A synchronized track retains its native ID, position, velocity, and category so the existing desk renderer can select the corresponding player, projectile, entity, or contraption sprite and compare the ID with `selectedTargetId`.

## Optional-mod boundary

The Mixin is `@Pseudo`, targets Create: Radars by class name, and keeps Create: Radars classes out of handler signatures. Runtime API access is isolated behind class-name based reflection and is entered only after `ModList` confirms `create_radar` is loaded. Starting without Create: Radars must not resolve any Create: Radars class.

## Required runtime proof

Static source checks establish the intended extension point but do not prove transformed bytecode or gameplay. Before the draft PR can be marked ready, the resolved `0.4.9.4-1.21.1` JAR must show exactly one redirected monitor `INSTANCEOF`, the development client must start both with and without Create: Radars, and an in-game endpoint must produce visible filtered tracks and disappear after its physical Data-Link block is removed.
