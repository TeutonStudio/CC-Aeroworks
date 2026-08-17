#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


binding = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayBinding.kt")
radar_bindings = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/display/RadarDisplayBindings.kt")
registry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/display/RadarSourceRegistry.kt")
catalog = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalog.kt")
catalog_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalogState.kt")
widgets = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DisplayBindingRowWidgets.kt")
radar_widgets = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/client/RadarSourceOptions.kt")
selector = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/SourceSelectorWidget.kt")
access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/compat/aeroworks/RadarDeskAccess.kt")
state_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityDisplayBindingMixin.kt")
ui_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
radar_ui_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/mixin/client/RadarModuleScreenMixin.kt")
radar_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/network/SetRadarDisplaySourcePayload.kt")
script_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayTouchScriptPayload.kt")
catalog_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayScriptCatalogPayloads.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
radar_payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/network/RadarPayloads.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
radar_peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/compat/computercraft/RadarControlDeskPeripheral.kt")
computer_desk = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
display_module = read("src/main/resources/data/computercraft/lua/rom/modules/main/display.lua")
handler_runtime = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua")
router_example = read("examples/cc/display-binding-router.lua")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
radar_mixins = read("src/main/resources/cc_aeroworks_radarcompat.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Binding configuration remains separate from Aeroworks input channels.
require("sealed interface DisplayBinding" in binding, "display binding model is missing")
require("data class Extension" in binding, "generic extension binding model is missing")
require('const val TYPE = "radar_source"' in radar_bindings, "radar source extension binding identity is missing")
require("DisplayBindingExtensions.register" in radar_bindings, "radar binding extension is not registered")
require("data class LuaHandler" in binding, "Lua handler binding is missing")
require("ControlChannel" not in binding, "display bindings must not masquerade as Aeroworks ControlChannels")
require("MAX_HANDLER_PATH_LENGTH" in binding, "Lua handler path must be bounded")

# Radar routing continues to reuse each ingress' already synchronized native Create: Radars snapshot.
require("ConsoleMultiblockManager.resolve" in registry, "radar sources must be scoped to the desk multiblock")
require("network.memberAt(source.ingressPos)" in registry, "render-time source lookup must use cached memberAt lookup")
require("ccaeroworks_getRadarSnapshot" in registry, "radar routing must reuse the ingress desk snapshot")
require("MAX_SYNCED_TRACKS" not in registry, "routing must not create a second track serialization path")
require("RadarSourceRegistry.resolveSnapshot" in access, "radar surfaces must honor per-socket source bindings")

# Bindings persist through the normal ConsoleBlockEntity NBT/client sync path and clear on dismount.
require("CCAeroworksDisplayBindings" in state_mixin, "display bindings must have a persistent NBT key")
require('method = ["write"]' in state_mixin and 'method = ["read"]' in state_mixin,
        "display bindings must participate in ConsoleBlockEntity write/read")
require("dismount(I)Lnet/minecraft/world/item/ItemStack;" in state_mixin,
        "dismount must clear stale display bindings")
require('"ConsoleBlockEntityDisplayBindingMixin"' in mixins,
        "display binding state mixin must be registered")

# Radar sources use the shared one-row selector and the Network Filterer item as their source icon.
require("radarSourceOption" in radar_widgets and "SourceSelectorIcon.Item" in radar_widgets,
        "radar source presentation must provide item icon/title/network text to the shared selector")
require('ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")' in radar_widgets,
        "radar source presentation must use the Create: Radars Network Filterer registry item")
require("descriptor.radarPos" not in radar_widgets,
        "radar source icon must never regress to the radar bearing/radar block")
require("SourceSelectorWidget<RadarSourceChoice>" in radar_ui_mixin and "SetRadarDisplaySourcePayload" in radar_ui_mixin,
        "Radar Display module UI must use the shared selector and the binding payload")
require("RadarSourceRegistry.sources(desk)" in radar_payload,
        "server must validate requested radar sources against the current multiblock")
require("RadarModuleTypes.radarDisplayType(module.type())" in radar_payload,
        "server must reject source bindings on non-radar modules")
require('"client.ModuleScreenDisplayBindingMixin"' in mixins,
        "Script Display binding UI mixin must be registered")
require('"client.RadarModuleScreenMixin"' in radar_mixins,
        "Radar Display binding UI mixin must be registered by radar compat")

# Script sources are discovered from the embedded computer instead of accepting arbitrary typed paths.
require("createRootMount()" in catalog, "script catalog must scan the embedded computer root mount")
for limit in ("MAX_SCRIPTS", "MAX_FILE_SIZE", "MAX_DEPTH", "MAX_PATH_LENGTH"):
    require(limit in catalog, f"script catalog is missing bound {limit}")
require('"require"' in catalog and '"display"' in catalog and '"touchdisplay"' in catalog,
        "script catalog must classify display/touchdisplay require calls")
require("skipTrivia" in catalog and "skipQuoted" in catalog,
        "script capability discovery must ignore comments and quoted text")
require("DisplayScriptCatalogState" in catalog_state and "BlockPos" in catalog_state,
        "client catalog metadata must be keyed to desk/socket context")
require("scriptSourceOptions" in widgets and "SourceSelectorWidget<String>" in ui_mixin,
        "script source UI must use the same bounded selector as radar sources")
require("mouseScrolled" in selector and "expanded" in selector and "MAX_VISIBLE_OPTIONS" in selector,
        "shared source selector must own wheel input while open")
require("EditBox" not in ui_mixin and "Touch script" not in ui_mixin,
        "ModuleScreen must not restore the arbitrary touch-script EditBox")
require("RequestDisplayScriptCatalogPayload" in ui_mixin,
        "ModuleScreen must request the server-authoritative script catalog")
require("DisplayScriptCatalog.find" in script_payload,
        "script selection payload must revalidate selected files against the current catalog")
require("DisplayBinding.LuaHandler(descriptor.path)" in script_payload,
        "script binding must persist the catalog's canonical path")
require("SableInteractionGeometry.withinReach" in script_payload and
        "SableInteractionGeometry.mayInteract" in script_payload,
        "script selection must validate reach in rendered Sable space")
require("SableInteractionGeometry.withinReach" in catalog_payload and
        "SableInteractionGeometry.mayInteract" in catalog_payload,
        "script catalog requests must validate reach in rendered Sable space")
require("player.distanceToSqr(pos.center)" not in script_payload and
        "player.distanceToSqr(pos.center)" not in catalog_payload and
        "player.distanceToSqr(payload.pos.center)" not in script_payload,
        "display script source packets must not compare the player to Sable plot coordinates")
require("PacketDistributor.sendToPlayer" in catalog_payload,
        "script catalog metadata must be returned by a server response payload")
require("RequestDisplayScriptCatalogPayload.TYPE" in payloads and "DisplayScriptCatalogPayload.TYPE" in payloads,
        "script catalog request/response payloads must be registered")
require("SetRadarDisplaySourcePayload.TYPE" in radar_payloads and "SetRadarDisplaySourcePayload" not in payloads,
        "radar source payload must be registered only by radar compat")

for module_path in (
    "src/main/resources/data/computercraft/lua/rom/modules/main/display.lua",
    "src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua",
):
    require((ROOT / module_path).is_file(), f"missing bundled display API module: {module_path}")
require('require("display")' in read("src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua"),
        "touchdisplay must build on the common display module")

# Embedded display bindings execute automatically without consuming the raw CC event contract.
require('"id" to (desk as DeskIdentityAccess).ccaeroworks_getDeskId().toString()' in peripheral,
        "ControlDesk getInfo must expose the stable desk id used by embedded display events")
require("info.id == event.deskId" in display_module,
        "display.resolve must retain stable-id fallback for older embedded display events")
require("pcall(api.wrap, event.deskX, event.deskY, event.deskZ, \"ControlDesk\")" in display_module,
        "display.resolve must directly wrap the source desk when event coordinates are available")
require("os.pullEventRaw = function(filter)" in handler_runtime and "nativePullEventRaw()" in handler_runtime,
        "display handler runtime must be a non-blocking CraftOS event hook")
require('event[1] ~= "cc_aeroworks_console_display_input"' in handler_runtime,
        "automatic handler runtime must consume embedded console display events")
require("cc_aeroworks_desk_display_input" not in handler_runtime,
        "automatic handler runtime must not execute owner-local script paths on external computers")
require("handlerBaseEnvironment = _ENV" in handler_runtime and
        "handlerRequire = require" in handler_runtime and
        "handlerPackage = package" in handler_runtime,
        "automatic handler runtime must preserve the CraftOS shell module environment")
require("_G = handlerGlobalEnvironment" in handler_runtime and
        "loadfile(path, nil, createHandlerEnvironment())" in handler_runtime,
        "selected handlers must load with shell require/package while retaining computer globals")
require("local chunk, loadError = loadfile(path)" not in handler_runtime and "local cache" not in handler_runtime,
        "selected display handlers must not fall back to the BIOS environment or a stale cache")
require('event[1] == filter or event[1] == "terminate"' in handler_runtime,
        "event hook must preserve filtered pullEvent and termination semantics")
require("lastSignature" in handler_runtime and "lastEpoch" in handler_runtime,
        "event hook must deduplicate a touch event observed by parallel event consumers")
require("handler.onTap or handler.onPointer" in handler_runtime and
        "handler.onDoubleTap or handler.onPointer" in handler_runtime,
        "automatic runtime must dispatch tap and double-tap callbacks")
require("deskX = event[15]" in handler_runtime and
        "deskY = event[16]" in handler_runtime and
        "deskZ = event[17]" in handler_runtime,
        "automatic handler events must expose appended source desk coordinates")
require("member.pos.x" in dispatcher and "member.pos.y" in dispatcher and "member.pos.z" in dispatcher,
        "embedded display events must append exact source desk coordinates")

# A touch must not disappear if source discovery created an embedded computer which is still off.
require("MAX_PENDING_COMPUTER_EVENTS" in computer_desk and "pendingComputerEvents" in computer_desk,
        "embedded event delivery must use a bounded pending queue")
require("fun queueComputerEventWhenReady" in computer_desk and "computer.turnOn()" in computer_desk,
        "embedded event delivery must start CraftOS when a display event arrives")
require("pendingComputerEvents.isNotEmpty()" in computer_desk and
        "if (newPowered) flushPendingComputerEvents(computer)" in computer_desk,
        "pending display events must flush only after CC:Tweaked reports the computer as on")
require("pendingComputerEvents.clear()" in computer_desk,
        "pending embedded events must be discarded when the computer is closed")
require(dispatcher.count("owner.queueComputerEventWhenReady(") >= 2,
        "display and legacy touch events must use start-safe embedded delivery")
require("computer.queueEvent(" not in dispatcher,
        "display dispatcher must not directly queue events which CC:Tweaked drops while off")

# The explicit compatibility router must execute the same display/touchdisplay modules correctly.
require("handlerRequire = require" in router_example and
        "loadfile(path, nil, createHandlerEnvironment())" in router_example,
        "display binding router example must load handlers with the shell module environment")
require("local cache" not in router_example,
        "display binding router example must not retain stale handler chunks")

# Existing programmatic configuration and compatibility events remain available.
for method in ("getDisplayBinding", "setDisplayTouchScript", "clearDisplayBinding"):
    require(f"fun {method}" in peripheral, f"ControlDesk core API is missing {method}")
for method in ("getRadarSources", "setRadarSource"):
    require(f"fun {method}" in radar_peripheral, f"Radar compat ControlDesk API is missing {method}")
require("handlerPath" in dispatcher and "handlerPath" in peripheral_state,
        "display input events must expose the optional Lua handler path")
require("CONSOLE_TOUCH_EVENT" in dispatcher, "legacy embedded touch event must remain available")
require('"monitor_touch"' in peripheral_state, "CC:Tweaked monitor_touch compatibility must remain available")
require((ROOT / "examples/cc/display-binding-router.lua").is_file(),
        "display binding Lua router example is missing")

require("python3 tools/verify-display-bindings.py" in workflow,
        "workflow must enforce the display binding architecture")

print("Validated display bindings, shared radar/script source selector UI, Sable-aware script selection, direct source-desk resolution, automatic reloadable touch handlers, and legacy touch compatibility.")
