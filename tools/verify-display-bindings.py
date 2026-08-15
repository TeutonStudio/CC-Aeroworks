#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


binding = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayBinding.kt")
binding_service = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayBindingService.kt")
registry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarSourceRegistry.kt")
access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
state_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityDisplayBindingMixin.kt")
ui_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
radar_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetRadarDisplaySourcePayload.kt")
script_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayTouchScriptPayload.kt")
app_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayApplicationPayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
reactive_frame = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/reactive/ReactiveDisplayFrame.kt")
reactive_frames = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/reactive/ReactiveDisplayFrames.kt")
ui_native = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DisplayUiLuaApi.kt")
ui_lua = read("src/main/resources/data/cc_aeroworks/lua/ui.lua")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Binding configuration is intentionally separate from Aeroworks input channels.
require("sealed interface DisplayBinding" in binding, "display binding model is missing")
require("data class RadarSource" in binding, "radar source binding is missing")
require("data class LuaHandler" in binding, "legacy Lua handler binding is missing")
require("data class LuaApplication" in binding, "controller + boot application binding is missing")
require("controllerPath" in binding and "bootProgramPath" in binding,
        "display application must persist controller and boot program independently")
require("ControlChannel" not in binding, "display bindings must not masquerade as Aeroworks ControlChannels")
require("MAX_HANDLER_PATH_LENGTH" in binding, "Lua application paths must be bounded")

# Radar sources are desk ingress references and reuse each ingress' already synchronized snapshot.
require("ConsoleMultiblockManager.resolve" in registry, "radar sources must be scoped to the desk multiblock")
require("network.memberAt(source.ingressPos)" in registry, "render-time source lookup must use cached memberAt lookup")
require("ccaeroworks_getRadarSnapshot" in registry, "radar routing must reuse the ingress desk snapshot")
require("MAX_SYNCED_TRACKS" not in registry, "routing must not create a second track serialization path")
require("RadarSourceRegistry.resolveSnapshot" in access, "radar surfaces must honor per-socket source bindings")

# Bindings persist through normal ConsoleBlockEntity NBT/client sync and clear on dismount.
require("CCAeroworksDisplayBindings" in state_mixin, "display bindings must have a persistent NBT key")
require('method = ["write"]' in state_mixin and 'method = ["read"]' in state_mixin,
        "display bindings must participate in ConsoleBlockEntity write/read")
require("dismount(I)Lnet/minecraft/world/item/ItemStack;" in state_mixin,
        "dismount must clear stale display bindings")
require('"ConsoleBlockEntityDisplayBindingMixin"' in mixins,
        "display binding state mixin must be registered")

# UI selection is server-authoritative.
require("Radar source: local" in ui_mixin, "Radar Display module UI must expose the local/default source")
require("SetRadarDisplaySourcePayload" in ui_mixin, "Radar Display module UI must use the binding payload")
require("RadarSourceRegistry.sources(desk)" in radar_payload,
        "server must validate requested radar sources against the current multiblock")
require("CCModuleTypes.radarDisplayType(module.type())" in radar_payload,
        "server must reject source bindings on non-radar modules")
require('"client.ModuleScreenDisplayBindingMixin"' in mixins,
        "Radar/display binding UI mixin must be registered")

# Large normal displays expose controller and boot program independently.
require("ccaeroworks_controllerScriptField" in ui_mixin and "ccaeroworks_bootProgramField" in ui_mixin,
        "large display module UI must expose controller and boot program fields")
require("SetDisplayApplicationPayload" in ui_mixin,
        "display application fields must persist through one server-authoritative payload")
require("DeskDisplayType.THREE_DIGIT" in app_payload,
        "server must reject application bindings on unsupported display modules")
require("MAX_HANDLER_PATH_LENGTH" in app_payload,
        "display application payload must enforce path bounds")
require("SetDisplayTouchScriptPayload" in payloads and "SetDisplayApplicationPayload" in payloads,
        "legacy and two-level display configuration payloads must be registered")
require("bootProgramPath" in script_payload,
        "legacy touch-script updates must preserve the configured boot application")

# Public ControlDesk configuration supports both new names and the old compatibility name.
for method in (
    "getRadarSources", "getDisplayBinding", "setRadarSource", "setDisplayTouchScript",
    "setDisplayController", "setDisplayBootProgram", "setDisplayApplication", "clearDisplayBinding"
):
    require(f"fun {method}" in peripheral, f"ControlDesk API is missing {method}")
require("setTouchScript" in binding_service and "setController" in binding_service and "setBootProgram" in binding_service,
        "display binding service must preserve the legacy method while exposing two-level application configuration")

# Input routing keeps legacy events while resolving the controller path from either binding format.
require("DisplayBindings.controllerPath" in dispatcher and "DisplayBindings.controllerPath" in peripheral_state,
        "display input events must expose the active controller path")
require("CONSOLE_TOUCH_EVENT" in dispatcher, "legacy embedded touch event must remain available")
require('"monitor_touch"' in peripheral_state, "CC:Tweaked monitor_touch compatibility must remain available")

# Reactive runtime is sparse, transactional and mounted as a bundled Lua library.
require("REACTIVE_DISPLAY_TILE_SIZE" in reactive_frame and "LongArray" in reactive_frame,
        "reactive display framebuffer must use packed tiles instead of one-character-per-pixel strings")
require("patchTiles" in reactive_frame and "patchTiles.isEmpty()" in reactive_frame,
        "frame commits must suppress unchanged tile updates")
require("sendToPlayersTrackingChunk" in reactive_frames,
        "reactive frame changes must use compact tracking-chunk patches")
require('getModuleName(): String = "cc_aeroworks.ui_native"' in ui_native,
        "native reactive UI API is not registered as a module")
for feature in ("ui.state", "ui.derived", "ui.component", "ui.LazyColumn", "ui.supervise"):
    require(feature in ui_lua, f"bundled reactive UI library is missing {feature}")
require('native.beginScope(id, phase)' in ui_lua and '"composition"' in ui_lua and '"layout"' in ui_lua and '"draw"' in ui_lua,
        "reactive UI must track composition, layout and draw scopes separately")
require((ROOT / "examples/cc/display-binding-router.lua").is_file(),
        "legacy display binding Lua router example is missing")

require("python3 tools/verify-display-bindings.py" in workflow,
        "workflow must enforce the display binding architecture")

print("Validated two-level display applications, legacy compatibility, sparse tiled runtime frames, phase-scoped reactive UI and server-authoritative configuration.")
