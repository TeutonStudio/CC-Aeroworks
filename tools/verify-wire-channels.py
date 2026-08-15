#!/usr/bin/env python3
"""Validate ComputerControlDesk wire channels, directional controls and DBW multiblock selection."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    bank = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/wire/WireChannelBank.kt")
    api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt")
    access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
    desk = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
    backend = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/drivebywire/DriveByWireWireBackend.kt")
    native_dbw = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/drivebywire/NativeDriveByWireChannels.kt")
    selection = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DriveByWireDeskSelection.kt")
    direction = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ControlDirectionalSignals.kt")
    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/wire/WireChannelSnapshotState.kt")
    mixin = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireClientWireNetworkHandlerMixin.java")
    display_mixin = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireDisplaySourceMixin.java")
    screen = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")
    sidebar = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskComputerSidebar.kt")
    widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/WireChannelManagerWidget.kt")
    payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
    state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
    components = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCDataComponents.kt")
    docs = read("docs/wire-channels.md")

    require('Regex("[a-z][a-z0-9_-]{0,31}")' in bank, "wire names are not constrained")
    require("const val MAX_CHANNELS: Int = 32" in bank, "wire channel limit changed unexpectedly")
    require("val id: UUID" in bank and "val name: String" in bank, "wire definitions need stable UUID identity")
    require("value in 0..15" in bank, "user wire signals must be ordinary redstone 0..15")
    require("snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner === owner" in bank,
            "wire output must be gated by active multiblock ownership")
    require("resetAllInternal()" in bank and "clearSignals()" in bank, "wire fail-safe clearing is missing")
    require("CCDataComponents.WIRE_CHANNELS" in desk, "wire definitions must survive the desk item lifecycle")
    require('"wire_channels"' in components and ".persistent(Codec.STRING)" in components,
            "persistent wire-channel data component is missing")

    public_api = api.split("class ComputerWireAdminLuaApi", 1)[0]
    for method in ("list", "exists", "get", "set", "pulse", "reset", "resetAll", "getInfo", "getBackend", "isEnabled"):
        require(f"fun {method}" in public_api, f"public wires API is missing {method}")
    for forbidden in ("addChannel", "removeChannel", "renameChannel", "fun add(", "fun remove(", "fun rename("):
        require(forbidden not in public_api, f"public wires API exposes configuration mutation: {forbidden}")
    require("ComputerWireLuaApi" in access and "ComputerWireAdminLuaApi" in access,
            "wire APIs must remain scoped to ComputerControlDesk")

    require("WireNetworkManager.trySetSignalAt" in backend, "DBW values are not forwarded")
    require("WireNetworkManager.removeConnection" in backend and "WireNetworkManager.createConnection" in backend,
            "rename/delete must migrate DBW connections")
    require("data class WireConnectionView" in bank and "val side: String" in bank,
            "wire snapshot must expose sink coordinate and block side")
    require("connectionTargets(sourcePos: BlockPos" in bank and "WireConnectionView(" in backend,
            "DBW sink topology is not projected into the channel snapshot")

    # Modular ControlDesk channel identity must come from Aeroworks itself, not DBW's block-level interface.
    require('CONSOLE_WIRE_CHANNELS = "com.mred231.aeroworks.compat.drivebywire.ConsoleWireChannels"' in native_dbw,
            "native DBW discovery must use Aeroworks ConsoleWireChannels")
    require('getMethod("channelsFor", ConsoleBlockEntity::class.java)' in native_dbw,
            "native DBW discovery must call ConsoleWireChannels.channelsFor")
    require('getMethod("parse", String::class.java)' in native_dbw,
            "native DBW discovery must parse exact Aeroworks channel identity")
    for accessor_name in ('getMethod("socket")', 'getMethod("channelId")', 'getMethod("sign")'):
        require(accessor_name in native_dbw, f"native DBW parsed channel lacks {accessor_name}")
    require("MultiChannelWireSource" not in native_dbw,
            "ControlDesk DBW discovery must not fall back to block-level MultiChannelWireSource")

    # Signed Aeroworks axes are two physical redstone channels sharing zero.
    require("(-value).coerceIn(0, 15)" in direction and "value.coerceIn(0, 15)" in direction,
            "signed axes must split into negative/positive 0..15 strengths")
    require('channel == "turn" -> "left" to "right"' in direction,
            "yoke turn must expose left/right")
    require('channel == "pitch" -> "forward" to "back"' in direction,
            "yoke pitch must expose forward/back")
    require("it.socket == socket && it.channelId == channel && it.sign < 0" in direction,
            "negative direction must bind exact Aeroworks socket/channel/sign DBW identity")
    require("it.socket == socket && it.channelId == channel && it.sign > 0" in direction,
            "positive direction must bind exact Aeroworks socket/channel/sign DBW identity")
    require("ControlDirectionalSignals.split" in snapshot and "ChannelSignalMapping" not in snapshot,
            "control snapshot must split signed axes instead of midpoint-normalizing them")
    require("connectionTargets(sourcePos, it)" in snapshot,
            "direction rows must query DBW sinks using exact physical channel ID")

    # Whole active multiblock is one scroll catalogue, every native endpoint retaining sourcePos.
    require("snapshot.members.forEach" in selection and "NativeDriveByWireChannels.channels(member.desk)" in selection,
            "DBW resolver must enumerate Aeroworks channels for every multiblock member")
    require("owner.wireChannelNames()" in selection and "member.pos == owner.blockPos" in selection,
            "user-defined channels must be inserted once at ComputerControlDesk owner")
    require("sourcePos: BlockPos" in selection and "channel: String" in selection,
            "DBW endpoint identity must retain source position plus channel")
    require("!CombinedInputSource.isDisplayPointerModule(module)" in selection,
            "display-pointer channels must be filtered per resolved Aeroworks socket")
    require("DriveByWireDeskSelectionResolver.INSTANCE.resolve" in mixin,
            "DBW client hook must use the whole-desk resolver")
    require("selectedSource = endpoint.getSourcePos()" in mixin and "currentChannel = endpoint.getChannel()" in mixin,
            "scrolling must change both physical DBW source and channel")
    require("selection.next(selectedSource, currentChannel, forward)" in mixin,
            "mouse wheel must traverse the complete multiblock endpoint catalogue")
    require("ConsoleMultiblockDisplayBounds.resolve" in mixin,
            "DBW source outline must cover the whole ControlDesk multiblock")
    require("isDisplayPointerModule" in display_mixin,
            "standalone display pointer channels must still be rejected")

    require("channelsButton" in sidebar and "WireChannelManagerWidget" in screen,
            "computer screen is missing its Channels page")
    require("collapsedGroupIds" in widget and "ChannelRow.Module" in widget,
            "channel/module groups must be collapsible")
    require("ChannelRow.Connection" in widget and "connection.x" in widget and "connection.side" in widget,
            "channel rows must show connected DBW coordinate and block side")
    require("activeComputerDesk" in state, "wire UI requests must resolve the validated desk session")
    for registered in ("RequestWireChannelSnapshotPayload.TYPE", "MutateWireChannelPayload.TYPE", "WireChannelSnapshotPayload.TYPE"):
        require(registered in payloads, f"wire payload is not registered: {registered}")

    require("Channels tab" in docs and "same `WireChannelBank`" in docs,
            "wire documentation must retain GUI/shell shared-state contract")

    print("Validated exact Aeroworks directional 0..15 DBW identities, whole-multiblock scrolling, display-pointer exclusion, sink geometry and persistent user wire channels.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
