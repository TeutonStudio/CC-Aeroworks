#!/usr/bin/env python3
"""Validate Create Display Link telemetry and optional Simulated docking relay contracts."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    targets = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCDisplayTargets.kt")
    display_target = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryDisplayTarget.kt")
    runtime = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryRuntime.kt")
    decoder = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/CreateTelemetryDecoder.kt")
    api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt")
    dock = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/SimulatedDockCompat.kt")
    root = read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt")
    config = read("src/main/kotlin/de/teutonstudio/ccaeroworks/config/CCServerConfig.kt")
    access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
    mods = read("src/main/templates/META-INF/neoforge.mods.toml")
    docs = read("docs/telemetry.md")
    docking_docs = read("docs/docking-telemetry.md")
    example = read("examples/cc/telemetry-dashboard.lua")

    require("CCBlockEntities.COMPUTER_CONTROL_DESK.get()" in targets, "Computer Control Desk telemetry target is missing")
    require("TELEMETRY.get()" in targets, "Telemetry DisplayTarget is not registered")
    require("AeroworksTypes.consoleBlockEntityType()" in targets and "CONTROL_DESK.get()" in targets,
            "Normal Control Desks no longer retain the existing display target")
    require('"simulated", "docking_connector"' in targets,
            "Simulated docking connector is not dynamically registered as a telemetry target")
    require("dev.simulated_team" not in targets, "Target registry has a hard Simulated class dependency")

    for token in (
        "TelemetryRuntime.accept(target, context, text)",
        "DisplayTargetStats(1, 64, this)",
    ):
        require(token in display_target, f"Telemetry DisplayTarget misses contract token: {token}")

    for source in ("fill_level", "count_items", "list_items", "count_fluids", "list_fluids"):
        require(f'"create" to "{source}"' in decoder, f"Structured Create decoder is missing {source}")
    require("ThresholdSwitchBlockEntity" in decoder, "Fill-level decoder does not use Create threshold data")
    require("InvManipulationBehaviour.TYPE" in decoder, "Item decoder does not use Create inventory behaviour")
    require("TankManipulationBehaviour.OBSERVE" in decoder, "Fluid decoder does not use Create tank behaviour")
    require("FilteringBehaviour.TYPE" in decoder, "Smart Observer filtering is not respected")
    require("ItemStack.isSameItemSameComponents" in decoder, "Item variants are not grouped with Create semantics")
    require("displayText.to" not in decoder and "toDoubleOrNull" not in decoder and "Regex(" not in decoder,
            "Structured telemetry appears to parse formatted display text")

    for token in (
        "UUID.nameUUIDFromBytes",
        "Sable.HELPER.getContaining(level, pos)",
        "lastSeenTick",
        "revision",
        "persistentData",
        "cc_aeroworks_telemetry",
        "DisplayLinkBlockEntity",
        "level.isLoaded(source.linkPos)",
    ):
        require(token in runtime, f"Telemetry runtime misses lifecycle/identity token: {token}")

    require('arrayOf("telemetry")' in api, "Embedded computer does not register the telemetry API")
    require('"cc_aeroworks.telemetry"' in api, "Telemetry module name is missing")
    for method in (
        "fun list()", "fun get(", "fun find(", "fun rename(", "fun clearName(", "fun getStatus()",
        "fun getDocks()", "fun getDock(", "fun renameDock(", "fun clearDockName(",
        "fun listTelemetry()", "fun getTelemetry(", "fun getTransferBuffers()",
    ):
        require(method in api, f"Telemetry Lua API is missing {method}")

    for event in (
        "cc_aeroworks_telemetry_added",
        "cc_aeroworks_telemetry_changed",
        "cc_aeroworks_telemetry_removed",
        "cc_aeroworks_dock_changed",
        "cc_aeroworks_remote_telemetry_changed",
    ):
        require(event in root, f"Telemetry event constant is missing: {event}")
    require("TelemetryLuaApi(access, system)" in access, "Telemetry API factory is not attached to embedded computers")

    for token in (
        "subLevel.plot.loadedChunks",
        "SimulatedDockAccess.isDock",
        "getMethod(\"isLocked\")",
        "getMethod(\"getOtherConnector\")",
        "Capabilities.ItemHandler.BLOCK",
        "Capabilities.FluidHandler.BLOCK",
        "Capabilities.EnergyStorage.BLOCK",
        'PERSISTENT_DOCK_ALIAS = "dock_alias"',
    ):
        require(token in dock, f"Dock compatibility is missing token: {token}")
    require("import dev.simulated_team" not in dock, "Simulated compatibility must remain runtime-optional")

    for key in (
        "maxSourcesPerEndpoint", "maxListEntries", "staleAfterTicks",
        "validationIntervalTicks", "dockScanIntervalTicks",
    ):
        require(f'"{key}"' in config, f"Telemetry server config is missing {key}")
    require('modId="simulated"' in mods and 'type="optional"' in mods,
            "Simulated dependency metadata is not optional")

    for text, name in ((docs, "telemetry docs"), (docking_docs, "docking docs")):
        require("Display Link" in text, f"{name} does not explain the Create Display Link path")
        require("telemetry" in text, f"{name} does not document the Lua API")
    require("telemetry.get" in example and "telemetry.getDocks" in example,
            "Telemetry example does not exercise local and docking telemetry")

    print(
        "Validated structured Create telemetry, stable runtime identities, alias persistence, lifecycle events, "
        "optional Simulated/Sable docking relay, transfer-buffer separation, docs and Lua example."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
