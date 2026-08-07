#!/usr/bin/env python3
"""Validate Create: Radars Data Link integration for desk-mounted Radar Displays."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MIXIN_CONFIG = ROOT / "src/main/resources/cc_aeroworks.mixins.json"
DATA_LINK_ITEM_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkItemMixin.java"
OLD_CONTROLLER_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarNetworkControllerMixin.java"
OLD_ENTITY_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkMixin.java"
COMPAT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt"
STATE_ACCESS = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/RadarDeskStateAccess.kt"
DESK_MIXIN = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt"
MODULE_TYPES = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt"
PONDER = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java"
PLUGIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java"
DOCS = ROOT / "docs/create-radars-integration.md"
TEST_PLAN = ROOT / "docs/radar-controller-test-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict:
    value = json.loads(read(path))
    require(isinstance(value, dict), f"{path} must contain a JSON object")
    return value


def main() -> int:
    mixins = load_json(MIXIN_CONFIG)
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar desk state mixin is missing")
    require(
        "compat.CreateRadarDataLinkItemMixin" in common_mixins,
        "Create: Radars Data Link target extension is not registered",
    )
    require(
        "compat.CreateRadarNetworkControllerMixin" not in common_mixins,
        "Obsolete adjacent Network Controller ticker integration is still registered",
    )
    require(DATA_LINK_ITEM_MIXIN.is_file(), "Data Link item mixin file is missing")
    require(not OLD_CONTROLLER_MIXIN.exists(), "Obsolete Network Controller mixin file remains")
    require(not OLD_ENTITY_MIXIN.exists(), "A Data Link block entity mixin should not be required")

    data_link_mixin = read(DATA_LINK_ITEM_MIXIN)
    for token in (
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockItem"',
        '@Inject(method = "getFilterTarget"',
        "CreateRadarCompat.isRadarDeskTarget(blockEntity)",
        '"com.happysg.radar.block.datalink.DataLinkBlockItem$FilterTarget"',
        '"com.happysg.radar.block.datalink.DataLinkBlockItem$FilterTargetKind"',
        '"MONITOR"',
        "callback.setReturnValue(monitorTarget)",
    ):
        require(token in data_link_mixin, f"Data Link monitor target extension is missing: {token}")
    require("useOn" not in data_link_mixin, "CC-Aeroworks must not replace the native Data Link useOn flow")
    require("BlockPlaceContext" not in data_link_mixin, "Data Link placement must remain native")

    compat = read(COMPAT)
    required_tokens = (
        "fun isRadarDeskTarget(candidate: Any?)",
        "fun refreshDesk(desk: ConsoleBlockEntity)",
        "NATIVE_MONITOR_INTERVAL_TICKS: Long = 5L",
        "SNAPSHOT_HEARTBEAT_TICKS",
        'invokeStaticLookup(NETWORK_DATA_CLASS, "get", level)',
        '"getFiltererForEndpoint"',
        'invokeLookup(networkData, "getGroup"',
        'readFieldLookup(group, "monitorEndpoints")',
        'readFieldLookup(group, "radarPos")',
        'readFieldLookup(group, "detectionTag")',
        'readFieldLookup(group, "selectedTargetId")',
        'invokeStaticLookup(DETECTION_CONFIG_CLASS, "fromTag", detectionTag)',
        'invokeLookup(filter, "test", raw)',
        'invokeStaticLookup(PHYSICS_HANDLER_CLASS, "getWorldVec", level, radarPos)',
        'invokeLookup(radar, "getTracks")',
        'invokeLookup(radar, "getRange")',
        'invokeLookup(radar, "isRunning")',
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
        "shouldSynchronize(previous, snapshot, level.gameTime)",
        "desk.notifyUpdate()",
        "TrackReadResult.Failure",
    )
    for token in required_tokens:
        require(token in compat, f"Native monitor endpoint synchronization is missing: {token}")

    for forbidden in (
        "refreshController",
        "adjacentDeskNetworks",
        "findAdjacentControllers",
        "MULTIPLE_CONTROLLERS",
        "NETWORK_CONTROLLER_BLOCK_ID",
        "radarCache",
        "radarPosCache",
        "cachedTracks",
        "detectedDestinations",
    ):
        require(forbidden not in compat, f"Obsolete adjacent-controller behavior remains: {forbidden}")

    state_access = read(STATE_ACCESS)
    desk_mixin = read(DESK_MIXIN)
    require("RadarControllerLink" not in state_access, "Controller positions must not be stored by CC-Aeroworks")
    require('@Inject(method = ["tick"]' in desk_mixin, "Desk endpoint does not follow the native monitor tick cycle")
    require("CreateRadarCompat.refreshDesk(this as ConsoleBlockEntity)" in desk_mixin, "Desk tick does not refresh its Data Link endpoint")
    require("RADAR_CONTROLLER_NBT_KEY" not in desk_mixin, "Legacy controller location is still persisted")

    module_types = read(MODULE_TYPES)
    for token in (
        "moduleTypeIdentities(moduleType)",
        "declaredFieldIdentities(moduleType)",
        "matchesModuleIdentity",
        "RadarDisplayType.SMALL.modulePath",
        "RadarDisplayType.LARGE.modulePath",
    ):
        require(token in module_types, f"Stable Radar Display classification is missing: {token}")

    english = load_json(LANG / "en_us.json")
    german = load_json(LANG / "de_de.json")
    require(set(english) == set(german), "German and English language keys differ")

    ponder = read(PONDER)
    plugin = read(PLUGIN)
    require("RadarDisplayScenes::controllerConnection" in plugin, "Radar connection scene is not registered")
    require("RadarDisplayScenes::directRadarDisplay" in plugin, "Radar endpoint scene is not registered")
    require('"create_radar", "network_filterer"' in ponder, "Ponder does not show the Network Controller")
    require('"create_radar", "data_link"' in ponder, "Ponder does not show the Data Link item")
    require(ponder.count("showText(") == 10, "Radar Ponder scenes must contain ten explanation steps")

    docs = read(DOCS)
    test_plan = read(TEST_PLAN)
    for token in (
        "SelectedFiltererPos",
        "NetworkData.attachMonitor",
        "monitorEndpoints",
        "getFiltererForEndpoint",
        "DetectionConfig",
        "physische Data-Link-Block",
        "5 Ticks",
        "256",
    ):
        require(token in docs, f"Native Data Link documentation is incomplete: {token}")
    require("Data Link auf das Pult" in docs, "Documentation does not describe linking the Radar Display desk")
    require("automatische Controller-Erkennung" not in docs, "Obsolete adjacency documentation remains")
    require("Data-Link-Endpoint" in test_plan, "Regression plan does not cover the desk endpoint")
    require("Data Link entfernen" in test_plan, "Regression plan does not cover native unlink cleanup")
    require("API_INCOMPATIBLE" in test_plan, "Regression plan does not cover API incompatibility")

    print(
        "Validated native Create: Radars filterer-first Data Link placement, desk monitor endpoint registration, "
        "five-tick NetworkData synchronization, filtering, cleanup contract and documentation."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError, ValueError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
