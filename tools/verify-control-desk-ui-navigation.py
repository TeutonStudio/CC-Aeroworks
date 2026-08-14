#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


module = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenSwitchMixin.kt")
overview = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenSwitchMixin.kt")
computer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractComputerScreenSwitchMixin.kt")
state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ControlDeskUiSwitchState.kt")
client_nav = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskUiClientNavigation.kt")
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
require("maxByOrNull { it.x }" in aero_buttons and "leftmost - GAP - size" in aero_buttons,
        "PC button must anchor left of Aeroworks' existing bottom action row")

# The old top-centred text buttons are explicitly forbidden.
for name, text in (("ModuleScreen", module), ("computer", computer)):
    require("buttonX = leftPos + (imageWidth - buttonWidth) / 2" not in text,
            f"{name} must not restore the old top-centred switch")
require("Button.builder" not in module, "ModuleScreen must not use the legacy vanilla Computer text button")
require("Button.builder" not in computer, "computer screen must not use the legacy vanilla Controls text button")

# 0/1/many is delegated back to Aeroworks. This is the critical one-control/no-list contract.
require("ClientReturnMode.OVERVIEW" in state and "ClientReturnMode.DETAIL" in state,
        "return state must distinguish overview and exact detail")
require("reopenExactClientControls" in state and "socket.reopenModuleMenu()" in state,
        "exact detail return must preserve ConsoleSocket/subPath")
require("ConsoleScreenOpener.open(console)" in client_nav,
        "fallback/overview return must use Aeroworks' native 0/1/many dispatcher")
require("one control" in client_nav.lower() or "0/1/many" in client_nav,
        "one-control/no-overview behavior must remain documented next to runtime path")

# CC side must look/behave like the native sidebar, not like a free-floating button.
require("ComputerSidebar.HEIGHT" in cc_sidebar, "Controls tab must attach below native CC sidebar")
require("GuiSprites.getComputerTextures" in cc_sidebar, "Controls tab must reuse CC family sidebar sprite")
require("DynamicImageButton" in cc_sidebar, "Controls tab must use CC's native sidebar button type")
require("ControlDeskComputerSidebar" in computer, "computer mixin must add the sidebar tab")
require("ControlDeskUiClientNavigation.reopenControls()" in computer, "Controls tab must route to saved controls context")

for path in (
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls.png",
    "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/buttons/control_desk_controls_hover.png",
):
    require((ROOT / path).is_file(), f"missing sidebar icon sprite: {path}")

require('"client.ConsoleScreenAccessor"' in mixins, "ConsoleScreen accessor mixin must be registered")
require('"client.ConsoleScreenSwitchMixin"' in mixins, "ConsoleScreen switch mixin must be registered")
require('"client.AbstractComputerScreenAccessor"' in mixins, "CC screen accessor mixin must be registered")
require("Inspect Aeroworks navigation layouts" not in workflow and "Inspect Aeroworks control UI bytecode" not in workflow,
        "temporary bytecode probes must not remain in final workflow")
require("python3 tools/verify-control-desk-ui-navigation.py" in workflow,
        "workflow must enforce UI navigation contract")

print("Validated symmetric ControlDesk UI navigation: native Aeroworks bottom-row PC buttons, conditional overview, exact detail return, and CC-style Controls sidebar tab.")
