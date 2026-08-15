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
    screen = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")
    sidebar = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskComputerSidebar.kt")
    widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/WireChannelManagerWidget.kt")
    payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/WireChannelPayloads.kt")
    payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
    state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
    docs = read("docs/wire-channels.md")

    require('Regex("[a-z][a-z0-9_-]{0,31}")' in bank, "Wire names are not constrained")
    require("const val MAX_CHANNELS: Int = 32" in bank, "Wire channel limit changed unexpectedly")
    require("val id: UUID" in bank and "val name: String" in bank, "Wire definitions lack stable UUID/name identity")
    require("pulseEndTick" in bank, "Server-side pulse state is missing")
    require("snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner === owner" in bank,
            "Wire output is not gated by active multiblock ownership")
    require("resetAllInternal()" in bank and "clearSignals()" in bank, "Wire fail-safe clearing is missing")

    # GUI mutations and shell mutations are administrative frontends over the same bank. Runtime Lua remains read/drive-only.
    require("fun snapshot(): WireChannelBankView" in bank, "Wire UI snapshot model is missing")
    require("fun removeChannel(id: UUID)" in bank and "fun renameChannel(id: UUID" in bank,
            "GUI mutations must target stable channel UUIDs")
    public_api = api.split("class ComputerWireAdminLuaApi", 1)[0]
    for method in ("list", "exists", "get", "set", "pulse", "reset", "resetAll", "getInfo", "getBackend", "isEnabled"):
        require(f"fun {method}" in public_api, f"Public wires API is missing {method}")
    for forbidden in ("addChannel", "removeChannel", "renameChannel", "fun add(", "fun remove(", "fun rename("):
        require(forbidden not in public_api, f"Public wires API exposes configuration mutation: {forbidden}")
    require('getNames(): Array<String> = arrayOf("__cc_aeroworks_wire_admin")' in api,
            "Private wire shell bridge is missing")

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

    # Computer UI gets its own CC-style Channels tab and a server-authoritative management list.
    require("channelsButton" in sidebar and "control_desk_channels" in sidebar,
            "Computer sidebar is missing the Channels tab")
    require("WireChannelManagerWidget" in screen and "ccaeroworks_setChannelMode" in screen,
            "computer screen is missing the channel management work area")
    require("WIRE CHANNELS" in widget and "mouseScrolled" in widget,
            "channel manager must expose a scrollable CC-style list")
    require("RequestWireChannelSnapshotPayload" in screen and "MutateWireChannelPayload" in screen,
            "channel manager must use the server snapshot/mutation protocol")
    require("selected.id" in screen and "selected.connections > 0" in screen and '"Confirm"' in screen,
            "channel mutations must use UUIDs and confirm removal of connected channels")
    require("activeComputerDesk" in state,
            "wire UI payloads must resolve the validated ComputerControlDesk session")
    require("owner.wireBank.renameChannel(payload.id" in payload and "owner.wireBank.removeChannel(payload.id" in payload,
            "server wire UI mutations must target UUID identities")
    require("PacketDistributor.sendToPlayer" in payload,
            "wire UI must receive a server-authoritative bank snapshot")
    for registered in ("RequestWireChannelSnapshotPayload.TYPE", "MutateWireChannelPayload.TYPE", "WireChannelSnapshotPayload.TYPE"):
        require(registered in payloads, f"wire UI payload is not registered: {registered}")

    for path in (
        "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels.png",
        "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels_hover.png",
    ):
        require((ROOT / path).is_file(), f"missing channel sidebar sprite: {path}")

    require("@Pseudo" in mixin, "Optional Drive By Wire client hook is not guarded with @Pseudo")
    require("selectedSource" in mixin and "wireChannelNames()" in mixin,
            "Drive By Wire selection is not resolved per ComputerControlDesk block entity")

    require("Channels tab" in docs and "same `WireChannelBank`" in docs,
            "wire documentation must explain the graphical channel manager and shared state")
    require("never copied or restored" in docs, "Transient fail-safe signal behavior is not documented")

    print(
        "Validated shared shell/GUI wire configuration, public runtime API separation, persistent UUID definitions, "
        "server-authoritative channel snapshots, fail-safe output lifecycle and Drive By Wire migration."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
