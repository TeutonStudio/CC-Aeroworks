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
access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
state_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityDisplayBindingMixin.kt")
ui_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
radar_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetRadarDisplaySourcePayload.kt")
script_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayTouchScriptPayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Binding configuration is intentionally separate from Aeroworks input channels.
require("sealed interface DisplayBinding" in binding, "display binding model is missing")
require("data class RadarSource" in binding, "radar source binding is missing")
require("data class LuaHandler" in binding, "Lua handler binding is missing")
require("ControlChannel" not in binding, "display bindings must not masquerade as Aeroworks ControlChannels")
require("MAX_HANDLER_PATH_LENGTH" in binding, "Lua handler path must be bounded")

# Radar sources are desk ingress references and reuse each ingress' already synchronized snapshot.
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

# UI selection is server-authoritative and can only select a source from the same multiblock.
require("Radar source: local" in ui_mixin, "Radar Display module UI must expose the local/default source")
require("SetRadarDisplaySourcePayload" in ui_mixin, "Radar Display module UI must use the binding payload")
require("RadarSourceRegistry.sources(desk)" in radar_payload,
        "server must validate requested radar sources against the current multiblock")
require("CCModuleTypes.radarDisplayType(module.type())" in radar_payload,
        "server must reject source bindings on non-radar modules")
require('"client.ModuleScreenDisplayBindingMixin"' in mixins,
        "Radar Display binding UI mixin must be registered")

# Large normal displays expose the same configuration row for a bounded Lua touch handler.
require("EditBox" in ui_mixin and "Touch script" in ui_mixin,
        "large display module UI must expose a touch script path field")
require("SetDisplayTouchScriptPayload" in ui_mixin,
        "touch script field must persist through a server-authoritative payload")
require("DeskDisplayType.THREE_DIGIT" in script_payload,
        "server must reject Lua handler bindings on unsupported display modules")
require("MAX_HANDLER_PATH_LENGTH" in script_payload,
        "touch script payload must enforce the handler path bound")
require("SetRadarDisplaySourcePayload.TYPE" in payloads and "SetDisplayTouchScriptPayload.TYPE" in payloads,
        "both display binding payloads must be registered")

# Lua configuration and input routing keep legacy events while appending the optional handler path.
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

print("Validated display bindings: per-socket persistence, multiblock radar ingress reuse, server-authoritative radar/script configuration, Lua handler metadata and legacy touch compatibility.")
