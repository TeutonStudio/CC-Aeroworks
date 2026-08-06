#!/usr/bin/env python3
"""Validate automatic adjacent Network Controller radar integration."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MIXIN_CONFIG = ROOT / "src/main/resources/cc_aeroworks.mixins.json"
CONTROLLER_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarNetworkControllerMixin.java"
ITEM_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarNetworkControllerLinkMixin.java"
OLD_ITEM_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkItemMixin.java"
OLD_ENTITY_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkMixin.java"
COMPAT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt"
STATE_ACCESS = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/RadarDeskStateAccess.kt"
DESK_MIXIN = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt"
MODULE_TYPES = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt"
PONDER = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java"
PLUGIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java"
DOCS = ROOT / "docs/create-radars-integration.md"
TEST_PLAN = ROOT / "docs/radar-controller-test-plan.md"
OLD_TEST_PLAN = ROOT / "docs/radar-link-test-plan.md"


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
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar desk mixin is missing")
    require(
        "compat.CreateRadarNetworkControllerMixin" in common_mixins,
        "Network Controller tick mixin is missing",
    )
    require(
        "compat.CreateRadarNetworkControllerLinkMixin" not in common_mixins
        and "compat.CreateRadarDataLinkItemMixin" not in common_mixins
        and "compat.CreateRadarDataLinkMixin" not in common_mixins,
        "A Data Link mixin is still registered",
    )
    require(CONTROLLER_MIXIN.is_file(), "Network Controller tick mixin file is missing")
    require(not ITEM_MIXIN.exists(), "Controller-to-desk Data Link item mixin still exists")
    require(not OLD_ITEM_MIXIN.exists(), "Legacy Data Link item mixin still exists")
    require(not OLD_ENTITY_MIXIN.exists(), "Legacy Data Link block entity mixin still exists")

    controller_mixin = read(CONTROLLER_MIXIN)
    require(
        'targets = "com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity"' in controller_mixin,
        "Controller mixin targets the wrong optional class",
    )
    require(
        'method = "tick(Lnet/minecraft/world/level/Level;' in controller_mixin,
        "Controller mixin does not target the public static block ticker",
    )
    require("@Coerce Object controller" in controller_mixin, "Optional controller argument is not coerced safely")
    require("CreateRadarCompat.refreshController(controller)" in controller_mixin, "Controller ticker does not refresh desks")

    compat = read(COMPAT)
    required_tokens = (
        "fun refreshController(controller: Any)",
        "SNAPSHOT_INTERVAL_TICKS",
        "SNAPSHOT_HEARTBEAT_TICKS",
        "adjacentDeskNetworks(level, controllerEntity.blockPos)",
        "controllerPos.relative(direction)",
        "val updateOwner = controllers.minByOrNull",
        "if (updateOwner.blockPos != tickingController.blockPos) return",
        "findAdjacentControllers(level, network.desks)",
        "controllers.size > 1",
        "RadarLinkStatus.MULTIPLE_CONTROLLERS",
        "for (direction in Direction.values())",
        "desk.blockPos.relative(direction)",
        "controllers.putIfAbsent(candidate.blockPos, candidate)",
        "controllers += tickingController",
        'private const val NETWORK_CONTROLLER_BLOCK_ID: String = "create_radar:network_filterer"',
        "BuiltInRegistries.BLOCK.getKey(candidate.blockState.block)",
        'invokeDeclaredLookup(controller, "getRadar", level)',
        'readFieldLookup(controller, "radarCache")',
        'readFieldLookup(controller, "radarPosCache")',
        'invokeLookup(radar, "getTracks")',
        'invokeLookup(radar, "getRange")',
        'invokeLookup(radar, "isRunning")',
        'invokeLookup(radar, "getWorldPos")',
        "filter(AeroworksDeskAccess::hasRadarDisplay)",
        "shouldSynchronize(previous, snapshot, level.gameTime)",
        "destination.notifyUpdate()",
        "state == ConsoleNetworkState.ACTIVE || state == ConsoleNetworkState.NONE",
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
        "RadarResolution.Failure",
        "TrackReadResult.Failure",
    )
    for token in required_tokens:
        require(token in compat, f"Adjacent controller integration is missing contract token: {token}")

    for forbidden in (
        "DataComponents",
        "CustomData",
        "SelectedFiltererPos",
        "handleControllerLink",
        "RadarControllerLink",
        "MonitorBlockEntity",
        "DataLinkBlockEntity",
        "BlockPlaceContext",
        "getTargetPosition",
        "getSourcePosition",
        "fun capture(",
        "sendBlockUpdated(",
        "detectedDestinations.ifEmpty",
        "controllers.size == 1",
        'invokeDeclared(controller, "getRadar", level)',
        'readField(controller, "radarCache")',
    ):
        require(forbidden not in compat, f"Removed or unreliable radar behavior remains: {forbidden}")

    state_access = read(STATE_ACCESS)
    desk_mixin = read(DESK_MIXIN)
    require("RadarControllerLink" not in state_access, "Controller position model is still persisted")
    require("getRadarControllerLink" not in state_access, "Controller link getter still exists")
    require("setRadarControllerLink" not in state_access, "Controller link setter still exists")
    require("RADAR_CONTROLLER_NBT_KEY" not in desk_mixin, "Controller position is still stored in desk NBT")
    require("CreateRadarCompat" not in desk_mixin, "Desk mixin still owns the radar polling loop")
    require('method = ["tick"]' not in desk_mixin, "Radar refresh still depends on an Aeroworks tick injection")

    module_types = read(MODULE_TYPES)
    for token in (
        "moduleTypeIdentities(moduleType)",
        "declaredFieldIdentities(moduleType)",
        "matchesModuleIdentity",
        '"summary"',
        '"getSummary"',
        "RadarDisplayType.SMALL.modulePath",
        "RadarDisplayType.LARGE.modulePath",
    ):
        require(token in module_types, f"Stable radar module classification is missing: {token}")

    english = load_json(LANG / "en_us.json")
    german = load_json(LANG / "de_de.json")
    require(set(english) == set(german), "German and English language keys differ")
    obsolete_messages = {
        "message.cc_aeroworks.radar_controller_linked",
        "message.cc_aeroworks.radar_controller_invalid",
        "message.cc_aeroworks.radar_display_missing",
        "message.cc_aeroworks.radar_monitor_selected",
        "message.cc_aeroworks.radar_select_monitor_first",
        "message.cc_aeroworks.radar_monitor_invalid",
        "message.cc_aeroworks.radar_link_created",
        "message.cc_aeroworks.radar_link_failed",
        "message.cc_aeroworks.radar_link_cleared",
        "message.cc_aeroworks.radar_route_ambiguous",
    }
    require(not (obsolete_messages & english.keys()), "Obsolete interactive radar messages still exist")

    ponder = read(PONDER)
    plugin = read(PLUGIN)
    require("RadarDisplayScenes::controllerConnection" in plugin, "Controller scene is not registered")
    require("RadarDisplayScenes::directRadarDisplay" in plugin, "Direct radar scene is not registered")
    require('"create_radar", "network_filterer"' in ponder, "Ponder does not show the Network Controller")
    require('"create_radar", "data_link"' in ponder, "Ponder does not show native Controller-to-Radar linking")
    require("monitorStack" not in ponder and '"create_radar", "monitor"' not in ponder, "Ponder still requires a monitor")
    require(ponder.count("showText(") == 10, "Radar Ponder scenes must contain ten explanation steps")

    docs = read(DOCS)
    test_plan = read(TEST_PLAN)
    require(not OLD_TEST_PLAN.exists(), "Legacy monitor/Data Link test plan still exists")
    for token in (
        "direkt angrenzenden Network Controller",
        "alle sechs direkt angrenzenden Positionen",
        "Kein angrenzender Controller",
        "Mehrere angrenzende Controller",
        "weder einen Data-Link-Item-Mixin",
        "20 Ticks",
        "256",
    ):
        require(token in docs, f"Adjacent controller documentation is incomplete: {token}")
    require("5 Ticks" in docs, "Adjacent controller documentation omits the scan interval")
    require("Data-Link-Klick auf das Pult" in docs, "Documentation does not forbid desk linking clicks")
    require("automatische erkennung" in test_plan.lower(), "Regression plan has the wrong scope")
    require("keine Controllerposition gespeichert" in test_plan, "Regression plan does not forbid persisted links")
    require("MULTIPLE_CONTROLLERS" in test_plan, "Regression plan does not cover ambiguous controllers")
    require("API_INCOMPATIBLE" in test_plan, "Regression plan does not cover API incompatibility")

    print(
        "Validated the public Network Controller ticker hook, throttled automatic adjacency discovery, "
        "diagnostic snapshots and complete removal of controller-to-desk Data Link interaction."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError, ValueError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
