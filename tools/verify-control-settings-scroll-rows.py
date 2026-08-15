#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


geometry_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/client/ModuleScreenRowGeometry.kt"
old_geometry_path = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenRowGeometry.kt"
geometry = read(geometry_path)
accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenAccessor.kt")
invoker = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenInvoker.kt")
combined = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
bindings = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
widgets = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DisplayBindingRowWidgets.kt")
workflow = read(".github/workflows/verify.yml")

# Ordinary helper classes must stay outside the configured Mixin package tree.
require(not old_geometry_path.exists(),
        "ModuleScreenRowGeometry must not live under the reserved mixin package tree")
require("package de.teutonstudio.ccaeroworks.client" in geometry,
        "ModuleScreenRowGeometry must live in a normal client package")
require("import de.teutonstudio.ccaeroworks.client.ModuleScreenRowGeometry" in combined,
        "Combined-input mixin must import the non-mixin geometry helper")
require("import de.teutonstudio.ccaeroworks.client.ModuleScreenRowGeometry" in bindings,
        "display-binding mixin must import the non-mixin geometry helper")

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

# The mixins may read native list state, but must not invent a parallel scroll state.
require('@Accessor("contentHeight")' in accessor and '@Accessor("renderedScroll")' in accessor,
        "ModuleScreen content height and animated scroll must be exposed")
for method in ("listLeft", "listTop", "rowLeft"):
    require(f'@Invoker("{method}")' in invoker, f"ModuleScreen {method} geometry must use a native invoker")

# Combined decoration is attached to native row geometry, not one-time screen-space discovery.
require("discoverModeToggleBounds" not in combined and "ccaeroworks_modeToggleBounds" not in combined,
        "Combined icon must not cache absolute mode-toggle bounds")
require("ModuleScreenRowGeometry.nativeGroups" in combined and "ModuleScreenRowGeometry.modeToggleRect" in combined,
        "Combined icon must resolve its native row on every render")
require("ccaeroworks_getRenderedScroll" in combined,
        "Combined icon must follow Aeroworks smooth renderedScroll")
require("graphics.enableScissor" in combined and "ModuleScreenRowGeometry.LIST_HEIGHT" in combined,
        "Combined decoration must be clipped to the native list viewport")

# One owner computes all extension rows and contentHeight exactly once after row discovery.
require("ccaeroworks_extensionRows" in bindings,
        "display binding mixin must own a single extension-row count")
require(bindings.count("ccaeroworks_setContentHeight(") == 1,
        "only one ModuleScreen extension owner may mutate contentHeight")
require("contentHeightWithExtensions" in bindings and "ccaeroworks_extensionRows" in bindings,
        "extension owner must derive native content height from final row count")
require("extensionScreenTop" in bindings and "ccaeroworks_getRenderedScroll" in bindings,
        "every extension row must follow native animated scroll")
require('method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"]' in bindings,
        "extension widget positions must synchronize after native renderBg scroll interpolation")
require("graphics.enableScissor" in bindings and "fullyVisible" in bindings,
        "extension rows/widgets must stay inside the native list viewport")
require("AeroworksGuiTextures.MODULE_ROW.render" in bindings,
        "extension rows must reuse Aeroworks' native MODULE_ROW styling")
require("topPos + imageHeight" not in bindings and "leftPos + (imageWidth" not in bindings,
        "configuration rows must not return to inventory-overlapping absolute placement")

# Radar rows and script dropdown both participate in the same extension layout.
require("RadarSourceRowButton" in bindings and "RadarSourceChoice" in bindings,
        "radar choices must be normal extension rows")
require("ScriptSourceDropdownWidget" in bindings,
        "script source must be an extension-row dropdown")
require("mouseScrolled" in widgets and "MAX_VISIBLE_OPTIONS" in widgets,
        "script dropdown must have bounded independent scrolling")
require("EditBox" not in bindings,
        "script source must not restore the old free-form EditBox")

# Kotlin companion objects generate a static Companion field on the mixin class.
require("\n    companion object" not in bindings,
        "ModuleScreenDisplayBindingMixin must not declare a Kotlin companion object")
require("private const val BINDING_ROW_WIDTH" in bindings,
        "display binding layout constants must remain private file-level constants")

require("python3 tools/verify-control-settings-scroll-rows.py" in workflow,
        "repository workflow must enforce the scroll-row architecture")

print(
    "Validated ControlDesk settings rows: native Aeroworks geometry, one extension owner, renderedScroll anchoring, "
    "scissored Combined decoration, row-based radar choices and independently scrolling script dropdown."
)
