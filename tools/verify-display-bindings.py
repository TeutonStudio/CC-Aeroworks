#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


binding = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayBinding.kt")
registry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarSourceRegistry.kt")
catalog = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalog.kt")
catalog_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalogState.kt")
widgets = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DisplayBindingRowWidgets.kt")
access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
state_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityDisplayBindingMixin.kt")
ui_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
radar_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetRadarDisplaySourcePayload.kt")
script_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayTouchScriptPayload.kt")
catalog_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayScriptCatalogPayloads.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Binding configuration remains separate from Aeroworks input channels.
require("sealed interface DisplayBinding" in binding, "display binding model is missing")
require("data class RadarSource" in binding, "radar source binding is missing")
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

# Radar sources are visible row choices, not a cycle button, and selection remains server-authoritative.
require("RadarSourceRowButton" in widgets and "renderCheck" in widgets,
        "radar source rows must render the requested icon/text/check selection treatment")
require("graphics.renderItem" in widgets and "0x777777" in widgets,
        "radar source rows must render an icon and secondary gray network label")
require("RadarSourceChoice" in ui_mixin and "SetRadarDisplaySourcePayload" in ui_mixin,
        "Radar Display module UI must use row choices and the binding payload")
require("RadarSourceRegistry.sources(desk)" in radar_payload,
        "server must validate requested radar sources against the current multiblock")
require("CCModuleTypes.radarDisplayType(module.type())" in radar_payload,
        "server must reject source bindings on non-radar modules")
require('"client.ModuleScreenDisplayBindingMixin"' in mixins,
        "Radar Display binding UI mixin must be registered")

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
require("ScriptSourceDropdownWidget" in widgets and "MAX_VISIBLE_OPTIONS" in widgets,
        "script source UI must be a bounded dropdown")
require("mouseScrolled" in widgets and "expanded" in widgets,
        "script dropdown must own wheel input while open")
require("EditBox" not in ui_mixin and "Touch script" not in ui_mixin,
        "ModuleScreen must not restore the arbitrary touch-script EditBox")
require("RequestDisplayScriptCatalogPayload" in ui_mixin,
        "ModuleScreen must request the server-authoritative script catalog")
require("DisplayScriptCatalog.find" in script_payload,
        "script selection payload must revalidate selected files against the current catalog")
require("DisplayBinding.LuaHandler(descriptor.path)" in script_payload,
        "script binding must persist the catalog's canonical path")
require("PacketDistributor.sendToPlayer" in catalog_payload,
        "script catalog metadata must be returned by a server response payload")
require("RequestDisplayScriptCatalogPayload.TYPE" in payloads and "DisplayScriptCatalogPayload.TYPE" in payloads,
        "script catalog request/response payloads must be registered")

for module_path in (
    "src/main/resources/data/computercraft/lua/rom/modules/main/display.lua",
    "src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua",
):
    require((ROOT / module_path).is_file(), f"missing bundled display API module: {module_path}")
require('require("display")' in read("src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua"),
        "touchdisplay must build on the common display module")

# Existing programmatic configuration and compatibility events remain available.
for method in ("getRadarSources", "getDisplayBinding", "setRadarSource", "setDisplayTouchScript", "clearDisplayBinding"):
    require(f"fun {method}" in peripheral, f"ControlDesk API is missing {method}")
require("handlerPath" in dispatcher and "handlerPath" in peripheral_state,
        "display input events must expose the optional Lua handler path")
require("CONSOLE_TOUCH_EVENT" in dispatcher, "legacy embedded touch event must remain available")
require('"monitor_touch"' in peripheral_state, "CC:Tweaked monitor_touch compatibility must remain available")
require((ROOT / "examples/cc/display-binding-router.lua").is_file(),
        "display binding Lua router example is missing")

require("python3 tools/verify-display-bindings.py" in workflow,
        "workflow must enforce the display binding architecture")

print("Validated display bindings: row-based radar selection, bounded embedded-computer script discovery, server-authoritative catalog selection, bundled display/touchdisplay modules and legacy touch compatibility.")
