#!/usr/bin/env python3
"""Validate the Create: Radars Data Link interaction used by radar desk displays."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MIXIN_CONFIG = ROOT / "src/main/resources/cc_aeroworks.mixins.json"
ITEM_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkItemMixin.java"
ENTITY_MIXIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/CreateRadarDataLinkMixin.java"
COMPAT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt"
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
    require(
        "compat.CreateRadarDataLinkItemMixin" in common_mixins,
        "Create: Radars Data Link item compatibility mixin is not registered",
    )
    require(
        "compat.CreateRadarDataLinkMixin" in common_mixins,
        "Create: Radars Data Link block entity capture mixin is not registered",
    )

    item_mixin = read(ITEM_MIXIN)
    require(
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockItem"' in item_mixin,
        "Data Link item mixin targets the wrong optional class",
    )
    require(
        '@Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 0)' in item_mixin,
        "Data Link item mixin must intercept useOn optionally and cancellably",
    )
    require(
        "CreateRadarCompat.handleDataLinkUse(context)" in item_mixin,
        "Data Link item mixin does not delegate to CreateRadarCompat",
    )

    entity_mixin = read(ENTITY_MIXIN)
    require(
        'targets = "com.happysg.radar.block.datalink.DataLinkBlockEntity"' in entity_mixin,
        "Radar snapshot mixin targets the wrong Data Link block entity",
    )
    require(
        "CreateRadarCompat.capture(this)" in entity_mixin,
        "Radar snapshot mixin does not capture configured Data Links",
    )

    compat = read(COMPAT)
    for token in (
        "fun handleDataLinkUse(context: UseOnContext): InteractionResult?",
        "player.isShiftKeyDown && selection.contains(SELECTED_MONITOR_KEY)",
        "clickedEntity != null && isMonitor(clickedEntity)",
        "val desk = clickedEntity as? ConsoleBlockEntity ?: return null",
        "if (!AeroworksDeskAccess.hasRadarDisplay(desk)) return null",
        "if (!selection.contains(SELECTED_MONITOR_KEY))",
        "selectedDimension == currentDimension",
        "val placeContext = BlockPlaceContext(context)",
        "val placement = dataLinkItem.place(placeContext)",
        'invokeBlockPos(dataLink, "target", monitorPos)',
        "fun capture(dataLink: Any)",
        'invoke(dataLink, "getSourcePosition")',
        'invoke(dataLink, "getTargetPosition")',
    ):
        require(token in compat, f"Radar Data Link compatibility is missing contract token: {token}")

    require(
        compat.index("clickedEntity != null && isMonitor(clickedEntity)")
        < compat.index("val desk = clickedEntity as? ConsoleBlockEntity ?: return null"),
        "Monitor selection must be handled before the desk placement path",
    )
    require(
        compat.index("if (!AeroworksDeskAccess.hasRadarDisplay(desk)) return null")
        < compat.index("val dataLinkItem = context.itemInHand.item as? BlockItem ?: return null"),
        "CC-Aeroworks must not intercept Data Link placement on desks without a radar display",
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
    }
    require(required_messages <= english.keys(), "Radar Data Link interaction messages are incomplete")
    require(
        "monitor" in english["ponder.cc_aeroworks.radar_display.text_2"].lower()
        and "desk" in english["ponder.cc_aeroworks.radar_display.text_3"].lower(),
        "English radar Ponder instructions must select the monitor before the desk",
    )
    require(
        "monitor" in german["ponder.cc_aeroworks.radar_display.text_2"].lower()
        and "pult" in german["ponder.cc_aeroworks.radar_display.text_3"].lower(),
        "German radar Ponder instructions must select the monitor before the desk",
    )

    ponder = read(PONDER)
    require(
        ponder.index("First right-click a Create: Radars monitor")
        < ponder.index("Then right-click a free side of the desk"),
        "Radar Ponder scene shows the Data Link clicks in the wrong order",
    )
    require("monitorStack()" in ponder, "Radar Ponder scene does not show the monitor item")

    docs = read(DOCS)
    require(
        "Data Link verbinden" in docs
        and "Radar-Monitor rechtsklicken" in docs
        and "freie Seite des Steuerungspults rechtsklicken" in docs,
        "Radar Data Link documentation does not describe the working two-click sequence",
    )
    require(
        "übrigen Create:-Radars-Verbindungsarten werden nicht verändert" in docs,
        "Documentation does not preserve the original Create: Radars Data Link paths",
    )

    print(
        "Validated optional Data Link item interception, monitor-first desk placement, translations, "
        "Ponder sequence, snapshot capture and documentation."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError, ValueError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
