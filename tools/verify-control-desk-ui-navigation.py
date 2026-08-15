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
state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
client_nav = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskUiClientNavigation.kt")
aero_buttons = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskNavigationButtons.kt")
cc_sidebar = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskComputerSidebar.kt")
channel_widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/WireChannelManagerWidget.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


# Both Aeroworks entry shapes keep the same native bottom-row PC action.
require("ControlDeskNavigationButtons.computerButton" in module, "ModuleScreen must use native bottom-row PC button")
require("ControlDeskNavigationButtons.computerButton" in overview, "ConsoleScreen overview must use native bottom-row PC button")
require("rememberClientControls(menu.contentHolder)" in module, "detail screen must retain exact ConsoleSocket")
require("rememberClientOverview(console)" in overview, "overview screen must retain overview return context")
require("HoverTintIconButton" in aero_buttons, "Aeroworks navigation must use native HoverTintIconButton")

# Aeroworks 1.3.0 anchors its content eight pixels inside the left edge.
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

# Catnip generic callback must keep explicit widget type.
require("withCallback<HoverTintIconButton>(callback)" in aero_buttons,
        "Aeroworks button callback must specify Catnip's generic widget return type explicitly")
require("button.withCallback(callback)" not in aero_buttons,
        "raw Catnip withCallback(callback) call reintroduces the Kotlin type-inference failure")

# Aeroworks side must never return to legacy text navigation.
require("buttonX = leftPos + (imageWidth - buttonWidth) / 2" not in module,
        "ModuleScreen must not restore the old top-centred switch")
require("Button.builder" not in module,
        "ModuleScreen must not use the legacy vanilla Computer text button")

# 0/1/many control return remains delegated to Aeroworks.
require("ClientReturnMode.OVERVIEW" in state and "ClientReturnMode.DETAIL" in state,
        "return state must distinguish overview and exact detail")
require("reopenExactClientControls" in state and "socket.reopenModuleMenu()" in state,
        "exact detail return must preserve ConsoleSocket/subPath")
require("ConsoleScreenOpener.open(console)" in client_nav,
        "fallback/overview return must use Aeroworks' native 0/1/many dispatcher")
require("one control" in client_nav.lower() or "0/1/many" in client_nav,
        "one-control/no-overview behavior must remain documented next to runtime path")

# CC side contains vertically stacked Controls and Channels work-area tabs.
require("ComputerSidebar.HEIGHT" in cc_sidebar, "custom tabs must attach below native CC sidebar")
require("GuiSprites.getComputerTextures" in cc_sidebar, "custom tabs must reuse CC family sidebar sprite")
require("DynamicImageButton" in cc_sidebar, "custom tabs must use CC's native sidebar button type")
require("extensionIndex" in cc_sidebar and "TAB_HEIGHT + TAB_GAP" in cc_sidebar,
        "custom sidebar must support multiple vertical extension segments")
require("controlsButton" in cc_sidebar and "channelsButton" in cc_sidebar,
        "ComputerControlDesk must expose Controls and Channels sidebar actions")
require("ControlDeskUiClientNavigation.reopenControls()" in computer,
        "Controls tab must route to saved controls context")
require("WireChannelManagerWidget" in computer and "ccaeroworks_setChannelMode" in computer,
        "Channels tab must switch the terminal work area into channel management")
require('@Accessor("terminal")' in computer_accessor,
        "Computer screen mixin must access the native terminal widget instead of guessing its geometry")
require("WIRE CHANNELS" in channel_widget,
        "channel work area must be a dedicated CC-style management list")

for path in (
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls_hover.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_channels_hover.png",
):
    require((ROOT / path).is_file(), f"missing sidebar icon sprite: {path}")

require('"client.ConsoleScreenAccessor"' in mixins, "ConsoleScreen accessor mixin must be registered")
require('"client.ConsoleScreenSwitchMixin"' in mixins, "ConsoleScreen switch mixin must be registered")
require('"client.AbstractComputerScreenAccessor"' in mixins, "CC screen accessor mixin must be registered")
require("Inspect Aeroworks navigation layouts" not in workflow and "Inspect Aeroworks control UI bytecode" not in workflow,
        "temporary bytecode probes must not remain in final workflow")
require("python3 tools/verify-control-desk-ui-navigation.py" in workflow,
        "workflow must enforce UI navigation contract")

print("Validated symmetric ControlDesk navigation plus stacked CC-style Controls/Channels work-area tabs without disturbing Aeroworks' native action geometry.")
