#!/usr/bin/env python3
"""Verify that the in-game API catalog follows the actual public Lua surfaces."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/ApiReferenceCatalog.kt"
GUIDE = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/GuideBookContent.kt"
FALLBACK_BOOK = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/item/GuideBookContent.kt"
DIAGNOSTICS = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DisplayScriptDiagnostics.kt"
HANDLERS = ROOT / "src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua"
DISPLAY_MODULE = ROOT / "src/main/resources/data/computercraft/lua/rom/modules/main/display.lua"
TOUCH_MODULE = ROOT / "src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua"

PUBLIC_CLASSES = {
    "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt": (
        "ControlDeskPeripheral", {"debugDisplayTouchLog"}
    ),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetwork.kt": ("DeskLuaHandle", set()),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt": ("ComputerConsoleLuaApi", set()),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerChannelLuaApi.kt": ("ComputerChannelLuaApi", set()),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlLuaApi.kt": ("ComputerControlLuaApi", set()),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt": ("ComputerWireLuaApi", set()),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt": ("TelemetryLuaApi", set()),
    "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/compat/computercraft/RadarControlDeskPeripheral.kt": (
        "RadarControlDeskPeripheral", set()
    ),
}

REQUIRED_REFERENCES = {
    "control_desk", "desk_handle", "peripherals", "channels", "controls", "wires", "telemetry",
    "dock_handle", "display", "touchdisplay", "radar_control_desk",
}
REQUIRED_MODULES = {
    "cc_aeroworks.peripherals", "cc_aeroworks.channels", "cc_aeroworks.controls",
    "cc_aeroworks.wires", "cc_aeroworks.telemetry", "display", "touchdisplay",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def class_body(source: str, class_name: str) -> str:
    match = re.search(rf"\bclass\s+{re.escape(class_name)}\b[^{{]*{{", source)
    require(match is not None, f"Could not find class {class_name}")
    start = match.end() - 1
    depth = 0
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start + 1:index]
    raise AssertionError(f"Unbalanced class body for {class_name}")


def kotlin_lua_methods(source: str, class_name: str) -> set[str]:
    body = class_body(source, class_name)
    matches = re.findall(
        r"@LuaFunction(?:\([^)]*\))?\s*fun\s+(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_]*))\s*\(",
        body,
    )
    return {backticked or ordinary for backticked, ordinary in matches}


def verify_public_surfaces(catalog: str) -> None:
    for relative_path, (class_name, internal) in PUBLIC_CLASSES.items():
        source = (ROOT / relative_path).read_text(encoding="utf-8")
        methods = kotlin_lua_methods(source, class_name) - internal
        require(methods, f"No public Lua methods discovered for {class_name}")
        missing = sorted(method for method in methods if f'"{method}(' not in catalog)
        require(not missing, f"API catalog is missing {class_name} methods: {', '.join(missing)}")
        for method in internal:
            require(
                f'"{method}(' not in catalog,
                f"Internal method {class_name}.{method} leaked into the public API catalog",
            )


def verify_rom_modules(catalog: str) -> None:
    display = DISPLAY_MODULE.read_text(encoding="utf-8")
    touch = TOUCH_MODULE.read_text(encoding="utf-8")
    display_methods = set(re.findall(r"function\s+display\.([A-Za-z_][A-Za-z0-9_]*)\s*\(", display))
    touch_methods = set(re.findall(r"function\s+touchdisplay\.([A-Za-z_][A-Za-z0-9_]*)\s*\(", touch))
    require(display_methods, "No methods discovered in display.lua")
    require(touch_methods, "No methods discovered in touchdisplay.lua")
    missing_display = sorted(method for method in display_methods if f'"{method}(' not in catalog)
    missing_touch = sorted(method for method in touch_methods if f'"{method}(' not in catalog)
    require(not missing_display, "API catalog is missing display.lua methods: " + ", ".join(missing_display))
    require(not missing_touch, "API catalog is missing touchdisplay.lua methods: " + ", ".join(missing_touch))


def verify_typed_manual(catalog: str, guide: str) -> None:
    signatures = re.findall(r'"([A-Za-z_][A-Za-z0-9_]*\([^"\n]*\)\s*->\s*[^"\n]+)"', catalog)
    require(signatures, "API catalog contains no typed method signatures")
    require(all("->" in signature for signature in signatures), "Typed API signatures must declare their return type")
    require(": string" in catalog and ": integer" in catalog and ": boolean" in catalog,
            "API catalog must document common argument types")
    require("ApiAccent" in catalog, "API references must retain per-module color accents")
    require("GuideEntry.ApiScopes" in guide, "Guide start page must render the API scope table")
    require("GuideEntry.ApiTypeLegend" in guide, "Guide start page must render the Lua type legend")
    require("GuideEntry.ApiSubset(\"desk_handle\"" in guide,
            "Module guide must retain its typed Desk handle subset")
    require("DRIVE BY WIRE CABLE" in catalog and "MOD REQUIRED FOR OUTPUT" in catalog,
            "Wire API must explain its Drive By Wire dependency")


def main() -> int:
    catalog = CATALOG.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    fallback = FALLBACK_BOOK.read_text(encoding="utf-8")
    diagnostics = DIAGNOSTICS.read_text(encoding="utf-8")
    handlers = HANDLERS.read_text(encoding="utf-8")

    ids = set(re.findall(r"ApiReference\(\s*\"([a-z0-9_]+)\"", catalog))
    missing_ids = sorted(REQUIRED_REFERENCES - ids)
    require(not missing_ids, "Missing API references: " + ", ".join(missing_ids))

    guide_refs = set(re.findall(r'GuideEntry\.(?:Api|ApiSubset)\("([a-z0-9_]+)"', guide))
    unknown_refs = sorted(guide_refs - ids)
    require(not unknown_refs, "Guide references unknown API entries: " + ", ".join(unknown_refs))

    for module in REQUIRED_MODULES:
        require(f'"{module}"' in catalog, f"API catalog is missing module {module}")

    verify_public_surfaces(catalog)
    verify_rom_modules(catalog)
    verify_typed_manual(catalog, guide)

    require("aeroworks.get" not in guide, "Guide still contains the removed global aeroworks.* API")
    require('GuideEntry.Api("channels")' in guide, "Guide does not expose the preferred channels API")
    channels = catalog[catalog.index('"channels", "channels"'):]
    require("preferred = true" in channels, "channels must remain marked preferred")
    require('requiredMod = "create_radar"' in guide, "Create: Radars guide page is not feature-gated")
    require('requiredMod = "simulated"' in guide, "Create: Simulated guide page is not feature-gated")
    require('isLoaded("create_radar")' in fallback, "Fallback radar page is not feature-gated")

    require('if (draw) observation.touchEvents += "draw"' in diagnostics, "Display diagnostics do not record draw handlers")
    require('type(handler.onDraw) == "function"' in handlers, "Display autorun does not report onDraw to diagnostics")
    require('cc_aeroworks.ui' not in catalog, "Unpublished cc_aeroworks.ui must not appear in the public catalog")

    print(
        "Validated Kotlin and ROM Lua method coverage, typed argument/return documentation, API scopes/modules, "
        "legacy API removal, optional integration gating and tap/draw display diagnostics."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError, ValueError) as error:
        print(f"ERROR: {error}")
        raise SystemExit(1)
