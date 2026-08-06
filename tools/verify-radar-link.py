#!/usr/bin/env python3
"""Validate Create: Radars native links and CC-Aeroworks desk-network routing."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MIXIN_CONFIG = ROOT / "src/main/resources/cc_aeroworks.mixins.json"
ITEM_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkItemMixin.java"
ENTITY_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkMixin.java"
COMPAT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt"
MODULE_TYPES = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt"
PONDER = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java"
DOCS = ROOT / "docs/create-radars-integration.md"


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
    require("compat.CreateRadarDataLinkItemMixin" in common_mixins, "Data Link item mixin is missing")
    require("compat.CreateRadarDataLinkMixin" in common_mixins, "Data Link entity mixin is missing")

    item_mixin = read(ITEM_MIXIN)
    require(
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockItem"' in item_mixin,
        "Data Link item mixin targets the wrong optional class",
    )
    require("CreateRadarCompat.handleDataLinkUse(context)" in item_mixin, "Data Link item is not delegated")

    entity_mixin = read(ENTITY_MIXIN)
    require(
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockEntity"' in entity_mixin,
        "Radar snapshot mixin targets the wrong optional class",
    )
    require("CreateRadarCompat.capture(this)" in entity_mixin, "Data Link snapshots are not captured")

    compat = read(COMPAT)
    required_tokens = (
        "fun handleDataLinkUse(context: UseOnContext): InteractionResult?",
        "private val NATIVE_SELECTION_KEYS",
        '"SelectedFiltererPos"',
        '"SelectedMountPos"',
        "val stack = context.itemInHand",
        "val existingSelection = stack.tag",
        "if (hasNativeSelection(existingSelection))",
        "clearMonitorSelection(stack)",
        "clickedEntity != null && isMonitor(clickedEntity)",
        "val selection = stack.getOrCreateTag()",
        "val sourceDesk = clickedEntity as? ConsoleBlockEntity ?: return null",
        "val route = resolveRadarRoute(sourceDesk)",
        "if (!isRoutable(route.state))",
        "if (route.destinations.isEmpty())",
        "if (route.destinations.size > 1)",
        "val placeContext = BlockPlaceContext(context)",
        'invokeBlockPos(dataLink, "target", monitorPos)',
        'invoke(dataLink, "getSourcePosition") == sourceDesk.blockPos',
        'invoke(dataLink, "getTargetPosition") == monitorPos',
        "level.removeBlock(placedPos, false)",
        "stack.grow(1)",
        "fun capture(dataLink: Any)",
        "val destination = route.destinations.singleOrNull() ?: return",
        "ConsoleMultiblockManager.resolve(level, sourceDesk.blockPos)",
        "network.members.map { it.desk }",
        "filter(AeroworksDeskAccess::hasRadarDisplay)",
        "state == ConsoleNetworkState.ACTIVE || state == ConsoleNetworkState.NONE",
        "routeFailureKey(route.state)",
    )
    for token in required_tokens:
        require(token in compat, f"Radar routing is missing contract token: {token}")

    require(
        compat.index("if (hasNativeSelection(existingSelection))")
        < compat.index("clickedEntity != null && isMonitor(clickedEntity)"),
        "Native Create: Radars selections must bypass monitor interception",
    )
    require(
        "player.persistentData" not in compat,
        "CC-Aeroworks monitor selection must be stored on the Data Link item, not the player",
    )
    require(
        'if (!AeroworksDeskAccess.hasRadarDisplay(desk)) return null' not in compat,
        "Data Link placement is still restricted to the display's own desk",
    )
    require(
        'message.cc_aeroworks.console_conflict' in compat
        and 'message.cc_aeroworks.console_too_large' in compat
        and 'message.cc_aeroworks.console_partially_loaded' in compat,
        "Invalid radar topology does not reuse localized network diagnostics",
    )

    module_types = read(MODULE_TYPES)
    for token in (
        "moduleTypeIdentities(moduleType)",
        "matchesModuleIdentity",
        '"summary"',
        '"getSummary"',
        "CCAeroworks.MOD_ID",
        "RadarDisplayType.SMALL.modulePath",
        "RadarDisplayType.LARGE.modulePath",
    ):
        require(token in module_types, f"Stable radar module classification is missing: {token}")
    require(
        "moduleType === SMALL_RADAR" in module_types and "moduleType === LARGE_RADAR" in module_types,
        "Fast canonical radar module classification is missing",
    )

    english = load_json(LANG / "en_us.json")
    german = load_json(LANG / "de_de.json")
    require(set(english) == set(german), "German and English language keys differ")
    required_messages = {
        "message.cc_aeroworks.radar_monitor_selected",
        "message.cc_aeroworks.radar_select_monitor_first",
        "message.cc_aeroworks.radar_monitor_invalid",
        "message.cc_aeroworks.radar_link_created",
        "message.cc_aeroworks.radar_link_failed",
        "message.cc_aeroworks.radar_link_cleared",
        "message.cc_aeroworks.radar_route_missing",
        "message.cc_aeroworks.radar_route_ambiguous",
    }
    require(required_messages <= english.keys(), "Radar routing messages are incomplete")

    ponder = read(PONDER)
    require("automaticRouting" in ponder, "Automatic radar routing scene is missing")
    require("dataLinkCompatibility" in ponder, "Data Link compatibility scene is missing")
    require("PonderText.get" in ponder, "Radar Ponder text is not localized")
    require("monitorStack()" in ponder and "dataLinkStack()" in ponder, "Radar source items are not shown")

    docs = read(DOCS)
    lower_docs = docs.lower()
    require("automatisch" in lower_docs, "Radar docs do not explain automatic routing")
    require("genau eine" in lower_docs, "Radar docs do not explain the unique-target rule")
    require("verschiedenen pulten" in lower_docs, "Radar docs do not explain cross-desk routing")
    require("kein eingebetteter computer" in lower_docs, "Radar docs still require an embedded computer")
    require("network controller" in lower_docs, "Radar docs do not preserve native Network Controller links")
    require("itemstack" in lower_docs, "Radar docs do not explain item-local monitor selection")
    require(
        "mehrere computer" in lower_docs and "abgelehnt" in lower_docs,
        "Radar docs do not explain that conflicting computer ownership disables routing",
    )

    print(
        "Validated native Create: Radars selection passthrough, item-local monitor selection, rollback-safe placement, "
        "computer-optional desk routing, stable radar module classification and localized documentation."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError, ValueError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
