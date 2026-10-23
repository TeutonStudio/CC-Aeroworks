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
    handles = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralLuaHandles.kt")
    computer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ConsoleComputer.kt")
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
        "val segments = snapshot.segmentPositions",
        "targetPos in segmentPositions",
        "nearestDesk(segments, segmentIndex, desksByPos)",
        "peripheral.additionalTypes",
        "PeripheralTypeNames.aliases(types)",
        "existing.pos == targetPos",
        "existing.types == types",
        "equivalent(existing.target, peripheral)",
        "UUID.nameUUIDFromBytes",
        '"${desk.address}/${side.name.lowercase(Locale.ROOT)}"',
        '"${desk.address}/segment_${address(segmentPos)}/${side.name.lowercase(Locale.ROOT)}"',
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
    require_tokens(runtime, "PeripheralNetworkRuntime", (
        '"id" to current.networkId',
        "private val attachments = PeripheralAttachmentRuntime(",
        "attachments.refresh(force)",
        "attachments.binding(node.address)",
        "attachments.close()",
        "onAttached = { endpoint -> queueAttached(endpoint.node()) }",
        "onDetached = { endpoint -> queueDetached(endpoint.node()) }",
        'system.queueEvent("peripheral", node.address)',
        'system.queueEvent("peripheral_detach", node.address)',
        "GRAPH_REFRESH_INTERVAL = 5L",
        "handles.isEmpty() -> null",
        "handles.size == 1 -> handles.values.first()",
        "else -> handles",
        "alwaysCollection -> handles",
        "PeripheralTypeNames.isControlDesk(type)",
        "class AeroworksPeripheralTopologyProvider",
        ") : PeripheralTopologyProvider",
        "PeripheralProviders.endpoints(computer)",
        "PeripheralTopology(",
        "PeripheralEndpoint(",
    ))
    require_tokens(handles, "PeripheralLuaHandles", (
        "class DeskLuaHandle",
        "class PeripheralLuaHandle",
        "binding.call(context, name, arguments)",
        "runtime.peripheralsForDesk(address)",
    ))

    require("PeripheralNetworkRuntimes.tick(this)" in computer, "Computer tick does not refresh the peripheral graph")
    removed_monolith = "PeripheralNetwork" + ".kt"
    require(removed_monolith not in "\n".join((builder, graph, runtime, handles)),
            "Refactored peripheral runtime still references the removed monolith")
    require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralBinding.kt").exists(),
            "Aeroworks must not restore the generic binding now owned by TeutonLib")
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
        "Validated split peripheral builder/graph/provider architecture, TeutonLib attachment delegation, stable network IDs, "
        "physical peripheral deduplication, lifecycle events, docs and Lua examples."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
