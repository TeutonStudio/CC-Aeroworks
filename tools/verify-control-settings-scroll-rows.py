#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


geometry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenRowGeometry.kt")
accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenAccessor.kt")
invoker = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenInvoker.kt")
combined = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
bindings = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
workflow = read(".github/workflows/verify.yml")

# Aeroworks 1.3.0 bytecode contract used by all CC-Aeroworks row extensions.
require("LIST_WIDTH: Int = 251" in geometry, "ModuleScreen list width must match Aeroworks 1.3.0")
require("LIST_HEIGHT: Int = 108" in geometry, "ModuleScreen list height must match Aeroworks 1.3.0")
require("SINGLE_HEIGHT: Int = 30" in geometry and "PAIR_HEIGHT: Int = 52" in geometry,
        "native row heights must match Aeroworks 1.3.0")
require("ROW_GAP: Int = 1" in geometry, "native row gap must match Aeroworks 1.3.0")
require("fun nativeGroups" in geometry and "column.isButton()" in geometry and "!column.positive()" in geometry,
        "row geometry must mirror Aeroworks native group pairing")
require("renderedScroll" in geometry and "extensionScreenTop" in geometry,
        "all extension Y coordinates must derive from the animated native scroll")

# The mixins may read the native list state, but must not invent a parallel scroll state.
require('@Accessor("contentHeight")' in accessor and '@Accessor("renderedScroll")' in accessor,
        "ModuleScreen content height and animated scroll must be exposed")
for method in ("listLeft", "listTop", "rowLeft"):
    require(f'@Invoker("{method}")' in invoker, f"ModuleScreen {method} geometry must use a native invoker")

# Combined decoration is attached to native row geometry, not one-time screen-space pixel discovery.
require("discoverModeToggleBounds" not in combined and "ccaeroworks_modeToggleBounds" not in combined,
        "Combined icon must not cache absolute mode-toggle bounds")
require("ModuleScreenRowGeometry.nativeGroups" in combined and "ModuleScreenRowGeometry.modeToggleRect" in combined,
        "Combined icon must resolve its native row on every render")
require("ccaeroworks_getRenderedScroll" in combined,
        "Combined icon must follow Aeroworks smooth renderedScroll")
require("graphics.enableScissor" in combined and "ModuleScreenRowGeometry.LIST_HEIGHT" in combined,
        "Combined decoration must be clipped to the native list viewport")

# Radar/script configuration is appended to the native scroll content instead of covering inventory UI.
require("contentHeightWithExtensions" in bindings and "ccaeroworks_setContentHeight" in bindings,
        "display bindings must extend the native ModuleScreen content height")
require("extensionScreenTop" in bindings and "ccaeroworks_getRenderedScroll" in bindings,
        "display binding row must follow the native animated scroll")
require('method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"]' in bindings,
        "binding widget positions must be synchronized from the native renderBg scroll update")
require("graphics.enableScissor" in bindings and "fullyVisible" in bindings,
        "binding rows/widgets must stay inside the native list viewport")
require("AeroworksGuiTextures.MODULE_ROW.render" in bindings,
        "extension configuration rows must reuse Aeroworks' native MODULE_ROW styling")
require("topPos + imageHeight" not in bindings and "leftPos + (imageWidth" not in bindings,
        "display binding controls must not return to absolute inventory-overlapping placement")

# Kotlin companion objects generate a static Companion field on the mixin class. Sponge Mixin rejects
# non-private static fields during preprocessing, so mixin layout constants must remain file-level/private.
require("\n    companion object" not in bindings,
        "ModuleScreenDisplayBindingMixin must not declare a Kotlin companion object")
require("private const val BINDING_ROW_WIDTH" in bindings,
        "display binding layout constants must remain private file-level constants")

require("python3 tools/verify-control-settings-scroll-rows.py" in workflow,
        "repository workflow must enforce the scroll-row architecture")

print(
    "Validated ControlDesk settings rows: native Aeroworks geometry and row styling, renderedScroll anchoring, "
    "scissored Combined decoration, mixin-safe Kotlin constants, and radar/script configuration inside the shared scroll content."
)
