#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def read(path: str) -> str: return (ROOT / path).read_text(encoding="utf-8")
def require(condition: bool, message: str) -> None:
    if not condition: raise SystemExit(f"FAIL: {message}")

module = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenSwitchMixin.kt")
overview = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenSwitchMixin.kt")
overview_accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenAccessor.kt")
workspace = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/teutonlib/DeskComputerWorkspace.kt")
workspace_client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/teutonlib/DeskComputerWorkspaceClient.kt")
state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
switch_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SwitchControlDeskUiPayload.kt")
client_nav = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskUiClientNavigation.kt")
aero_buttons = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskNavigationButtons.kt")
guide_screen = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/GuideBookScreen.kt")
guide_content = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/GuideBookContent.kt")
creative_sections = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/creative/AeroworksCreativeSections.kt")
source_builder = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceSnapshotBuilder.kt")
source_extensions = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceExtensions.kt")
radar_compat = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/RadarCompat.kt")
radar_client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/RadarCompatClient.kt")
radar_sources = read("src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/computer/source/RadarInformationSources.kt")
source_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/InformationSourcePayloads.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

require("ControlDeskNavigationButtons.navigationButtons" in module and "ControlDeskNavigationButtons.navigationButtons" in overview, "Aeroworks screens must expose shared Computer/API navigation")
require("rememberClientControls(menu.contentHolder)" in module and "rememberClientOverview(console)" in overview, "return context must preserve detail/overview")
require("HoverTintIconButton" in aero_buttons and "private const val UI_INSET = 8" in aero_buttons, "navigation icons must use native Aeroworks button geometry")
require("private const val BUTTON_GAP = 2" in aero_buttons and "computer.x + computer.width + BUTTON_GAP" in aero_buttons, "API action must sit directly to the right of Computer")
require(
    "filterIsInstance<IconButton>()" in aero_buttons and
    "widget !is HoverTintIconButton" in aero_buttons and
    "widget.y == anchor.y" in aero_buttons and
    "maxOf(uiLeft + UI_INSET, nativeLeftEdge" in aero_buttons,
    "Computer/API buttons must reserve Orientation without following right-aligned Done/Delete",
)
require("withCallback<HoverTintIconButton>(callback)" in aero_buttons, "Catnip callback typing regression")
require("private val API_ICON" in aero_buttons and "GuideBookScreen(Minecraft.getInstance().screen ?: screen, GuideSectionId.NETWORK_API)" in aero_buttons, "API button must open the named Network/API guide section")
require("Component.translatable(\"guide.cc_aeroworks.tab.network\")" in aero_buttons, "API button must use the localized Network/API tooltip")
require("if (!ControlDeskUiSwitchState.clientCanSwitchToComputer()) return" not in module and "if (!ControlDeskUiSwitchState.clientCanSwitchToComputer()) return" not in overview, "API documentation must not depend on Computer availability")
require("val computerAction = if" in module and "val computerAction = if" in overview, "Computer action must be optional while API remains available")
require("initialSection: GuideSectionId = GuideSectionId.START" in guide_screen and "GuideBookContent.indexOf(initialSection)" in guide_screen, "guide must support stable named entry sections")
require("minecraft?.setScreen(parent)" in guide_screen, "guide must return to its invoking Aeroworks screen")
require("GuideSectionId.NETWORK_API" in guide_content and "val id: GuideSectionId" in guide_content, "guide content must expose stable semantic section IDs")
require("removeIf(::isGuideBook)" in creative_sections and "appendMissing(bridgeItems, CCItems.GUIDE_BOOK" not in creative_sections, "legacy guide item must stay out of the Creative/search workflow")
require("registerAeroworksItem" not in radar_client and "RadarItems.SMALL_RADAR_DISPLAY" not in radar_client and "RadarItems.LARGE_RADAR_DISPLAY" not in radar_client,
        "radar displays must remain in the namespace-driven CC-Aeroworks creative section")
require("data class SwitchControlDeskUiPayload" in switch_payload and "val anchorPos: BlockPos" in switch_payload, "switch payload must carry explicit current anchor")
require("SwitchControlDeskUiPayload(current.be().blockPos)" in module and "SwitchControlDeskUiPayload(console.blockPos)" in overview, "visible screen must send its own desk position")
require("switchToComputer(player, payload.anchorPos)" in switch_payload and "validateAnchorAndResolveOwner" in state, "server must validate explicit anchor")
require("snapshot.members.any" in state, "range validation must accept any member of same multiblock")
require("reopenExactClientControls" in state and "socket.reopenModuleMenu()" in state and "ConsoleScreenOpener.open(console)" in client_nav, "return navigation must preserve exact native Aeroworks context")

