#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


module = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenSwitchMixin.kt")
overview = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenSwitchMixin.kt")
overview_accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenAccessor.kt")
computer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")
state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
client_nav = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskUiClientNavigation.kt")
io_client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DeskIoOverviewClient.kt")
io_screen = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DeskIoOverviewScreen.kt")
aero_buttons = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskNavigationButtons.kt")
cc_sidebar = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskComputerSidebar.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


# Both Aeroworks entry shapes must have the same native bottom-row PC action.
require("ControlDeskNavigationButtons.computerButton" in module, "ModuleScreen must use native bottom-row PC button")
require("ControlDeskNavigationButtons.computerButton" in overview, "ConsoleScreen overview must use native bottom-row PC button")
require("rememberClientControls(menu.contentHolder)" in module, "detail screen must retain exact ConsoleSocket")
require("rememberClientOverview(console)" in overview, "overview screen must retain overview return context")
require("HoverTintIconButton" in aero_buttons, "Aeroworks navigation must use native HoverTintIconButton")

# Aeroworks 1.3.0 anchors its content eight pixels inside the left edge of both relevant screens.
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
require("leftmost" not in aero_buttons and "shift" not in aero_buttons,
        "PC button X must not be inferred from or shift the right-aligned native action row")

# Catnip's Java withCallback() returns a generic T whose type is not inferable in Kotlin.
require("withCallback<HoverTintIconButton>(callback)" in aero_buttons,
        "Aeroworks button callback must specify Catnip's generic widget return type explicitly")
require("button.withCallback(callback)" not in aero_buttons,
        "raw Catnip withCallback(callback) call reintroduces the Kotlin type-inference failure")

# The old top-centred text buttons are explicitly forbidden.
for name, text in (("ModuleScreen", module), ("computer", computer)):
    require("buttonX = leftPos + (imageWidth - buttonWidth) / 2" not in text,
            f"{name} must not restore the old top-centred switch")
require("Button.builder" not in module, "ModuleScreen must not use the legacy vanilla Computer text button")
require("Button.builder" not in computer, "computer screen must not use legacy vanilla navigation buttons")

# 0/1/many is delegated back to Aeroworks.
require("ClientReturnMode.OVERVIEW" in state and "ClientReturnMode.DETAIL" in state,
        "return state must distinguish overview and exact detail")
require("reopenExactClientControls" in state and "socket.reopenModuleMenu()" in state,
        "exact detail return must preserve ConsoleSocket/subPath")
require("ConsoleScreenOpener.open(console)" in client_nav,
        "fallback/overview return must use Aeroworks' native 0/1/many dispatcher")
require("one control" in client_nav.lower() or "0/1/many" in client_nav,
        "one-control/no-overview behavior must remain documented next to runtime path")

# CC side must look/behave like native vertical sidebar segments.
require("ComputerSidebar.HEIGHT" in cc_sidebar, "custom tabs must attach below native CC sidebar")
require("GuiSprites.getComputerTextures" in cc_sidebar, "custom tabs must reuse CC family sidebar sprite")
require("DynamicImageButton" in cc_sidebar, "custom tabs must use CC's native sidebar button type")
require("index * (TAB_HEIGHT + TAB_GAP)" in cc_sidebar,
        "custom ComputerControlDesk tabs must stack vertically rather than overlap")
for button in ("controlsButton", "channelsButton", "sourcesButton"):
    require(f"fun {button}" in cc_sidebar, f"ComputerControlDesk sidebar is missing {button}")
require("ControlDeskComputerSidebar" in computer, "computer mixin must add the custom sidebar")
require("ControlDeskUiClientNavigation.reopenControls()" in computer, "Controls tab must route to saved controls context")
require("ControlDeskComputerSidebar.channelsButton" in computer,
        "computer screen must expose the Channels sidebar entry")
require("ControlDeskComputerSidebar.sourcesButton" in computer,
        "computer screen must expose the Information Sources sidebar entry")
require("DeskIoOverviewScreen.CATEGORY_CONTROL" in computer and "DeskIoOverviewScreen.CATEGORY_INFORMATION" in computer,
        "sidebar entries must request the intended I/O category")
require("RequestDeskIoOverviewPayload(console.blockPos)" in computer,
        "Channels/Sources tabs must request a fresh server-authoritative I/O snapshot")
require("preferredCategory" in io_client and "preferCategory" in io_client,
        "I/O client must retain the sidebar-requested initial category until the S2C snapshot arrives")
require("DeskIoOverviewClient.preferCategory(category)" in io_screen,
        "I/O refresh must preserve the currently selected category")

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
require("Inspect Aeroworks navigation layouts" not in workflow and "Inspect Aeroworks control UI bytecode" not in workflow,
        "temporary bytecode probes must not remain in final workflow")
require("python3 tools/verify-control-desk-ui-navigation.py" in workflow,
        "workflow must enforce UI navigation contract")

print("Validated ControlDesk UI navigation: Aeroworks return is preserved and the ComputerControlDesk exposes stacked CC-style Controls, Channels and Information Sources sidebar entries with category-preserving refresh.")
