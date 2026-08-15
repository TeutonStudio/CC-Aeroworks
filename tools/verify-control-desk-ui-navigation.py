#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def read(path: str) -> str: return (ROOT / path).read_text(encoding="utf-8")
def require(condition: bool, message: str) -> None:
    if not condition: raise SystemExit(f"FAIL: {message}")

module = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenSwitchMixin.kt")
overview = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenSwitchMixin.kt")
overview_accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenAccessor.kt")
computer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")
computer_accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenAccessor.kt")
computer_page = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ComputerDeskPage.kt")
state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
switch_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SwitchControlDeskUiPayload.kt")
client_nav = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskUiClientNavigation.kt")
aero_buttons = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskNavigationButtons.kt")
cc_sidebar = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskComputerSidebar.kt")
channel_widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/WireChannelManagerWidget.kt")
source_widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/InformationSourceManagerWidget.kt")
source_builder = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceSnapshotBuilder.kt")
source_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/InformationSourcePayloads.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

require("ControlDeskNavigationButtons.computerButton" in module and "ControlDeskNavigationButtons.computerButton" in overview, "Aeroworks screens must expose native Computer navigation")
require("rememberClientControls(menu.contentHolder)" in module and "rememberClientOverview(console)" in overview, "return context must preserve detail/overview")
require("HoverTintIconButton" in aero_buttons and "private const val UI_INSET = 8" in aero_buttons, "Computer icon must use native Aeroworks button geometry")
require("withCallback<HoverTintIconButton>(callback)" in aero_buttons, "Catnip callback typing regression")
require("data class SwitchControlDeskUiPayload" in switch_payload and "val anchorPos: BlockPos" in switch_payload, "switch payload must carry explicit current anchor")
require("SwitchControlDeskUiPayload(current.be().blockPos)" in module and "SwitchControlDeskUiPayload(console.blockPos)" in overview, "visible screen must send its own desk position")
require("switchToComputer(player, payload.anchorPos)" in switch_payload and "validateAnchorAndResolveOwner" in state, "server must validate explicit anchor")
require("snapshot.members.any" in state, "range validation must accept any member of same multiblock")
require("reopenExactClientControls" in state and "socket.reopenModuleMenu()" in state and "ConsoleScreenOpener.open(console)" in client_nav, "return navigation must preserve exact native Aeroworks context")

# One native CC screen hosts Terminal, Channels and Information Sources.
require("ComputerSidebar.HEIGHT" in cc_sidebar and "DynamicImageButton" in cc_sidebar, "custom tabs must extend CC native sidebar")
require("controlsButton" in cc_sidebar and "channelsButton" in cc_sidebar and "sourcesButton" in cc_sidebar, "Controls/Channels/Sources sidebar actions missing")
for page in ("TERMINAL", "CHANNELS", "INFORMATION_SOURCES"):
    require(page in computer_page, f"Computer page state missing {page}")
require("ccaeroworks_setPage" in computer and "ComputerDeskPage.CHANNELS" in computer and "ComputerDeskPage.INFORMATION_SOURCES" in computer, "computer work areas must switch through one page state")
require("WireChannelManagerWidget" in computer and "InformationSourceManagerWidget" in computer, "embedded work-area widgets missing")
require("RequestWireChannelSnapshotPayload" in computer and "RequestInformationSourceSnapshotPayload" in computer, "work areas must request server-authoritative snapshots")
require("InformationSourceSnapshotState.clear()" in computer and "WireChannelSnapshotState.clear()" in computer, "entering a work area must discard stale snapshot state")
require('@Accessor("terminal")' in computer_accessor, "computer screen must use native terminal widget")
require("options.keyInventory.matches(keyCode, scanCode)" in computer, "focused editor must consume configured inventory key")
require("private val ccaeroworks_snapshotIntervalTicks: Long = 20L" in computer, "snapshot cadence must stay instance-private for Mixin safety")
require("SNAPSHOT_INTERVAL_TICKS" not in computer and "companion object" not in computer, "computer Mixin must not emit static helper state")
require("+Wire" in computer and "+Group" in computer and "MutateChannelGroupPayload" in computer, "Channels footer must expose wire and user-group administration")
require("USER GROUPS" in channel_widget and "ChannelRow.UserGroup" in channel_widget and "ChannelRow.Binding" in channel_widget, "Channels tree must render user groups/bindings")
require("collapsedGroupIds" in channel_widget, "Channels hierarchy must remain collapsible")
require("ChannelRow.Connection" in channel_widget, "DBW sink rows must remain visible")
require("collapsedKinds" in source_widget and "InformationSourceKind.entries" in source_widget, "Information Sources must remain grouped/collapsible")

require("TelemetryRuntime.describeSources(owner)" in source_builder, "Display Link sources must come from TelemetryRuntime")
require("PeripheralNetworkBuilder.build(owner)" in source_builder, "storage must come from peripheral graph")
require("RadarSourceRegistry.sources(owner)" in source_builder, "radar Data Links must come from registry")
require("RadarNetworkControllerLookup.controllerFor" in source_builder, "radar controllers must use topology lookup")
require("activeComputerDesk(player)" in source_payload and "InformationSourceSnapshotBuilder.build(owner)" in source_payload, "Sources snapshot must use validated session")
require("RequestInformationSourceSnapshotPayload.TYPE" in payloads and "InformationSourceSnapshotPayload.TYPE" in payloads, "Sources payloads must be registered")
require("MutateChannelGroupPayload.TYPE" in payloads, "channel-group mutation payload must be registered")

require("package de.teutonstudio.ccaeroworks.client" in computer_page and "enum class ComputerDeskPage" in computer_page, "ComputerDeskPage must stay outside mixin package")
require("import de.teutonstudio.ccaeroworks.client.ComputerDeskPage" in computer, "screen Mixin must import normal page state")
require("enum class ComputerDeskPage" not in computer, "screen Mixin must not emit top-level helper classes")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ComputerDeskPage.kt").exists(), "ComputerDeskPage must never return to configured Mixin package")

for path in (
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls_hover.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels_hover.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_sources.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_sources_hover.png",
): require((ROOT / path).is_file(), f"missing sidebar icon: {path}")

for registered in ('"client.ConsoleScreenAccessor"','"client.ConsoleScreenSwitchMixin"','"client.AbstractComputerScreenAccessor"'):
    require(registered in mixins, f"missing UI mixin {registered}")
require("python3 tools/verify-control-desk-ui-navigation.py" in workflow, "workflow must enforce UI navigation contract")
print("Validated explicit ComputerControlDesk UI context, keyboard-safe channel/group editing, three native CC work areas and authoritative information sources.")
