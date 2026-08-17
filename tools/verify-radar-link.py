#!/usr/bin/env python3
"""Validate the native Create: Radars Data-Link endpoint contract."""

from __future__ import annotations

import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_BRANCH = "master"
MIXIN_CONFIG = ROOT / "src/main/resources/cc_aeroworks_radarcompat.mixins.json"
TARGET_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/radarcompat/mixin/createradar/CreateRadarDataLinkTargetMixin.java"
OLD_CONTROLLER_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/radarcompat/mixin/createradar/CreateRadarNetworkControllerMixin.java"
COMPAT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/createradar/CreateRadarCompat.kt"
DESK_MIXIN = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/mixin/ConsoleBlockEntityRadarMixin.kt"
SNAPSHOT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/display/RadarDisplaySnapshot.kt"
DESK_ACCESS = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/compat/aeroworks/RadarDeskAccess.kt"
CLIENT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt"
RADAR_CLIENT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/RadarCompatClient.kt"
CLASSIC_RENDERER = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/ComputerControlDeskRenderer.kt"
PONDER = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/radarcompat/client/ponder/RadarDisplayScenes.java"
DOCS = ROOT / "docs/create-radars-integration.md"
ANALYSIS = ROOT / "docs/create-radars-native-flow-analysis.md"
TEST_PLAN = ROOT / "docs/radar-controller-test-plan.md"
WIKI = ROOT / "wiki/Radar-Routing.md"
BUILD_GRADLE = ROOT / "build.gradle"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict:
    value = json.loads(read(path))
    require(isinstance(value, dict), f"{path} must contain a JSON object")
    return value


def verify_branch_origin() -> None:
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path or not Path(event_path).is_file():
        return

    payload = json.loads(Path(event_path).read_text(encoding="utf-8"))
    pull_request = payload.get("pull_request")
    if not isinstance(pull_request, dict):
        return

    base = pull_request.get("base")
    base_ref = base.get("ref") if isinstance(base, dict) else None
    require(base_ref == BASE_BRANCH, f"Pull request targets {base_ref}, expected {BASE_BRANCH}")


