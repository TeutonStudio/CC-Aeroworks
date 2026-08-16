#!/usr/bin/env python3
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def png_size(path: str) -> tuple[int, int]:
    data = (ROOT / path).read_bytes()
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"{path} must be a PNG")
    return struct.unpack(">II", data[16:24])


geometry_path = "src/main/kotlin/de/teutonstudio/ccaeroworks/client/ModuleScreenRowGeometry.kt"
old_geometry_path = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenRowGeometry.kt"
geometry = read(geometry_path)
accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenAccessor.kt")
invoker = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenInvoker.kt")
combined = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
bindings = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayBindingMixin.kt")
presentations = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/DisplayBindingRowWidgets.kt")
selector = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/SourceSelectorWidget.kt")
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

# One owner computes all source-selector extension rows and contentHeight exactly once.
require("ccaeroworks_extensionRows" in bindings,
        "display binding mixin must own a single extension-row count")
require(bindings.count("ccaeroworks_setContentHeight(") == 1,
        "only one ModuleScreen extension owner may mutate contentHeight")
require("contentHeightWithExtensions" in bindings and "ccaeroworks_extensionRows" in bindings,
        "extension owner must derive native content height from final row count")
require("extensionScreenTop" in bindings and "ccaeroworks_getRenderedScroll" in bindings,
        "source selectors must follow native animated scroll")
require('method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"]' in bindings,
        "source selector position must synchronize after native renderBg scroll interpolation")
require("fullyVisible" in bindings,
        "source selectors must deactivate when their rows leave the native list viewport")
require("AeroworksGuiTextures.MODULE_ROW" not in bindings,
        "source selectors must not reuse the native control row with Redstone/radio slots")
require(bindings.count("ccaeroworks_extensionRows = 1") == 1 and
        bindings.count("ccaeroworks_extensionRows = 2") == 1,
        "radar must consume one selector row while reactive application plus legacy touch consume two")
require("topPos + imageHeight" not in bindings and "leftPos + (imageWidth" not in bindings,
        "configuration rows must not return to inventory-overlapping absolute placement")

# Radar, reactive application and legacy touch are presentations over one selector implementation.
require("SourceSelectorWidget<RadarSourceChoice>" in bindings and "SourceSelectorWidget<String>" in bindings,
        "radar and script bindings must share SourceSelectorWidget")
require("{ _ -> choices.map(::radarSourceOption) }" in bindings,
        "radar choices must feed the shared dropdown instead of creating one row per radar")
require("ScriptSourceRole.REACTIVE_APP" in bindings and "ScriptSourceRole.LEGACY_TOUCH" in bindings and
        "scriptSourceOptions" in bindings,
        "script display must retain separate reactive application and legacy touch selectors")
require("mouseScrolled" in selector and "MAX_VISIBLE_OPTIONS = 5" in selector,
        "shared dropdown must have bounded independent scrolling")
require("POPUP_ROW_HEIGHT = 30" in selector,
        "popup rows must preserve the same 30px icon/text geometry as the closed selector")
require("CHEVRON_DOWN_SPRITE" in selector and "CHEVRON_UP_SPRITE" in selector,
        "dropdown state must use GUI sprites rather than font glyphs")
require('if (expanded) "^" else "v"' not in selector,
        "dropdown arrow must never regress to text glyphs")
require("SourceSelectorIcon.Item" in selector and "SourceSelectorIcon.Sprite" in selector,
        "shared selector must support both Minecraft item icons and custom GUI sprites")
require("EditBox" not in bindings,
        "script source must not restore the old free-form EditBox")

# Radar source icon is the Create: Radars Network Filterer / network controller, never radarPos.
require('ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")' in presentations,
        "radar source icon must resolve the Create: Radars network_filterer registry block")
require("BuiltInRegistries.BLOCK.getOptional(NETWORK_CONTROLLER_ID)" in presentations,
        "radar source icon must use registry lookup rather than a world/chunk lookup")
require("descriptor.radarPos" not in presentations,
        "radar source presentation must not derive its icon or title from the radar bearing/radar block")
require('CCAeroworks.id("source_selector/script")' in presentations,
        "script source must use the dedicated Minecraft-style script sprite")

# Custom selector art must be slot-free and dimensionally compatible with the row geometry.
asset_root = "src/main/resources/assets/cc_aeroworks/textures/gui/sprites/source_selector"
require(png_size(f"{asset_root}/row.png") == (235, 30), "source row sprite must be 235x30")
require(png_size(f"{asset_root}/row_hover.png") == (235, 30), "source hover sprite must be 235x30")
require(png_size(f"{asset_root}/dropdown_down.png") == (7, 4), "down chevron must be 7x4")
require(png_size(f"{asset_root}/dropdown_up.png") == (7, 4), "up chevron must be 7x4")
require(png_size(f"{asset_root}/script.png") == (16, 16), "script icon must be a Minecraft-sized 16x16 sprite")

# Kotlin companion objects generate a static Companion field on the mixin class.
require("\n    companion object" not in bindings,
        "ModuleScreenDisplayBindingMixin must not declare a Kotlin companion object")
require("private const val BINDING_ROW_WIDTH" in bindings,
        "display binding layout constants must remain private file-level constants")

require("python3 tools/verify-control-settings-scroll-rows.py" in workflow,
        "repository workflow must enforce the source-selector architecture")

print(
    "Validated ControlDesk settings rows: native Aeroworks geometry, one radar selector plus separate reactive/legacy script selectors, "
    "sprite chevrons, a 16x16 script icon, registry-backed radar Network Filterer icon, and independent popup scrolling."
)
