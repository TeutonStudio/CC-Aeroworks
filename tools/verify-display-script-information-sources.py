#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        print(f"ERROR: {message}", file=sys.stderr)
        raise SystemExit(1)


state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceSnapshotState.kt")
builder = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceSnapshotBuilder.kt")
widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/InformationSourceManagerWidget.kt")
payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/InformationSourcePayloads.kt")
catalog = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalog.kt")
diagnostics = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DisplayScriptDiagnostics.kt")
access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
handler = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua")
mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")

require('DISPLAY_SCRIPT("DISPLAY SCRIPTS")' in state,
        "information source model must expose a DISPLAY SCRIPTS category")
for model in ("DisplayScriptInformationView", "DisplayScriptInstanceView", "DisplayScriptDependencyView"):
    require(model in state, f"structured display script diagnostics are missing {model}")

require("imports: List<String>" in catalog and "declaredTouchEvents: List<String>" in catalog,
        "script catalog must retain bounded static import and touch metadata")
require("readLongBracket" in catalog and "TOUCH_CALLBACKS" in catalog,
        "Lua scanner must ignore long strings/comments and recognize touch callback declarations")
require('it == "display" || it == "touchdisplay"' in catalog,
        "diagnostics must not expand the selectable script capability beyond the current master runtime")

require("DisplayScriptDiagnosticsRuntime" in diagnostics and "DisplayScriptDiagnosticsRegistry" in diagnostics,
        "runtime diagnostics must be owner-scoped and snapshot-capable")
for hook in ("fun begin(", "fun finish(", "fun read(", "fun setTouchHandlers("):
    require(hook in diagnostics, f"runtime diagnostics are missing hook {hook}")
require('getModuleName(): String = "cc_aeroworks.display_diagnostics"' in diagnostics,
        "runtime diagnostics must be available to CraftOS as a private module")
require("DisplayScriptDiagnosticsLuaApi" in access,
        "embedded computer must register the display diagnostics Lua API")

require("diagnosticBegin" in handler and "diagnosticFinish" in handler,
        "automatic display handlers must bracket executions for runtime observation")
require("wrapTelemetry" in handler and '"telemetry:*"' in handler,
        "display handlers must report actual telemetry reads without changing the telemetry API")
require("setTouchHandlers" in handler,
        "display handlers must report the callbacks actually exported at runtime")

require("buildDisplayScripts" in builder and "DisplayScriptCatalog.scan(owner)" in builder,
        "information source snapshot must project the authoritative display script catalog")
require("DisplayScriptDiagnosticsRegistry.snapshot(owner)" in builder,
        "snapshot must merge runtime observations with static script metadata")
require("dependencyLabel" in builder and 'key == "telemetry:*"' in builder,
        "observed telemetry dependencies must resolve back to readable information source labels")

require("displayScripts" in payload and "MAX_DEPENDENCIES" in payload,
        "network payload must serialize bounded structured display diagnostics")
require("expandedScripts" in widget and "SourceRow.Script" in widget,
        "each display script must be independently expandable")
for section in ("IMPORTS", "REACTIVE", "OBSERVED", "TOUCH"):
    require(f'"    {section}' in widget or f'"      {section}' in widget,
            f"display script tree is missing {section} diagnostics")
require("ccaeroworks_lastSourceSnapshotRequest" in mixin and "ccaeroworks_snapshotIntervalTicks" in mixin,
        "information source page must keep refreshing while visible")

print("Validated display script source category, static Lua metadata, runtime dependency observation, touch diagnostics, bounded payloads, and expandable UI.")
