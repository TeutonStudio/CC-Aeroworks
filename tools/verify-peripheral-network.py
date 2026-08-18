#!/usr/bin/env python3
"""Validate the addressable desk and multiblock peripheral graph contract."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require_tokens(source: str, source_name: str, tokens: tuple[str, ...]) -> None:
    for token in tokens:
        require(token in source, f"{source_name} is missing runtime contract token: {token}")


def main() -> int:
    api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt")
    builder = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetworkBuilder.kt")
    graph = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetworkGraph.kt")
    runtime = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetworkRuntime.kt")
    binding = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralBinding.kt")
    handles = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralLuaHandles.kt")
    mounts = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralMountRegistry.kt")
    computer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
    local = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
    state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
    docs = read("docs/cc-peripheral-api.md")
    programming = read("docs/peripheral-programming.md")
    german_readme = read("README_GER.md")
    english_readme = read("README_ENG.md")
    dashboard = read("examples/cc/dashboard.lua")
    monitor = read("examples/cc/input-monitor.lua")

    require('arrayOf("peripherals")' in api, "Embedded computer does not register the peripherals API")
    require('"cc_aeroworks.peripherals"' in api, "Peripheral API module name is missing")
    require('arrayOf("aeroworks")' not in api, "Legacy aeroworks global API is still registered")
    for method in ("fun find(", "fun findAll(", "fun wrap(", "fun getDesks(", "fun getTypes(", "fun getNetwork(", "fun refresh("):
        require(method in api, f"Global peripherals API is missing {method}")

    require_tokens(builder, "PeripheralNetworkBuilder", (
        "Direction.NORTH",
        "Direction.SOUTH",
        "Direction.EAST",
        "Direction.WEST",
        "Direction.UP",
        "Direction.DOWN",
        "PeripheralCapability.get()",
        "side.opposite",
        "targetPos in deskPositions",
        "peripheral.additionalTypes",
        "PeripheralTypeNames.aliases(types)",
        "existing.pos == targetPos",
        "existing.types == types",
        "equivalent(existing.target, peripheral)",
        "UUID.nameUUIDFromBytes",
        'address = "${desk.address}/${side.name.lowercase(Locale.ROOT)}"',
        'fun address(pos: BlockPos): String = "${pos.x},${pos.y},${pos.z}"',
    ))
    require_tokens(graph, "PeripheralNetworkGraph", (
        "data class PeripheralNetworkGraph",
        "fun matches(type: String)",
        "fun aliases(types: Iterable<String>)",
        "fun isControlDesk(value: String)",
        "compact(value",
        "substringAfter(':', lower)",
    ))
    require_tokens(binding, "PeripheralBinding", (
        "ServerContext.get(system.getLevel().server).peripheralMethods().getSelfMethods(node.target)",
        "GuardedLuaContext(context, this)",
        "method.apply(node.target, guarded, this, arguments)",
        "node.target.attach(this)",
        "node.target.detach(this)",
        "NotAttachedException()",
        "system.mount(",
        "system.unmount(",
        "system.queueEvent(",
        "cleanupMounts(throwable)",
        "mounts.drain()",
        "throwable::addSuppressed",
    ))
    require_tokens(mounts, "PeripheralMountRegistry", (
        "linkedSetOf<String>()",
        "fun drain(): List<String>",
        "locations.clear()",
    ))
    require_tokens(runtime, "PeripheralNetworkRuntime", (
        '"id" to current.networkId',
        "bindings[node.address] = binding",
        "binding.attach()",
        'system.queueEvent("peripheral", node.address)',
        'system.queueEvent("peripheral_detach", node.address)',
        "getAvailablePeripherals",
        "GRAPH_REFRESH_INTERVAL = 5L",
        "handles.isEmpty() -> null",
        "handles.size == 1 -> handles.values.first()",
        "else -> handles",
        "alwaysCollection -> handles",
        "PeripheralTypeNames.isControlDesk(type)",
    ))
    require_tokens(handles, "PeripheralLuaHandles", (
        "class DeskLuaHandle",
        "class PeripheralLuaHandle",
        "binding.call(context, name, arguments)",
        "runtime.peripheralsForDesk(address)",
    ))

    require("PeripheralNetworkRuntimes.tick(this)" in computer, "Computer tick does not refresh the peripheral graph")
    require("PeripheralNetwork.kt" not in "\n".join((builder, graph, runtime, binding, handles)),
            "Refactored peripheral runtime still references the removed monolith")
    require("cc_aeroworks_peripheral_attached" in read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt"), "Attach event constant is missing")
    require("cc_aeroworks_peripheral_detached" in read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt"), "Detach event constant is missing")

    require('override fun getType(): String = "ControlDesk"' in local, "Local desk primary type is not ControlDesk")
    for alias in ("control_desk", "cc_aeroworks:control_desk", "CCAeroworks.PERIPHERAL_TYPE"):
        require(alias in local, f"Local desk alias is missing: {alias}")
    require("fun getDesk(" not in local and "fun getDesks(" not in local,
            "Local desk adapter still contains network-wide getDesk methods")
    require("MULTIBLOCK_INPUT_EVENT" not in state, "Legacy multiblock input polling is still active")
    require("MULTIBLOCK_CHANGED_EVENT" not in state, "Legacy multiblock change polling is still active")

    for source_name, source in (
        ("API documentation", docs),
        ("programming guide", programming),
        ("German README", german_readme),
        ("English README", english_readme),
        ("dashboard example", dashboard),
        ("input monitor example", monitor),
    ):
        require("peripherals.find" in source, f"{source_name} does not demonstrate the current API")
        require("aeroworks.getDesks" not in source, f"{source_name} still documents aeroworks.getDesks")
        require("setDeskDisplay" not in source, f"{source_name} still documents setDeskDisplay methods")

    require('peripherals.find("endermodem")' in docs, "API documentation lacks the unique EnderModem example")
    require("findAll" in docs and "findAll" in programming, "Stable collection lookup is undocumented")
    require("getPeripheralInfo" in docs, "Peripheral handle metadata is undocumented")

    print(
        "Validated split peripheral builder/graph/runtime/binding architecture, partial-attach cleanup, stable network IDs, "
        "physical peripheral deduplication, lifecycle events, docs and Lua examples."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