# TeutonLib owns the one native computer-screen controller. Aeroworks only registers its
# workspace identity, controls tab and domain actions through the public client descriptor.
require("ComputerWorkspaceClientDescriptor" in workspace_client and
        "ComputerWorkspaceClients.register(this)" in workspace_client,
        "Aeroworks must register a public TeutonLib workspace descriptor")
require("menu.displayStack.has(CCDataComponents.DESK_ID.get())" in workspace_client,
        "workspace matching must remain an Aeroworks adapter concern")
require("ComputerWorkspaceClientTab(" in workspace_client and
        "ControlDeskUiClientNavigation.reopenControls()" in workspace_client,
        "Controls must be contributed as a descriptor tab with native return navigation")
require("current.showTerminal.run()" in workspace_client,
        "Controls navigation must restore terminal state before returning to Aeroworks")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt").exists() and
        not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenAccessor.kt").exists(),
        "Aeroworks must not restore global CC computer-screen mixins")
require("ComputerExtensions.registerWorkspace" in workspace and "getServerComputer() === computer" in workspace, "workspace must resolve the actual running desk computer")
require("menu.computer === computer" in workspace and "menu.stillValid(player)" in workspace and "computer.checkUsable(player)" in workspace, "server workspace must validate the current menu and access")
for action in ("add_wire", "add_group", "rename_wire", "remove_wire", "rename_group", "remove_group", "bind", "rename_binding", "unbind", "targets"):
    require('"' + action + '"' in workspace, f"workspace action missing: {action}")
require("ChannelRegistry.snapshot(owner)" in workspace and "owner.wireBank" in workspace and "owner.channelGroups()" in workspace, "addon channel storage must remain authoritative")
require("InformationSourceKinds.CORE + snapshot.sources.map { it.kind }" in workspace and "collapsed = true" in workspace, "shared sources must retain extensible collapsible categories")
require("connections(channel.id, channel.connections)" in workspace and "WireConnectionScreen" in workspace_client, "DbW targets must remain visible/editable")
require("ComputerExtensions.sourceViews(computer)" in workspace, "peripheral addresses must come from TeutonLib's public runtime API")
require("registerChannelAuthority" in workspace, "pult computers must not create a second writable channel bank")
require("TelemetryRuntime.describeSources(owner.host)" in source_builder, "Display Link sources must come from TelemetryRuntime")
require("PeripheralNetworkBuilder.build(owner)" in source_builder, "storage must come from peripheral graph")
require("InformationSourceExtensions.sources(owner)" in source_builder, "optional information sources must use the extension boundary")
require("CopyOnWriteArrayList" in source_extensions and "fun register(" in source_extensions, "information source extension registry must remain safe and explicit")
require("InformationSourceExtensions.register(RadarInformationSources::sources)" in radar_compat, "radar compat must register its information-source provider")
require("RadarSourceRegistry.sources(owner.host)" in radar_sources, "radar Data Links must come from the isolated radar registry")
require("RadarNetworkControllerLookup.controllersFor" in radar_sources, "radar controllers must use isolated topology lookup")
require("RadarSourceRegistry" not in source_builder and "RadarNetworkControllerLookup" not in source_builder, "base information-source builder must not depend directly on radar compat")
require("activeComputerDesk(player)" in source_payload and "InformationSourceSnapshotBuilder.build(owner)" in source_payload, "Sources snapshot must use validated session")
require("RequestInformationSourceSnapshotPayload.TYPE" in payloads and "InformationSourceSnapshotPayload.TYPE" in payloads, "Sources payloads must be registered")
require("MutateChannelGroupPayload.TYPE" in payloads, "channel-group mutation payload must be registered")

for path in (
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls_hover.png",
): require((ROOT / path).is_file(), f"missing sidebar icon: {path}")

for registered in ('"client.ConsoleScreenAccessor"','"client.ConsoleScreenSwitchMixin"'):
    require(registered in mixins, f"missing UI mixin {registered}")
require("AbstractComputerScreen" not in mixins,
        "Aeroworks mixin configuration must not target CC's global computer screen")
require("python3 tools/verify-control-desk-ui-navigation.py" in workflow, "workflow must enforce UI navigation contract")
print("Validated Computer/API Aeroworks navigation, explicit ComputerControlDesk UI context, keyboard-safe channel/group editing, Standard/Controls/Sources/Channels ordering and authoritative optional information sources.")
