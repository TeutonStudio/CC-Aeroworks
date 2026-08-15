#!/usr/bin/env python3
"""Validate directional channels, logical groups, high-level API and DBW multiblock selection."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def read(path: str) -> str: return (ROOT / path).read_text(encoding="utf-8")
def require(condition: bool, message: str) -> None:
    if not condition: raise AssertionError(message)

def main() -> int:
    bank = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/wire/WireChannelBank.kt")
    wire_api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt")
    channel_api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerChannelLuaApi.kt")
    access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
    backend = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/drivebywire/DriveByWireWireBackend.kt")
    native_dbw = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/drivebywire/NativeDriveByWireChannels.kt")
    selection = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DriveByWireDeskSelection.kt")
    session = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DriveByWireDeskSelectionSession.kt")
    direction = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ControlDirectionalSignals.kt")
    semantics = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ControlChannelSemantics.kt")
    registry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ChannelRegistry.kt")
    groups = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ChannelGroupBank.kt")
    group_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ComputerControlDeskChannelGroupsMixin.kt")
    mixin = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireClientWireNetworkHandlerMixin.java")
    signal_filter = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/DriveByWireSignalFilterMixin.java")
    catalog_filter = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/ConsoleWireChannelsDisplayFilterMixin.java")
    screen = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")
    widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/WireChannelManagerWidget.kt")
    network = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/WireChannelPayloads.kt")
    payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
    state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
    components = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCDataComponents.kt")
    command = read("src/main/resources/data/computercraft/lua/rom/programs/cc_aeroworks_channels.lua")
    docs = read("docs/wire-channels.md")

    require('Regex("[a-z][a-z0-9_-]{0,31}")' in bank, "wire names are not constrained")
    require("const val MAX_CHANNELS: Int = 32" in bank and "val id: UUID" in bank, "wire definition contract changed")
    require("value in 0..15" in bank, "user wires must remain ordinary redstone 0..15")
    require("snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner === owner" in bank, "wire fail-safe ownership gate missing")
    public_wire_api = wire_api.split("class ComputerWireAdminLuaApi", 1)[0]
    for method in ("list", "exists", "get", "set", "pulse", "reset", "resetAll", "getInfo", "getBackend", "isEnabled"):
        require(f"fun {method}" in public_wire_api, f"legacy wires API missing {method}")
    require("WireNetworkManager.trySetSignalAt" in backend and "WireNetworkManager.createConnection" in backend, "DBW backend forwarding/migration missing")
    require("connectionTargets(sourcePos: BlockPos" in bank and "WireConnectionView(" in backend, "DBW sink topology must reach GUI")

    require('CONSOLE_WIRE_CHANNELS = "com.mred231.aeroworks.compat.drivebywire.ConsoleWireChannels"' in native_dbw, "native channels must come from Aeroworks")
    require('getMethod("channelsFor", ConsoleBlockEntity::class.java)' in native_dbw and 'getMethod("parse", String::class.java)' in native_dbw, "Aeroworks DBW bridge contract missing")
    require("filterExposedIds" in native_dbw and "isDriveByWireExposed" in native_dbw, "native discovery must share display/DBW semantics")
    require("MultiChannelWireSource" not in native_dbw, "ControlDesk discovery must not use block-level DBW channel API")

    require("(-value).coerceIn(0, 15)" in direction and "value.coerceIn(0, 15)" in direction, "signed axes must split into two redstone outputs")
    require("sign = -1" in direction and "sign = 1" in direction, "directional channel must retain native override sign")
    require('channel == "turn" -> "left" to "right"' in direction and 'channel == "pitch" -> "forward" to "back"' in direction, "yoke direction labels changed")
    require("DISPLAY_POINTER" in semantics and "driveByWire = supported && kind == ControlChannelKind.VEHICLE_CONTROL" in semantics, "display semantics must exclude DBW")
    require("trySetSignalAt" in signal_filter and "isDriveByWireExposed" in signal_filter, "runtime DBW signal guard missing")
    require("filterExposedIds" in catalog_filter, "Aeroworks DBW catalogue filter missing")

    # Whole multiblock has one logical selection session; DBW only sees the current physical endpoint.
    require("snapshot.members.forEach" in selection and "owner.wireChannelNames()" in selection, "DBW selection must enumerate native and user endpoints across whole multiblock")
    require("object DriveByWireDeskSelectionSession" in session and "fun begin" in session and "fun cycle" in session and "fun current" in session, "logical DBW multiblock session missing")
    require("DriveByWireDeskSelectionSession.INSTANCE.begin" in mixin and "DriveByWireDeskSelectionSession.INSTANCE.cycle" in mixin, "DBW client hook must drive logical session")
    require("selectedSource = endpoint.getSourcePos()" in mixin and "currentChannel = endpoint.getChannel()" in mixin, "current endpoint must be mirrored into DBW transport state")
    require("containsMember" in mixin, "clicking any selected multiblock member must clear logical source")
    require("ConsoleMultiblockDisplayBounds.resolve" in mixin, "DBW source outline must cover whole ControlDesk")

    # Stable logical channel IDs and user groups.
    require('id = "control:$deskId:$socket:$moduleId:$nativeChannel:${signal.direction}"' in registry, "canonical control id missing")
    require('id = "wire:${channel.id}"' in registry, "canonical wire UUID id missing")
    require("data class ChannelGroupDefinition" in groups and "data class ChannelGroupBinding" in groups, "user group data model missing")
    require("MAX_GROUPS = 32" in groups and "MAX_BINDINGS = 64" in groups, "user group bounds missing")
    require("target.startsWith(\"control:\") || target.startsWith(\"wire:\")" in groups, "group binding target validation missing")
    require('"channel_groups"' in components and "CHANNEL_GROUPS" in group_mixin, "persistent channel_groups component/lifecycle missing")
    require("collectImplicitComponents" in group_mixin and "applyImplicitComponents" in group_mixin, "channel groups must survive item/block lifecycle")

    # High-level runtime API is additive; low-level controls/wires remain registered.
    for method in ("ls", "stat", "read", "setWire", "pulseWire", "resetWire", "override", "overrideBatch", "release", "releaseAll"):
        require(f"fun {method}" in channel_api, f"channels API missing {method}")
    require('arrayOf("channels")' in channel_api and 'arrayOf("__cc_aeroworks_channel_admin")' in channel_api, "channels public/admin globals missing")
    require("ComputerChannelLuaApi" in access and "ComputerChannelAdminLuaApi" in access and "ComputerWireLuaApi" in access, "logical API must be additive to legacy APIs")
    require("target.sign * value" in registry, "directional high-level override must convert 0..15 into signed native value")
    require("Override batch addresses both directions" in registry, "batch must reject contradictory directions of same native axis")
    for root in ('"modules"', '"wires"', '"groups"'):
        require(root in registry, f"channels.ls root missing {root}")

    # GUI and CraftOS share one server-side ChannelGroupBank.
    require("USER GROUPS" in widget and "ChannelRow.UserGroup" in widget and "ChannelRow.Binding" in widget, "user groups missing from Channels tree")
    require("MISSING" in widget, "unavailable persisted binding must remain visible")
    require("MutateChannelGroupPayload" in screen and "ChannelGroupMutation.BIND" in screen, "GUI must mutate server-side group bank")
    require("owner.channelGroups()" in network and "ChannelRegistry.findById" in network, "group mutation payload must validate against canonical registry")
    require("MutateChannelGroupPayload.TYPE" in payloads, "channel group mutation payload not registered")
    require("channels group add" in command and "channels bind" in command and "channels unbind" in command, "CraftOS channels administration command incomplete")
    require("activeComputerDesk" in state, "channel UI requests must resolve validated desk session")

    require("Channels tab" in docs and "same `WireChannelBank`" in docs and "channels.ls" in docs, "channel documentation incomplete")
    print("Validated logical 0..15 channels, persistent user groups, high-level channels API, DBW multiblock sessions, display isolation and sink geometry.")
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exc:
        print(f"ERROR: {exc}"); raise SystemExit(1)
