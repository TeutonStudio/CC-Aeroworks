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


def main() -> int:
    api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt")
    graph = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetwork.kt")
    computer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
    local = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
    state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
    docs = read("docs/cc-peripheral-api.md")
    programming = read("docs/peripheral-programming.md")
    readme = read("README.md")
    dashboard = read("examples/cc/dashboard.lua")
    monitor = read("examples/cc/input-monitor.lua")

    require('arrayOf("peripherals")' in api, "Embedded computer does not register the peripherals API")
    require('"cc_aeroworks.peripherals"' in api, "Peripheral API module name is missing")
    require('arrayOf("aeroworks")' not in api, "Legacy aeroworks global API is still registered")
    for method in ("fun find(", "fun findAll(", "fun wrap(", "fun getDesks(", "fun getTypes(", "fun getNetwork(", "fun refresh("):
        require(method in api, f"Global peripherals API is missing {method}")

    for token in (
        "Direction.values()",
        "PeripheralCapability.get()",
        "side.opposite",
        "targetPos in deskPositions",
        "peripheral.additionalTypes",
        "PeripheralTypeNames.aliases(types)",
        "ServerContext.get(system.getLevel().server).peripheralMethods().getSelfMethods(node.target)",
        "GuardedLuaContext(context, this)",
        "method.apply(node.target, guarded, this, arguments)",
        "node.target.attach(this)",
        "node.target.detach(this)",
        "NotAttachedException()",
        "system.mount(",
        "system.unmount(",
        "system.queueEvent(",
        'system.queueEvent("peripheral", node.address)',
        'system.queueEvent("peripheral_detach", node.address)',
        "getAvailablePeripherals",
        "getMainThreadMonitor",
        "GRAPH_REFRESH_INTERVAL = 5L",
    ):
        require(token in graph, f"Peripheral graph is missing runtime contract token: {token}")

    require("PeripheralNetworkRuntimes.tick(this)" in computer, "Computer tick does not refresh the peripheral graph")
    require("handles.isEmpty() -> null" in graph, "find does not return nil for zero matches")
    require("handles.size == 1 -> handles.values.first()" in graph, "find does not return the direct unique handle")
    require("else -> handles" in graph, "find does not return a collection for multiple matches")
    require("alwaysCollection -> handles" in graph, "findAll does not force a collection")
    require("isControlDesk(type)" in graph, "ControlDesk collection special case is missing")
    require('address = "${desk.address}/${side.name.lowercase(Locale.ROOT)}"' in graph, "Peripheral address lacks desk position and side")
    require('fun address(pos: BlockPos): String = "${pos.x},${pos.y},${pos.z}"' in graph, "Desk address is not canonical x,y,z")
    require("compact(value" in graph and "substringAfter(':', lower)" in graph, "Peripheral type aliases are not normalized")
    require("cc_aeroworks_peripheral_attached" in read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt"), "Attach event constant is missing")
    require("cc_aeroworks_peripheral_detached" in read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt"), "Detach event constant is missing")

    require('override fun getType(): String = "ControlDesk"' in local, "Local desk primary type is not ControlDesk")
    for alias in ("control_desk", "cc_aeroworks:control_desk", "CCAeroworks.PERIPHERAL_TYPE"):
        require(alias in local, f"Local desk alias is missing: {alias}")
    require("getDesk" not in local, "Local desk adapter still contains network-wide getDesk methods")
    require("MULTIBLOCK_INPUT_EVENT" not in state, "Legacy multiblock input polling is still active")
    require("MULTIBLOCK_CHANGED_EVENT" not in state, "Legacy multiblock change polling is still active")

    for source_name, source in (
        ("API documentation", docs),
        ("programming guide", programming),
        ("README", readme),
        ("dashboard example", dashboard),
        ("input monitor example", monitor),
    ):
        require("peripherals.find" in source, f"{source_name} does not demonstrate the new API")
        require("aeroworks.getDesks" not in source, f"{source_name} still documents aeroworks.getDesks")
        require("setDeskDisplay" not in source, f"{source_name} still documents setDeskDisplay methods")

    require('peripherals.find("endermodem")' in docs, "API documentation lacks the unique EnderModem example")
    require("findAll" in docs and "findAll" in programming, "Stable collection lookup is undocumented")
    require("getPeripheralInfo" in docs, "Peripheral handle metadata is undocumented")

    print(
        "Validated local ControlDesk adapters, automatic multiblock scanning, unique type lookup, guarded real "
        "peripheral delegation, lifecycle events, documentation and Lua examples."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
