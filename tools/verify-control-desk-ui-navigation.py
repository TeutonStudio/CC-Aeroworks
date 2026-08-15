#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


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


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


require("ControlDeskNavigationButtons.computerButton" in module, "ModuleScreen must use native bottom-row PC button")
require("ControlDeskNavigationButtons.computerButton" in overview, "ConsoleScreen overview must use native bottom-row PC button")
require("rememberClientControls(menu.contentHolder)" in module, "detail screen must retain exact ConsoleSocket")
require("rememberClientOverview(console)" in overview, "overview screen must retain overview return context")
require("HoverTintIconButton" in aero_buttons, "Aeroworks navigation must use native HoverTintIconButton")

require("private const val UI_INSET = 8" in aero_buttons,
        "PC button must use Aeroworks' eight-pixel left content inset")
require("val buttonX = uiLeft + UI_INSET" in aero_buttons,
        "PC button must derive X exclusively from the screen's left edge")
require(".setX(" not in aero_buttons,
        "PC navigation must never move Aeroworks' native Delete/Done buttons")
require("computerButton(this, leftPos, Runnable" in module,
        "ModuleScreen PC button must anchor to the native leftPos")
require('@Accessor("windowLeft")' in overview_accessor and "ccaeroworks_getWindowLeft" in overview_accessor,
        "ConsoleScreen must expose its exact native windowLeft geometry")
require("accessor.ccaeroworks_getWindowLeft()" in overview,
        "ConsoleScreen PC button must anchor to the native windowLeft")

# Current screen context, not stale world-interaction history, must drive the Computer button.
require("data class SwitchControlDeskUiPayload" in switch_payload and "val anchorPos: BlockPos" in switch_payload,
        "computer UI switch payload must carry an explicit desk anchor")
require("buffer.writeBlockPos(payload.anchorPos)" in switch_payload and "buffer.readBlockPos()" in switch_payload,
        "computer UI switch payload must serialize its desk anchor")
require("SwitchControlDeskUiPayload(current.be().blockPos)" in module,
        "detail screen must send its current ConsoleSocket desk position")
require("SwitchControlDeskUiPayload(console.blockPos)" in overview,
        "overview screen must send its current ConsoleBlockEntity position")
require("switchToComputer(player, payload.anchorPos)" in switch_payload,
        "server must validate the explicit anchor instead of stale click state")
require("validateAnchorAndResolveOwner" in state and "sessions[player.uuid] = Session" in state,
        "validated explicit UI anchors must refresh the server session used by embedded pages")
require("snapshot.members.any" in state,
        "server interaction distance must accept any member of the same desk multiblock")

require("withCallback<HoverTintIconButton>(callback)" in aero_buttons,
        "Aeroworks button callback must specify Catnip's generic widget return type explicitly")
require("button.withCallback(callback)" not in aero_buttons,
        "raw Catnip withCallback(callback) call reintroduces Kotlin type-inference failure")
require("Button.builder" not in module,
        "ModuleScreen must not use the legacy vanilla Computer text button")

require("ClientReturnMode.OVERVIEW" in state and "ClientReturnMode.DETAIL" in state,
        "return state must distinguish overview and exact detail")
require("reopenExactClientControls" in state and "socket.reopenModuleMenu()" in state,
        "exact detail return must preserve ConsoleSocket/subPath")
require("ConsoleScreenOpener.open(console)" in client_nav,
        "fallback/overview return must use Aeroworks' native dispatcher")

# Native CC screen hosts mutually exclusive Channels and Information Sources work areas.
require("ComputerSidebar.HEIGHT" in cc_sidebar, "custom tabs must attach below native CC sidebar")
require("GuiSprites.getComputerTextures" in cc_sidebar, "custom tabs must reuse CC family sidebar sprite")
require("DynamicImageButton" in cc_sidebar, "custom tabs must use CC's native sidebar button type")
require("extensionIndex" in cc_sidebar and "TAB_HEIGHT + TAB_GAP" in cc_sidebar,
        "custom sidebar must support multiple vertical extension segments")
