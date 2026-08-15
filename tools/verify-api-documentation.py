#!/usr/bin/env python3
"""Fail when public CC-Aeroworks APIs drift away from the structured handbook registry."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/ApiDocumentationRegistry.kt"
GUIDE = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/GuideBookContent.kt"
EVENTS = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt"
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
UI_LUA = ROOT / "src/main/resources/data/cc_aeroworks/lua/ui.lua"

IMPLEMENTATIONS = {
    "control_desk": ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt",
    "peripherals": ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt",
    "controls": ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlLuaApi.kt",
    "telemetry": ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt",
}

LUA_FUNCTION = re.compile(
    r"@LuaFunction(?:\([^)]*\))?\s+fun\s+`?([A-Za-z0-9_]+)`?\s*\(",
    re.MULTILINE,
)
DOC_METHOD = re.compile(r'ApiMethodDocumentation\("([^"]+)"')
DOC_EVENT = re.compile(r'ApiEventDocumentation\("(cc_aeroworks_[^"]+)"')
MODULE_START = re.compile(r'id\s*=\s*"([a-z_]+)"')
SUMMARY_KEY = re.compile(r'summaryKey\s*=\s*"([^"]+)"')


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def extract_module_block(source: str, module_id: str) -> str:
    marker = f'id = "{module_id}"'
    start = source.find(marker)
    if start < 0:
        fail(f"ApiDocumentationRegistry is missing module {module_id}")
    next_start = source.find("ApiModuleDocumentation(", start + len(marker))
    return source[start:] if next_start < 0 else source[start:next_start]


def public_methods(path: Path, module_id: str) -> set[str]:
    source = path.read_text(encoding="utf-8")
    if module_id == "telemetry":
        # DockLuaHandle is a nested handle with its own methods, not the global telemetry module.
        source = source.split("object TelemetryComputerRuntimes", 1)[0]
    return set(LUA_FUNCTION.findall(source))


def documented_methods(registry: str, module_id: str) -> set[str]:
    block = extract_module_block(registry, module_id)
    return {name.split(".", 1)[0] if module_id != "ui" else name for name in DOC_METHOD.findall(block)}


def verify_native_modules(registry: str) -> None:
    for module_id, path in IMPLEMENTATIONS.items():
        actual = public_methods(path, module_id)
        documented = documented_methods(registry, module_id)
        missing = sorted(actual - documented)
        stale = sorted(documented - actual)
        if missing:
            fail(f"{module_id} has undocumented @LuaFunction methods: {', '.join(missing)}")
        if stale:
            fail(f"{module_id} documents missing methods: {', '.join(stale)}")


def verify_ui_module(registry: str) -> None:
    block = extract_module_block(registry, "ui")
    documented = set(DOC_METHOD.findall(block))
    source = UI_LUA.read_text(encoding="utf-8")
    actual = set(re.findall(r"function\s+ui\.([A-Za-z0-9_.]+)\s*\(", source))
    # Methods on state/navigator/modifier/frame handles are deliberately documented by their
    # owning concept, not as top-level module functions.
    missing = sorted(actual - documented)
    stale = sorted(documented - actual)
    if missing:
        fail("ui has undocumented top-level functions: " + ", ".join(missing))
    if stale:
        fail("ui documents missing top-level functions: " + ", ".join(stale))


def verify_events(registry: str) -> None:
    documented = set(DOC_EVENT.findall(registry))
    constants = EVENTS.read_text(encoding="utf-8")
    for event in sorted(documented):
        if f'"{event}"' not in constants:
            fail(f"documented event has no CCAeroworks constant: {event}")


def verify_translations(registry: str) -> None:
    german = json.loads((LANG / "de_de.json").read_text(encoding="utf-8"))
    english = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
    keys = set(SUMMARY_KEY.findall(registry))
    module_ids = set(MODULE_START.findall(registry))
    for module_id in module_ids:
        keys.add(f"guide.cc_aeroworks.api.{module_id}.label")
        keys.add(f"guide.cc_aeroworks.api.{module_id}.title")
    for key in sorted(keys):
        if key not in german or key not in english:
            fail(f"API handbook translation is missing: {key}")


def verify_no_retired_examples() -> None:
    guide = GUIDE.read_text(encoding="utf-8")
    for retired in ("aeroworks.getDesks", "aeroworks.getModules", "aeroworks.getNetwork"):
        if retired in guide:
            fail(f"handbook still teaches retired API call: {retired}")


def main() -> int:
    registry = REGISTRY.read_text(encoding="utf-8")
    verify_native_modules(registry)
    verify_ui_module(registry)
    verify_events(registry)
    verify_translations(registry)
    verify_no_retired_examples()
    print("Validated ControlDesk, peripherals, controls, telemetry and Reactive UI against the structured handbook API registry.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
