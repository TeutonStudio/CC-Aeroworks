#!/usr/bin/env python3
"""Validate direct Create: Radars Network Controller to Aeroworks desk integration."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MIXIN_CONFIG = ROOT / "src/main/resources/cc_aeroworks.mixins.json"
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
    require(
        "compat.CreateRadarNetworkControllerLinkMixin" in common_mixins,
        "Network Controller item mixin is missing",
    )
    require(
        "compat.CreateRadarDataLinkItemMixin" not in common_mixins
        and "compat.CreateRadarDataLinkMixin" not in common_mixins,
        "Legacy monitor/Data Link mixins are still registered",
    )
    require(ITEM_MIXIN.is_file(), "Network Controller item mixin file is missing")
    require(not OLD_ITEM_MIXIN.exists(), "Legacy Data Link item mixin still exists")
    require(not OLD_ENTITY_MIXIN.exists(), "Legacy Data Link block entity mixin still exists")

    item_mixin = read(ITEM_MIXIN)
    require(
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockItem"' in item_mixin,
        "Controller link mixin targets the wrong optional class",
    )
    require(
        "CreateRadarCompat.handleControllerLink(context)" in item_mixin,
        "Data Link item does not delegate controller-to-desk linking",
    )

    compat = read(COMPAT)
    required_tokens = (
        "fun handleControllerLink(context: UseOnContext): InteractionResult?",
        'private const val SELECTED_FILTERER_KEY: String = "SelectedFiltererPos"',
        "val sourceDesk = context.level.getBlockEntity(context.clickedPos) as? ConsoleBlockEntity ?: return null",
        "readSelectedController(itemData(stack))",
        "RadarControllerLink(",
        "route.desks.forEach",
        "ccaeroworks_setRadarControllerLink(null)",
        "ccaeroworks_setRadarControllerLink(link)",
        "CustomData.update(DataComponents.CUSTOM_DATA, stack)",
        "fun refreshDesk(desk: ConsoleBlockEntity)",
        'invokeDeclared(controller, "getRadar", level)',
        'readField(controller, "radarCache")',
        'invoke(radar, "getTracks")',
        'invoke(radar, "getRange")',
        'invoke(radar, "isRunning")',
        'invoke(radar, "getWorldPos")',
        "filter(AeroworksDeskAccess::hasRadarDisplay)",
        "state == ConsoleNetworkState.ACTIVE || state == ConsoleNetworkState.NONE",
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
    )
    for token in required_tokens:
        require(token in compat, f"Direct controller integration is missing contract token: {token}")

    for forbidden in (
        "MonitorBlockEntity",
        "DataLinkBlockEntity",
        "BlockPlaceContext",
        "BlockItem",
        "getTargetPosition",
        "getSourcePosition",
        "handleDataLinkUse",
        "fun capture(",
        "SELECTED_MONITOR",
        "radar_route_ambiguous",
    ):
        require(forbidden not in compat, f"Legacy monitor/Data Link behavior remains: {forbidden}")

    state_access = read(STATE_ACCESS)
    desk_mixin = read(DESK_MIXIN)
    require("data class RadarControllerLink" in state_access, "Persistent controller link model is missing")
    require("fun ccaeroworks_getRadarControllerLink" in state_access, "Controller link getter is missing")
    require("fun ccaeroworks_setRadarControllerLink" in state_access, "Controller link setter is missing")
    require("RADAR_CONTROLLER_NBT_KEY" in desk_mixin, "Controller link is not persisted on the desk")
    require("RadarControllerLink.fromTag" in desk_mixin, "Controller link is not restored from NBT")
    require("CreateRadarCompat.refreshDesk" in desk_mixin, "Linked desk does not refresh its controller")
    require('method = ["tick"]' in desk_mixin, "Radar controller refresh is not attached to desk ticking")

    module_types = read(MODULE_TYPES)
    for token in (
        "moduleTypeIdentities(moduleType)",
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
    required_messages = {
        "message.cc_aeroworks.radar_controller_linked",
        "message.cc_aeroworks.radar_controller_invalid",
        "message.cc_aeroworks.radar_display_missing",
    }
    require(required_messages <= english.keys(), "Direct controller messages are incomplete")
    obsolete_messages = {
        "message.cc_aeroworks.radar_monitor_selected",
        "message.cc_aeroworks.radar_select_monitor_first",
        "message.cc_aeroworks.radar_monitor_invalid",
        "message.cc_aeroworks.radar_link_created",
        "message.cc_aeroworks.radar_link_failed",
        "message.cc_aeroworks.radar_link_cleared",
        "message.cc_aeroworks.radar_route_ambiguous",
    }
    require(not (obsolete_messages & english.keys()), "Legacy monitor/Data Link messages still exist")

    ponder = read(PONDER)
    plugin = read(PLUGIN)
    require("RadarDisplayScenes::controllerConnection" in plugin, "Controller connection scene is not registered")
    require("RadarDisplayScenes::directRadarDisplay" in plugin, "Direct radar scene is not registered")
    require('"create_radar", "network_filterer"' in ponder, "Ponder does not show the Network Controller")
    require("monitorStack" not in ponder and '"create_radar", "monitor"' not in ponder, "Ponder still requires a monitor")
    require(ponder.count("showText(") == 10, "Radar Ponder scenes must contain ten explanation steps")

    docs = read(DOCS)
    test_plan = read(TEST_PLAN)
    require(not OLD_TEST_PLAN.exists(), "Legacy monitor/Data Link test plan still exists")
    for token in (
        "Network Controller",
        "kein Data-Link-Block",
        "kein Monitorblock",
        "alle RadarDisplays",
        "20 Ticks",
        "256",
    ):
        require(token in docs, f"Direct controller documentation is incomplete: {token}")
    require("Monitor-zuerst-Ablauf" in docs, "Documentation does not explicitly retire the old monitor-first flow")
    require("Network Controller → Aeroworks-Steuerungspult" in test_plan, "New regression plan has the wrong scope")
    require("kein Data-Link-Block" in test_plan, "Regression plan does not forbid Data Link block placement")

    print(
        "Validated direct Network Controller to desk linking, persistent source ownership, direct radar snapshots, "
        "multi-display routing, localized Ponder scenes and complete removal of the monitor/Data Link block pipeline."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError, ValueError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
