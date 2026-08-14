#!/usr/bin/env python3
"""Validate the ComputerControlDesk virtual wire-channel contract."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    bank = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/wire/WireChannelBank.kt")
    api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt")
    access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
    desk = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
    backend = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/drivebywire/DriveByWireWireBackend.kt")
    components = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCDataComponents.kt")
    autorun = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_wires.lua")
    command = read("src/main/resources/data/computercraft/lua/rom/programs/cc_aeroworks_wires.lua")
    mixin = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireClientWireNetworkHandlerMixin.java")
    docs = read("docs/wire-channels.md")

    require('Regex("[a-z][a-z0-9_-]{0,31}")' in bank, "Wire names are not constrained")
    require("const val MAX_CHANNELS: Int = 32" in bank, "Wire channel limit changed unexpectedly")
    require("val id: UUID" in bank and "val name: String" in bank, "Wire definitions lack stable UUID/name identity")
    require("pulseEndTick" in bank, "Server-side pulse state is missing")
    require("snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner === owner" in bank,
            "Wire output is not gated by active multiblock ownership")
    require("resetAllInternal()" in bank and "clearSignals()" in bank, "Wire fail-safe clearing is missing")

    public_api = api.split("class ComputerWireAdminLuaApi", 1)[0]
    for method in ("list", "exists", "get", "set", "pulse", "reset", "resetAll", "getInfo", "getBackend", "isEnabled"):
        require(f"fun {method}" in public_api, f"Public wires API is missing {method}")
    for forbidden in ("addChannel", "removeChannel", "renameChannel", "fun add(", "fun remove(", "fun rename("):
        require(forbidden not in public_api, f"Public wires API exposes configuration mutation: {forbidden}")
    require('getNames(): Array<String> = arrayOf("__cc_aeroworks_wire_admin")' in api,
            "Private wire command bridge is missing")

    require("ComputerWireLuaApi" in access and "ComputerWireAdminLuaApi" in access,
            "Wire APIs are not scoped through the ComputerControlDesk component")
    require("wireBank.tick(newPowered)" in desk, "Wire runtime is not tied to computer power")
    require("wireBank.shutdown()" in desk, "Wire outputs are not cleared during block invalidation")
    require("CCDataComponents.WIRE_CHANNELS" in desk, "Wire definitions are not transferred through item components")
    require('"wire_channels"' in components and ".persistent(Codec.STRING)" in components,
            "Persistent wire-channel data component is missing")

    require("WireNetworkManager.trySetSignalAt" in backend, "Drive By Wire values are not forwarded")
    require("WireNetworkManager.removeConnection" in backend, "Deleted/renamed channels do not update DBW connections")
    require("WireNetworkManager.createConnection" in backend, "Rename does not migrate DBW connections")

    require('rawget(_G, "wires")' in autorun, "Autorun does not detect the ComputerControlDesk wires API")
    require('shell.setAlias("wires", "cc_aeroworks_wires")' in autorun,
            "ComputerControlDesk does not receive the wires shell alias")
    for verb in ('command == "add"', 'command == "remove"', 'command == "rename"', 'command == "info"'):
        require(verb in command, f"Bundled wires command is missing {verb}")
    require("runtime.set" not in command and "runtime.pulse" not in command,
            "Configuration command must not become a runtime signal-control command")

    require("@Pseudo" in mixin, "Optional Drive By Wire client hook is not guarded with @Pseudo")
    require("selectedSource" in mixin and "wireChannelNames()" in mixin,
            "Drive By Wire selection is not resolved per ComputerControlDesk block entity")

    require("created only through the bundled ComputerControlDesk shell command" in docs,
            "Command-only configuration rule is not documented")
    require("never copied or restored" in docs, "Transient fail-safe signal behavior is not documented")

    print(
        "Validated command-only wire configuration, public runtime API separation, persistent UUID definitions, "
        "fail-safe output lifecycle, Drive By Wire forwarding and per-desk channel selection."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
