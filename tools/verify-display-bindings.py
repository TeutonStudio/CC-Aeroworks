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
pointer_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayPointerActionPayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
computer_desk = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
reactive_frame = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/reactive/ReactiveDisplayFrame.kt")
reactive_frames = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/reactive/ReactiveDisplayFrames.kt")
ui_native = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DisplayUiLuaApi.kt")
ui_lua = read("src/main/resources/data/cc_aeroworks/lua/ui.lua")
autorun = read("src/main/resources/data/computercraft/lua/rom/autorun/00_cc_aeroworks_reactive_display.lua")
controller_host = read("src/main/resources/data/cc_aeroworks/lua/controller_host.lua")
sable_geometry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/sable/SableInteractionGeometry.kt")
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

# Player-facing display packets must validate rendered Sable geometry rather than plot coordinates.
require("Sable.HELPER.projectOutOfSubLevel" in sable_geometry and "distanceSquaredWithSubLevels" in sable_geometry,
        "shared Sable interaction geometry must project display interactions into rendered space")
for name, payload in (("pointer", pointer_payload), ("touch-script", script_payload), ("application", app_payload)):
    require("SableInteractionGeometry.mayInteract" in payload and "SableInteractionGeometry.withinReach" in payload,
            f"{name} payload must use Sable-aware interaction validation")
    require("player.distanceToSqr(payload.pos.center)" not in payload,
            f"{name} payload must not compare the player with Sable plot coordinates")

# Public ControlDesk configuration supports both new names and the old compatibility name.
for method in (
    "getRadarSources", "getDisplayBinding", "setRadarSource", "setDisplayTouchScript",
    "setDisplayController", "setDisplayBootProgram", "setDisplayApplication", "clearDisplayBinding"
):
    require(f"fun {method}" in peripheral, f"ControlDesk API is missing {method}")
require("setTouchScript" in binding_service and "setController" in binding_service and "setBootProgram" in binding_service,
        "display binding service must preserve the legacy method while exposing two-level application configuration")

# Input routing keeps legacy events, resolves either controller format and never drops startup touches.
require("DisplayBindings.controllerPath" in dispatcher and "DisplayBindings.controllerPath" in peripheral_state,
        "display input events must expose the active controller path")
require("CONSOLE_TOUCH_EVENT" in dispatcher, "legacy embedded touch event must remain available")
require('"monitor_touch"' in peripheral_state, "CC:Tweaked monitor_touch compatibility must remain available")
require(dispatcher.count("owner.queueComputerEventWhenReady(") >= 2,
        "embedded display events must use start-safe ComputerControlDesk delivery")
require("computer.queueEvent(" not in dispatcher,
        "display dispatcher must not queue directly into a computer which may still be starting")
require("MAX_PENDING_COMPUTER_EVENTS" in computer_desk and "pendingComputerEvents" in computer_desk,
        "ComputerControlDesk must keep a bounded queue for startup display events")
require("fun queueComputerEventWhenReady" in computer_desk and "flushPendingComputerEvents" in computer_desk,
        "queued display events must start the computer and flush after CraftOS is on")

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

# Configured displays run automatically without stealing CraftOS' foreground event stream.
require("coroutine.create(function() ui.supervise() end)" in autorun,
        "reactive display autorun must drive the canonical supervisor instead of duplicating it")
require("nativePullEventRaw()" in autorun and "os.pullEventRaw = function(filter)" in autorun,
        "reactive display autorun must hook CraftOS events non-blockingly")
require("supervisorFilter" in autorun and 'event[1] == "terminate"' in autorun,
        "supervisor coroutine must preserve filtered event semantics")
require("CONTROLLER_HOST" in autorun and "withControllerHosts" in autorun,
        "controller-only displays must receive a lightweight runtime without a configured boot app")
require("__cc_aeroworks_display_handlers_installed" in autorun,
        "reactive autorun must suppress the older one-path dispatcher after branch integration")
require('return ui.app(function() end)' in controller_host,
        "controller-only runtime host must remain an empty reactive app")
require((ROOT / "examples/cc/display-binding-router.lua").is_file(),
        "legacy display binding Lua router example is missing")

require("python3 tools/verify-display-bindings.py" in workflow,
        "workflow must enforce the display binding architecture")

print(
    "Validated two-level display applications, automatic non-blocking reactive touch supervision, "
    "controller-only input, Sable-aware interaction, start-safe event delivery and sparse reactive frames."
)
