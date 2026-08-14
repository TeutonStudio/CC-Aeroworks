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

require('arrayOf("deskio")' in api, "embedded computer must expose the deskio API")
require('"cc_aeroworks.deskio"' in api, "deskio must have a require()-able module name")
for method in ("list", "getSnapshot", "find"):
    require(f"fun {method}" in api, f"deskio API is missing {method}")
require("ComputerDeskIoLuaApi" in access, "deskio API factory is not registered")

require("fun describeSources" in telemetry, "telemetry inventory source contract changed unexpectedly")
require("fun describeChannels" in wires, "wire inventory source contract changed unexpectedly")
require("val content:" in binding and "val input:" in binding,
        "I/O inventory depends on orthogonal display content/input bindings")
require("python3 tools/verify-desk-io-inventory.py" in workflow,
        "workflow must validate the shared Desk I/O inventory")

require((ROOT / "docs/control-desk-io.md").is_file(), "Desk I/O architecture documentation is missing")

print("Validated shared ControlDesk I/O inventory: controls, displays, Display Link information sources and persistent wire outputs share one server-authoritative model without masquerading as Aeroworks modules.")
