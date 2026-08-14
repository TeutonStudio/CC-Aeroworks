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
display_target = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayTarget.kt")
state_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityDisplayBindingMixin.kt")
ui_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
radar_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetRadarDisplaySourcePayload.kt")
input_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayTouchScriptPayload.kt")
source_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayScriptSourcePayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Content routing and input routing are orthogonal. Radar/source selection must never replace a
# touch handler and editing a touch handler must never discard the visible content source.
require("sealed interface DisplayContentSource" in binding, "display content-source model is missing")
require("sealed interface DisplayInputBinding" in binding, "display input-binding model is missing")
require("data class DisplayBinding(" in binding and "val content:" in binding and "val input:" in binding,
        "display binding must contain independent content and input axes")
require("data class RadarSource" in binding, "radar content source is missing")
require("data class ScriptSource" in binding, "Lua script content source is missing")
require("data class LuaHandler" in binding, "Lua input handler is missing")
require("fun setContent" in binding and "fun setInput" in binding,
        "binding service must update each axis without replacing the other")
require("ControlChannel" not in binding, "display bindings must not masquerade as Aeroworks ControlChannels")
require("MAX_HANDLER_PATH_LENGTH" in binding and "MAX_SCRIPT_PATH_LENGTH" in binding,
        "Lua input/source paths must be bounded")
require("RadarDisplayType.LARGE" in binding,
        "large Radar Displays must support an independent Lua input handler")

# Legacy NBT must migrate instead of making every existing configured display forget its source.
require('"radar_source" -> RadarSourceKey.fromTag' in binding,
        "legacy radar-source binding migration is missing")
require('"lua_handler" -> tag.getString("path")' in binding,
        "legacy Lua-handler binding migration is missing")
require('put("content", content.toTag())' in binding and 'put("input", input.toTag())' in binding,
        "new NBT format must persist content and input independently")
require('"script_source"' in binding,
        "new content-source format must persist script controllers")

# Radar sources are desk ingress references and reuse each ingress' already synchronized snapshot.
require("ConsoleMultiblockManager.resolve" in registry, "radar sources must be scoped to the desk multiblock")
require("network.memberAt(source.ingressPos)" in registry, "render-time source lookup must use cached memberAt lookup")
require("ccaeroworks_getRadarSnapshot" in registry, "radar routing must reuse the ingress desk snapshot")
require("RadarSourceRegistry.resolveSnapshot" in access, "radar surfaces must honor per-socket source bindings")
require("DisplayBindings.get(desk, socket).content" in access,
        "radar rendering must inspect the content axis, not the input axis")

# A script source owns automatic content production, so Create Display Link text must not overwrite it.
require("DisplayContentSource.ScriptSource" in display_target,
        "Create DisplayTarget must respect script-source content ownership")
require("setDisplayText" in display_target,
        "Create DisplayTarget must retain normal default/manual display writes")

# Bindings persist through the normal ConsoleBlockEntity NBT/client sync path and clear on dismount.
require("CCAeroworksDisplayBindings" in state_mixin, "display bindings must have a persistent NBT key")
require('method = ["write"]' in state_mixin and 'method = ["read"]' in state_mixin,
        "display bindings must participate in ConsoleBlockEntity write/read")
require("binding.isDefault" in state_mixin, "empty composite bindings must not be persisted")
require("dismount(I)Lnet/minecraft/world/item/ItemStack;" in state_mixin,
        "dismount must clear stale display bindings")
require('"ConsoleBlockEntityDisplayBindingMixin"' in mixins,
        "display binding state mixin must be registered")

# Existing native UI remains compatible, while the unified I/O editor has its own payloads.
require("Radar source: local" in ui_mixin, "Radar Display module UI must expose the local/default source")
require("DisplayContentSource.RadarSource" in ui_mixin,
        "Radar Display UI must read the content source")
require("DisplayInputBinding.LuaHandler" in ui_mixin,
        "large display UI must read the input handler")
require("SetRadarDisplaySourcePayload" in ui_mixin, "Radar Display module UI must use the binding payload")
require("RadarSourceRegistry.sources(desk)" in radar_payload,
        "server must validate requested radar sources against the current multiblock")
require("DisplayBindings.setContent" in radar_payload,
        "radar payload must preserve the display input handler")
require("DisplayBindings.setInput" in input_payload,
        "touch-handler payload must preserve the display content source")
require("RadarDisplayType.LARGE" in input_payload,
        "server must accept Lua input handlers on the interactive large Radar Display")
require("DisplayContentSource.ScriptSource" in source_payload,
        "script-source payload must configure the content axis")
require("DeskDisplayType.THREE_DIGIT" in source_payload,
        "script-source payload must reject unsupported display modules")
require("MAX_HANDLER_PATH_LENGTH" in input_payload and "MAX_SCRIPT_PATH_LENGTH" in source_payload,
        "display script payloads must enforce path bounds")
require("SetRadarDisplaySourcePayload.TYPE" in payloads and
        "SetDisplayTouchScriptPayload.TYPE" in payloads and
        "SetDisplayScriptSourcePayload.TYPE" in payloads,
        "all display binding payloads must be registered")

# Lua configuration and input routing keep legacy events while appending the optional handler path.
for method in ("getRadarSources", "getDisplayBinding", "setRadarSource", "setDisplayScriptSource", "setDisplayTouchScript", "clearDisplayBinding"):
    require(f"fun {method}" in peripheral, f"ControlDesk API is missing {method}")
require("DisplayBindings.get(desk, touch.socket).input" in dispatcher,
        "embedded display input must route through the input axis")
require("DisplayBindings.get(desk, touch.socket).input" in peripheral_state,
        "peripheral display input must route through the input axis")
require("CONSOLE_TOUCH_EVENT" in dispatcher, "legacy embedded touch event must remain available")
require('"monitor_touch"' in peripheral_state, "CC:Tweaked monitor_touch compatibility must remain available")
require((ROOT / "examples/cc/display-binding-router.lua").is_file(),
        "display binding Lua router example is missing")

require("python3 tools/verify-display-bindings.py" in workflow,
        "workflow must enforce the display binding architecture")

print("Validated orthogonal display bindings: radar/manual/script content, independent raw/Lua input routing including large radar handlers, legacy migration, content ownership and compatibility events.")
