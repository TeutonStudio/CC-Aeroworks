#!/usr/bin/env python3
"""Validate the hierarchical ComputerControlDesk peripheral tree contract."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def require_before(source: str, first: str, second: str, message: str) -> None:
    first_index = source.find(first)
    second_index = source.find(second)
    require(first_index >= 0, f"Missing token: {first}")
    require(second_index >= 0, f"Missing token: {second}")
    require(first_index < second_index, message)


def main() -> int:
    api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt")
    graph = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetwork.kt")
    tree = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralTree.kt")
    command = read("src/main/resources/data/computercraft/lua/rom/programs/cc_aeroworks_peripherals.lua")
    autorun = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_peripherals.lua")
    docs = read("docs/peripheral-tree.md")

    require("fun getTree(): Map<String, Any> = runtime.describeTree()" in api, "Lua API does not expose getTree")
    require("fun PeripheralNetworkRuntime.describeTree()" in tree, "Tree serializer is missing")
    require('info["peripherals"] = children' in tree, "Desk tree nodes do not contain child peripherals")
    require('info["handle"] = deskHandle(desk)' in tree, "Desk tree nodes do not expose their handle")
    require('info["handle"] = peripheralHandle(node)' in tree, "Peripheral tree nodes do not expose their handle")
    require('children[node.side.name.lowercase(Locale.ROOT)] = info' in tree, "Peripheral children are not keyed by desk side")
    for token in ('info["x"] = node.pos.x', 'info["y"] = node.pos.y', 'info["z"] = node.pos.z'):
        require(token in tree, f"Peripheral tree metadata is missing {token}")

    for direction in ("NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"):
        require(f"Direction.{direction}" in graph, f"Canonical scan order is missing {direction}")

    require("binding.updateNode(node)" in graph, "Stable bindings are not refreshed with current graph metadata")
    require("private var attached = false" in graph, "Peripheral bindings still begin attached before runtime publication")
    require("fun attach()" in graph, "Peripheral binding does not have an explicit attach phase")
    require("bindings[node.address] = binding" in graph, "New binding is not inserted before attach")
    require_before(
        graph,
        "graph = next",
        "binding.attach()",
        "The new graph must be published before peripheral attach callbacks",
    )
    require_before(
        graph,
        "bindings[node.address] = binding",
        "binding.attach()",
        "The binding directory must contain a new peripheral before its attach callback",
    )

    require('rawget(_G, "peripherals")' in autorun, "Autorun does not detect the embedded peripherals API")
    require('shell.setAlias("peripherals", "cc_aeroworks_peripherals")' in autorun, "Embedded computer does not alias the peripherals command")
    require('rawget(_G, "peripherals")' in command, "Tree command does not use the embedded peripherals API")
    require("networkApi.getTree" in command, "Tree command does not call getTree")
    require('print(("ControlDesk %s%s")' in command, "Tree command does not render desk roots")
    require('print(("  %s -> %s [%s]")' in command, "Tree command does not render child peripherals")

    require("Normale CC:Tweaked-Computer" in docs, "Compatibility behavior is not documented")
    require("peripherals.getTree()" in docs, "Tree API is not documented")
    require("child.handle" in docs, "Delegated child handle usage is not documented")

    print(
        "Validated getTree serialization, side-keyed child devices, embedded-only shell aliasing, "
        "two-phase attachment publication, stable binding metadata and the runtime acceptance contract."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