require("controlsButton" in cc_sidebar and "channelsButton" in cc_sidebar and "sourcesButton" in cc_sidebar,
        "ComputerControlDesk must expose Controls, Channels and Information Sources sidebar actions")
require("ComputerDeskPage.INFORMATION_SOURCES" in computer and "INFORMATION_SOURCES" in computer_page,
        "Computer screen page state must include Information Sources")
require("InformationSourceManagerWidget" in computer and "RequestInformationSourceSnapshotPayload" in computer,
        "Information Sources page must use its dedicated work area and server snapshot")
require("InformationSourceSnapshotState.clear()" in computer,
        "entering Information Sources must discard stale client source metadata")
require("WireChannelManagerWidget" in computer and "ccaeroworks_setChannelMode" in computer,
        "Channels tab must retain its management work area")
require('@Accessor("terminal")' in computer_accessor,
        "computer screen mixin must access the native terminal widget instead of guessing geometry")
require("options.keyInventory.matches(keyCode, scanCode)" in computer,
        "focused channel-name input must consume the configured inventory key instead of hard-coding E")
require("private val ccaeroworks_snapshotIntervalTicks: Long = 20L" in computer,
        "Computer screen snapshot cadence must be an instance-private @Unique field safe for Sponge Mixin")
require("SNAPSHOT_INTERVAL_TICKS" not in computer and "companion object" not in computer,
        "Computer screen mixin must not emit a non-private static snapshot constant")
require("collapsedKinds" in source_widget and "InformationSourceKind.entries" in source_widget,
        "Information Sources sections must be collapsible and grouped by source kind")

# Information sources project existing authoritative owners; they do not rescan duplicate systems.
require("TelemetryRuntime.describeSources(owner)" in source_builder,
        "Display Link sources must come from TelemetryRuntime")
require("PeripheralNetworkBuilder.build(owner)" in source_builder,
        "storage connections must come from the canonical peripheral network")
require("RadarSourceRegistry.sources(owner)" in source_builder,
        "radar Data Links must come from the synchronized radar source registry")
require("RadarNetworkControllerLookup.controllerFor" in source_builder,
        "radar network controllers must use the read-only Create Radars topology lookup")
require("activeComputerDesk(player)" in source_payload and "InformationSourceSnapshotBuilder.build(owner)" in source_payload,
        "Information Source requests must use the validated active ComputerControlDesk session")
require("RequestInformationSourceSnapshotPayload.TYPE" in payloads and "InformationSourceSnapshotPayload.TYPE" in payloads,
        "Information Source payloads must be registered in both directions")

# Sponge Mixin reserves the configured mixin package tree.
require("package de.teutonstudio.ccaeroworks.client" in computer_page and "enum class ComputerDeskPage" in computer_page,
        "ComputerDeskPage must live outside the reserved mixin package tree")
require("import de.teutonstudio.ccaeroworks.client.ComputerDeskPage" in computer,
        "computer screen mixin must import the normal client-side page state")
require("enum class ComputerDeskPage" not in computer,
        "computer screen mixin file must not emit a top-level helper class into the mixin package")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ComputerDeskPage.kt").exists(),
        "ComputerDeskPage must never move back under the configured mixin package")

for path in (
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls_hover.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels_hover.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_sources.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_sources_hover.png",
):
    require((ROOT / path).is_file(), f"missing sidebar icon sprite: {path}")

require('"client.ConsoleScreenAccessor"' in mixins, "ConsoleScreen accessor mixin must be registered")
require('"client.ConsoleScreenSwitchMixin"' in mixins, "ConsoleScreen switch mixin must be registered")
require('"client.AbstractComputerScreenAccessor"' in mixins, "CC screen accessor mixin must be registered")
require("python3 tools/verify-control-desk-ui-navigation.py" in workflow,
        "workflow must enforce UI navigation contract")

print("Validated explicit ComputerControlDesk UI context, keyboard-safe channel editing, three native CC work-area tabs and authoritative Information Source discovery.")
