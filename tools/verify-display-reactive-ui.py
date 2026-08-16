#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


binding_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayBinding.kt"
native_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DisplayUiLuaApi.kt"
service_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskService.kt"
catalog_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalog.kt"
application_payload_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayApplicationPayload.kt"
touch_payload_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayTouchScriptPayload.kt"
widgets_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/client/DisplayBindingRowWidgets.kt"
module_screen_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt"
ui_path = "src/main/resources/data/cc_aeroworks/lua/ui.lua"
autorun_path = "src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua"
workflow_path = ".github/workflows/verify.yml"

binding = read(binding_path)
native = read(native_path)
service = read(service_path)
catalog = read(catalog_path)
application_payload = read(application_payload_path)
touch_payload = read(touch_payload_path)
widgets = read(widgets_path)
module_screen = read(module_screen_path)
ui = read(ui_path)
autorun = read(autorun_path)
workflow = read(workflow_path)

# Controller-only bindings are real legacy bindings. They must not acquire an empty reactive frame.
require("EMPTY_REACTIVE_APP_PATH" not in binding, "controller-only bindings must not boot a synthetic reactive app")
require("runtimeBootProgramPath" not in binding, "runtime boot path must be the configured application path")
require("fun hasReactiveApplication" in binding and "bootProgramPath(binding).isNotEmpty()" in binding,
        "reactive ownership must depend on an actual boot application")
require("if (!hasReactiveApplication(normalized))" in binding and "ReactiveDisplayFrames.clear" in binding,
        "leaving reactive mode must clear the reactive frame")
require(not (ROOT / "src/main/resources/data/cc_aeroworks/lua/empty_display_app.lua").exists(),
        "synthetic empty reactive app must be removed")
require('"bootProgram" to DisplayBindings.bootProgramPath(binding)' in native,
        "native display descriptors must expose only the configured reactive application")

# mainThread APIs stay mainThread-safe; the scheduler fix belongs in the Lua lifecycle, not annotations.
for signature in ("fun listDisplays()", "fun beginFrame(", "fun clearFrame(", "fun commit()"):
    position = native.find(signature)
    require(position >= 0 and "@LuaFunction(mainThread = true)" in native[max(0, position - 80):position],
            f"{signature} must remain a main-thread Lua API")

# Imperative pixels/text and reactive frames are mutually exclusive display owners.
require("fun requireImperativeWritable" in service and "ReactiveDisplayFrames.snapshot(desk, socket)" in service,
        "imperative display writes must reject active reactive ownership")
for method in ("setDisplayText", "setDisplayNumber", "clearDisplay", "setDisplayPixel", "setDisplayPixels", "clearDisplayPixels"):
    start = service.find(f"fun {method}")
    require(start >= 0 and "requireImperativeWritable" in service[start:start + 900],
            f"{method} must enforce display ownership")

# Pointer input is stateful for dependencies, while gesture callbacks remain one-shot events.
require("function ui.input.pointer()" in ui, "reactive UI must expose ui.input.pointer()")
require('local dependency = runtime.id .. ":input:pointer"' in ui and "native.read(dependency)" in ui,
        "pointer reads must register a reactive dependency")
require("function Runtime:updatePointer(event)" in ui and "self.pointerRevision = (self.pointerRevision or 0) + 1" in ui,
        "every pointer event must advance its revision, including repeated coordinates")
require('native.changed(self.id .. ":input:pointer")' in ui,
        "pointer updates must invalidate dependent scopes")
require("self:updatePointer(event)" in ui and "hitNode(self.root,event.x,event.y)" in ui,
        "pointer state must update before normal component hit testing")
require(
    "self.controller.onTap" in ui and
    "self.controller.onDoubleTap" in ui and
    "self.controller.onPointer" in ui and
    "p.onTap" in ui and
    "p.onDoubleTap" in ui and
    "p.onPointer" in ui,
    "one-shot controller and component gestures must remain available",
)

# The raw event is normalized once and retains resolution-independent/source coordinates.
require("local function pointerEventFromRaw(event)" in ui, "raw pointer conversion must be centralized")
for field, index in (("u", 13), ("v", 14), ("deskX", 15), ("deskY", 16), ("deskZ", 17)):
    require(f"{field}=event[{index}]" in ui, f"reactive pointer event must preserve {field}")
require(ui.count("pointerEventFromRaw(event)") >= 2,
        "ui.run and automatic supervision must share the same pointer conversion")

# Automatic supervision uses the current CraftOS coroutine, never a manually resumed child coroutine.
require("function ui.createSupervisor()" in ui and "function supervisor:handle(...)" in ui,
        "reactive apps need a reusable non-blocking supervisor")
require('if not display or not display.bootProgram or display.bootProgram == "" then return nil end' in ui,
        "automatic runtime must start only for configured reactive applications")
require("function ui.supervise()" in ui and "ui.createSupervisor()" in ui,
        "blocking ui.supervise must be a wrapper over the shared supervisor")
for forbidden in ("coroutine.create", "supervisorPullEventRaw", "resumeSupervisor"):
    require(forbidden not in autorun, f"automatic display runtime must not own a private scheduler: {forbidden}")
require("ui.createSupervisor" in autorun and "supervisor:handle" in autorun,
        "CraftOS autorun must drive the shared supervisor synchronously")
require("nativePullEventRaw()" in autorun,
        "automatic supervision must preserve the foreground raw event stream")

# Script discovery and selection distinguish modern apps from compatibility handlers.
require("val reactiveUi: Boolean" in catalog and '"cc_aeroworks.ui" -> reactiveUi = true' in catalog,
        "script catalog must discover reactive UI applications")
require("fun findReactiveUi" in catalog and "fun findLegacyTouch" in catalog,
        "server catalog must expose role-specific validation")
require("DisplayScriptCatalog.findReactiveUi" in application_payload and
        "DisplayScriptCatalog.findLegacyTouch" in application_payload,
        "application payload must validate app and controller roles separately")
require("DisplayScriptCatalog.findLegacyTouch" in touch_payload,
        "legacy touch payload must reject non-touch scripts")
require("REACTIVE_APP" in widgets and "LEGACY_TOUCH" in widgets and "entry.reactiveUi" in widgets,
        "ModuleScreen dropdowns must distinguish reactive apps from legacy handlers")
require("SetDisplayApplicationPayload" in module_screen and
        "ccaeroworks_selectedApplicationPath" in module_screen and
        "ccaeroworks_selectedControllerPath" in module_screen,
        "ModuleScreen must submit application and compatibility controller together")

require("python3 tools/verify-display-reactive-ui.py" in workflow,
        "workflow must enforce the reactive display input architecture")

print("Validated reactive display ownership, pointer dependencies, CraftOS scheduling, role-aware script selection, and legacy compatibility.")
