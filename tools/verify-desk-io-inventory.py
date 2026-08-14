#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


inventory = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/io/DeskIoInventory.kt")
api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerDeskIoLuaApi.kt")
access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
binding = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayBinding.kt")
telemetry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryRuntime.kt")
wires = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/wire/WireChannelBank.kt")
interaction = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleInteractionHandler.kt")
request = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/RequestDeskIoOverviewPayload.kt")
snapshot_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DeskIoOverviewPayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
overview = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DeskIoOverviewScreen.kt")
detail = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DeskIoDisplayConfigScreen.kt")
script_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetDisplayScriptSourcePayload.kt")
runtime = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_runtime.lua")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
workflow = read(".github/workflows/verify.yml")

for category in ("CONTROL", "DISPLAY", "INFORMATION", "OUTPUT"):
    require(category in inventory, f"Desk I/O inventory is missing {category} category")

require("ConsoleMultiblockManager.resolve" in inventory,
        "Desk I/O inventory must derive mounted modules from the current desk multiblock")
require("CCModuleTypes.displayType" in inventory and "CCModuleTypes.radarDisplayType" in inventory,
        "display modules must be classified explicitly")
require("continue" in inventory and 'DeskIoCategory.DISPLAY' in inventory,
        "display modules must not fall through into the control category")
require("TelemetryRuntime.describeSources" in inventory,
        "Display Link telemetry must appear as information objects")
require("wireBank.describeChannels" in inventory,
        "configured wire/redstone channels must appear as output objects")
require('"module:${member.id}:$socket"' in inventory,
        "mounted module IDs must use stable desk identity plus socket")
require('"telemetry:$sourceId"' in inventory,
        "telemetry objects must preserve the stable telemetry source ID")
require('"wire:$channelId"' in inventory,
        "wire objects must preserve the persistent channel UUID")
require("DisplayBindings.describe" in inventory,
        "display inventory objects must expose their content/input binding")
require("fun overview(" in inventory and "compactObject" in inventory,
        "client overview must use a compact projection instead of sending full telemetry tables")
require("RadarSourceRegistry.sources" in inventory,
        "radar display rows must carry the server-visible ingress choices used by the editor")

require('arrayOf("deskio")' in api, "embedded computer must expose the deskio API")
require('"cc_aeroworks.deskio"' in api, "deskio must have a require()-able module name")
for method in ("list", "getSnapshot", "find"):
    require(f"fun {method}" in api, f"deskio API is missing {method}")
require("ComputerDeskIoLuaApi" in access, "deskio API factory is not registered")

require("RequestDeskIoOverviewPayload" in interaction and "event.isCanceled = true" in interaction,
        "embedded desk configuration must enter the unified overview before Aeroworks' 0/1/many shortcut")
require("player.distanceToSqr" in request and "AeroworksTypes.isControlDesk" in request,
        "overview request must validate server-side reach and the target desk")
require("DeskIoInventory.overview" in request and "PacketDistributor.sendToPlayer" in request,
        "overview snapshot must be produced server-side and sent only to the requesting player")
require("MAX_JSON_LENGTH" in snapshot_payload,
        "overview packet must place a hard bound on serialized GUI data")
require("playToClient" in payloads and "DeskIoOverviewPayload.TYPE" in payloads,
        "overview S2C payload is not registered")
require("ConsoleScreenOpener.open" in overview,
        "control rows must delegate configuration back to Aeroworks' native screen dispatcher")
for label in ("CATEGORY_CONTROL", "CATEGORY_DISPLAY", "CATEGORY_INFORMATION", "CATEGORY_OUTPUT"):
    require(label in overview, f"overview screen is missing {label}")
require("DeskIoDisplayConfigScreen" in overview,
        "display rows must open the routing detail editor")

require("data class ScriptSource" in binding and '"script_source"' in binding,
        "script content source is missing from the orthogonal display binding model")
require("SetDisplayScriptSourcePayload" in detail and "SetDisplayTouchScriptPayload" in detail,
        "display editor must configure content and input independently")
require("SetRadarDisplaySourcePayload" in detail,
        "radar display editor must retain explicit ingress selection")
require("DisplayContentSource.ScriptSource" in script_payload and "DeskDisplayType.THREE_DIGIT" in script_payload,
        "script-source payload must remain restricted to the programmable large Desk Display")
require("setDisplayScriptSource" in peripheral,
        "ControlDesk Lua API must expose the new script content source")

require("__cc_aeroworks_display_runtime_installed" in runtime,
        "CraftOS display runtime must guard against duplicate installation")
require("deskio.find" in runtime and '"display"' in runtime,
        "CraftOS display runtime must discover bindings from the shared I/O model")
require("loadfile" in runtime and "script_source" in runtime,
        "CraftOS runtime must load configured content controller modules")
require("lua_handler" in runtime and "onTap" in runtime and "onDoubleTap" in runtime,
        "CraftOS runtime must route configured pointer handlers")
require("os.pullEventRaw" in runtime and "rawPullEventRaw" in runtime,
        "runtime must integrate with CraftOS events without blocking the foreground shell")
require("cc_aeroworks_display_binding_changed" in runtime,
        "runtime must refresh bindings when configuration changes")

require("fun describeSources" in telemetry, "telemetry inventory source contract changed unexpectedly")
require("fun describeChannels" in wires, "wire inventory source contract changed unexpectedly")
require("val content:" in binding and "val input:" in binding,
        "I/O inventory depends on orthogonal display content/input bindings")
require("python3 tools/verify-desk-io-inventory.py" in workflow,
        "workflow must validate the shared Desk I/O inventory")
require((ROOT / "docs/control-desk-io.md").is_file(), "Desk I/O architecture documentation is missing")

print("Validated complete ControlDesk I/O architecture: unified configuration overview, compact S2C snapshot, native control delegation, display source/input editor, Display Link information, persistent wire outputs and CraftOS script-source routing.")