def main() -> int:
    verify_branch_origin()

    mixins = load_json(MIXIN_CONFIG)
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar desk state mixin is missing")
    require(
        "createradar.CreateRadarDataLinkTargetMixin" in common_mixins,
        "Native Data Link monitor-classification mixin is missing",
    )
    mixinextras = mixins.get("mixinextras")
    require(isinstance(mixinextras, dict), "MixinExtras version contract is missing from mixin config")
    require(mixinextras.get("minVersion") == "0.5.0", "MixinExtras 0.5.0 expression support is not pinned")
    for forbidden in (
        "createradar.CreateRadarNetworkControllerMixin",
        "createradar.CreateRadarNetworkControllerLinkMixin",
        "createradar.CreateRadarDataLinkMixin",
        "createradar.CreateRadarDataLinkItemMixin",
    ):
        require(forbidden not in common_mixins, f"Obsolete radar mixin remains registered: {forbidden}")
    require(TARGET_MIXIN.is_file(), "Native Data Link target mixin file is missing")
    require(not OLD_CONTROLLER_MIXIN.exists(), "Adjacent Network Controller tick mixin still exists")

    target_mixin = read(TARGET_MIXIN)
    for token in (
        "@Pseudo",
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockItem"',
        "@Expression(\"? instanceof ?\")",
        "@ModifyExpressionValue(",
        'method = "getFilterTarget(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/happysg/radar/block/datalink/DataLinkBlockItem$FilterTarget;"',
        'at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0)',
        "require = 1",
        "expect = 1",
        "boolean nativeMonitor",
        "BlockEntity candidate",
        "boolean isDesk = candidate instanceof ConsoleBlockEntity",
        "RadarDeskAccess.hasRadarDisplay((ConsoleBlockEntity) candidate)",
        "boolean accepted = nativeMonitor || hasRadarDisplay",
        '"DL_CLASSIFY"',
        "return accepted",
    ):
        require(token in target_mixin, f"Native target classification is missing: {token}")
    for forbidden in (
        "@Redirect(",
        'value = "INSTANCEOF"',
        "ccaeroworks$nativeMonitorClass",
        "ccaeroworks$isNativeMonitor",
        "useOn(",
        "UseOnContext",
        "BlockPlaceContext",
        "SelectedFiltererPos",
        "NetworkData",
        "Class.forName",
        "getDeclaredConstructor",
        "FilterTargetKind",
        "newInstance(",
    ):
        require(forbidden not in target_mixin, f"Target mixin takes over native work or uses an invalid injection: {forbidden}")

    build_gradle = read(BUILD_GRADLE)
    require("mavenCentral()" in build_gradle, "MixinExtras compile-only repository is missing")
    require(
        'compileOnly("io.github.llamalad7:mixinextras-neoforge:0.5.0")' in build_gradle,
        "MixinExtras compile-only API dependency is missing",
    )

    desk_access = read(DESK_ACCESS)
    require("fun hasRadarDisplay(desk: ConsoleBlockEntity)" in desk_access, "RadarDisplay presence check is missing")
    require("radarDisplayType(desk, it) != null" in desk_access, "Desk target is not tied to a mounted RadarDisplay")

    compat = read(COMPAT)
    required_compat = (
        "fun refreshDesk(desk: ConsoleBlockEntity)",
        "level.gameTime % NATIVE_MONITOR_INTERVAL_TICKS",
        '"getFiltererForEndpoint"',
        "level.dimension(),",
        "desk.blockPos",
        '"getGroup"',
        'readField(group, "monitorEndpoints")',
        'readField(group, "radarPos")',
        'readField(group, "detectionTag")',
        'readField(group, "selectedTargetId")',
        '"com.happysg.radar.block.behavior.networks.NetworkData"',
        '"com.happysg.radar.block.behavior.networks.config.DetectionConfig"',
        '"com.happysg.radar.block.radar.track.RadarTrackUtil"',
        'invokeStatic(DETECTION_CONFIG_CLASS, "fromTag"',
        'invokePublic(filter, "test", raw)',
        'invokePublic(radar, "getTracks")',
        'invokePublic(radar, "getRange")',
        'invokePublic(radar, "isRunning")',
        'invokeStatic(RADAR_TRACK_UTIL_CLASS, "serializeNBTList", filtered)',
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
        "nativeTracks = nativeTracks.tag",
        "trackCount = nativeTracks.count",
        "desk.notifyUpdate()",
        "filteredTracks={}",
        '"S11_FILTERER_LOOKUP"',
        '"S12_MONITOR_ENDPOINTS"',
        '"S13_GROUP_STATE"',
        '"S15_RADAR_STATE"',
    )
    for token in required_compat:
        require(token in compat, f"NetworkData/native monitor synchronization is missing: {token}")
    for forbidden in (
        "adjacentDeskNetworks",
        "findAdjacentControllers",
        "refreshController",
        "NETWORK_CONTROLLER_BLOCK_ID",
        "controllerPos.relative",
        "controllers.size",
        "ConsoleMultiblockManager",
        "detectedDestinations.ifEmpty",
        "network.desks",
        "radarCache",
        "cachedTracks",
        "activeTrackCache",
        "SelectedFiltererPos",
        "RadarDisplayTrack",
        "RadarDisplayTrackSprite",
        "sendBlockUpdated(",
    ):
        require(forbidden not in compat, f"Forbidden adjacency or parallel radar model remains: {forbidden}")

    desk_mixin = read(DESK_MIXIN)
    require('method = ["tick"]' in desk_mixin, "Desk block entity does not run the endpoint refresh")
    require("CreateRadarCompat.refreshDesk" in desk_mixin, "Desk tick does not call the native endpoint adapter")
    require("clientPacket" in desk_mixin and "notifyUpdate" not in desk_mixin, "Snapshot NBT path drifted")
    require("RADAR_CONTROLLER_NBT_KEY" not in desk_mixin, "A controller position is still persisted")
    require('"N10_SERVER_WRITE_ENTER"' in desk_mixin and '"N21_CLIENT_READ_DECODED"' in desk_mixin, "NBT transport tracing is incomplete")

    snapshot = read(SNAPSHOT)
    for token in (
        "val operational: Boolean",
        "val radarPos: BlockPos?",
        "val detectionTag: CompoundTag",
        "val selectedTrackId: String?",
        "val nativeTracks: CompoundTag",
        "val trackCount: Int",
        "val status: RadarLinkStatus",
        'put("detection"',
        'put("radarPos"',
        'put("tracks", nativeTracks.copy())',
    ):
        require(token in snapshot, f"Native monitor state is not synchronized: {token}")
    require("RadarDisplayTrack" not in snapshot, "Snapshot still defines a parallel RadarTrack type")

    client = read(CLIENT)
    radar_client = read(RADAR_CLIENT)
    classic = read(CLASSIC_RENDERER)
    require("SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())" in client, "Computer desk Flywheel visual is not registered")
    require("ConsoleVisual(context, blockEntity, partialTick)" in client, "Computer desk does not preserve native Aeroworks ConsoleVisual")
    require("RadarOverlayRenderer" not in client, "Core client bootstrap still owns radar rendering")
    require("RadarOverlayRenderer::renderLevel" in radar_client, "Radar compat client does not register shared native radar overlay")
    require("ConsoleRenderer" in classic and "DeskDisplayRenderer.render" in classic, "Classic computer desk render fallback is missing")

    ponder = read(PONDER)
    require('"create_radar", "network_filterer"' in ponder, "Ponder does not show the Network Filterer")
    require('"create_radar", "data_link"' in ponder, "Ponder does not show the physical Data Link")
    require("scene.world().setBlock(physicalLink" in ponder, "Ponder never places the physical Data Link")
    require("hideSection(util.select().position(physicalLink)" in ponder, "Ponder never demonstrates native link removal")

    docs = read(DOCS)
    analysis = read(ANALYSIS)
    test_plan = read(TEST_PLAN)
    wiki = read(WIKI)
    for source, tokens in (
        (docs, ("SelectedFiltererPos", "canAttachMonitor", "monitorEndpoints", "DetectionConfig.test", "removeDataLinkAndCleanup", "runClient")),
        (analysis, ("getFilterTarget", "INSTANCEOF", "addDataLinkToGroup", "MonitorBlockEntity", "0.4.9.4-1.21.1")),
        (test_plan, ("NetworkData", "Detection-Filter", "ComputerControlDesk", "Flywheel", "NICHT GETESTET")),
        (wiki, ("physischen Data-Link-Block", "NetworkData", "DetectionConfig", "Create: Radars den Monitorendpoint")),
    ):
        for token in tokens:
            require(token in source, f"Radar documentation is incomplete: {token}")
    for source in (docs, test_plan, wiki):
        for forbidden in ("direkt angrenzenden Network Controller", "automatische Controller-Erkennung", "sechs direkten Nachbarpositionen"):
            require(forbidden not in source, f"Obsolete adjacency documentation remains: {forbidden}")

    print(
        "Validated the master-target branch contract, traced MixinExtras monitor classification, NetworkData endpoint state, "
        "native DetectionConfig filtering/RadarTrack payloads, physical-link cleanup and computer desk visuals."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
