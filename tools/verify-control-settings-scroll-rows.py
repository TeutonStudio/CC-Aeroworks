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
overlay_owner = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/SourceSelectorOverlayOwner.kt")
tooltip_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AbstractContainerScreenSourceSelectorTooltipMixin.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
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
require("fun intersectsViewport" in geometry and "rowTop < listBottom" in geometry,
        "source rows must become visible on viewport intersection rather than waiting for full visibility")
require("fullyVisible" not in geometry,
        "source-row geometry must not regress to all-or-nothing full-row visibility")

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

# One owner computes the single source-selector extension row and restores native height on Screen rebuilds.
require("ccaeroworks_extensionRows" in bindings,
        "display binding mixin must own a single extension-row count")
require("ccaeroworks_prepareDisplayBindingRows" in bindings and
        '@Inject(method = ["init()V"], at = [At("HEAD")])' in bindings,
        "display binding mixin must prepare repeated Screen.init calls before Aeroworks rebuilds")
require("ccaeroworks_nativeContentHeight >= 0" in bindings and
        "ccaeroworks_setContentHeight(ccaeroworks_nativeContentHeight)" in bindings,
        "repeated init must restore the unextended native content height before Aeroworks runs")
require("ccaeroworks_radarDropdown = null" in bindings and "ccaeroworks_scriptDropdown = null" in bindings,
        "repeated init must discard references to widgets cleared by Screen rebuildWidgets")
require(bindings.count("ccaeroworks_setContentHeight(") == 2,
        "display binding mixin must have exactly one native-height restore and one final extension write")
require("ccaeroworks_nativeContentHeight = screen.ccaeroworks_getContentHeight()" in bindings,
        "native content height must be recaptured only after the restored Aeroworks init completes")
require("contentHeightWithExtensions" in bindings and "ccaeroworks_extensionRows" in bindings,
        "extension owner must derive final content height from the restored native height")
require("extensionScreenTop" in bindings and "ccaeroworks_getRenderedScroll" in bindings,
        "source selector must follow native animated scroll")
require('method = ["renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"]' in bindings,
        "source selector position must synchronize after native renderBg scroll interpolation")
require("ModuleScreenRowGeometry.intersectsViewport" in bindings,
        "source selector must remain active while any part intersects the native list viewport")
require("listBottom = listTop + ModuleScreenRowGeometry.LIST_HEIGHT" in bindings,
        "source selector must receive the exact native list clipping bounds")
require("setRowPosition(rowLeft, rowTop, visible, listTop, listBottom)" in bindings,
        "source selector placement must include viewport bounds for clipping and hit testing")
require("AeroworksGuiTextures.MODULE_ROW" not in bindings,
        "source selector must not reuse the native control row with Redstone/radio slots")
require(bindings.count("ccaeroworks_extensionRows = 1") == 2,
        "radar and script sources must each consume exactly one extension row")
require("topPos + imageHeight" not in bindings and "leftPos + (imageWidth" not in bindings,
        "configuration rows must not return to inventory-overlapping absolute placement")

# A resize must not throw away an already delivered script catalog.
require("ccaeroworks_scriptCatalogRequested" in bindings and
        "if (!ccaeroworks_scriptCatalogRequested)" in bindings,
        "script catalog requests must be per Screen instance rather than repeated on resize")
require(bindings.count("DisplayScriptCatalogState.clear(desk.blockPos, socket)") == 1,
        "script catalog must only be cleared inside the one-time request guard")

# Radar and script are presentations over one selector implementation.
require("SourceSelectorWidget<RadarSourceChoice>" in bindings and "SourceSelectorWidget<String>" in bindings,
        "radar and script bindings must share SourceSelectorWidget")
require("{ _ -> choices.map(::radarSourceOption) }" in bindings,
        "radar choices must feed the shared dropdown instead of creating one row per radar")
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
require("fun renderOverlay" in selector and "ccaeroworks_renderBindingPopup" in bindings,
        "open selector popups must render after ModuleScreen's normal widget/decorations pass")
require('method = ["render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"]' in bindings,
        "popup overlay must be attached to the complete ModuleScreen render pass")
require("graphics.enableScissor(x, viewportTop, x + width, viewportBottom)" in selector and
        "graphics.disableScissor()" in selector,
        "partially visible source rows must be clipped to the native list instead of hidden wholesale")
require("insideVisibleRow" in selector and "mouseY >= viewportTop" in selector and "mouseY < viewportBottom" in selector,
        "hidden portions of clipped source rows must not remain clickable or hoverable")
require("fun isPopupMouseOver" in selector,
        "selector must expose exact popup hit testing for tooltip suppression")
require("preferredY.coerceIn(SCREEN_MARGIN, maxY)" in selector and
        "x.coerceIn(SCREEN_MARGIN, maxX)" in selector,
        "popup bounds must stay inside the resized screen")
require("EditBox" not in bindings,
        "script source must not restore the old free-form EditBox")

# Container item tooltips behind an open popup must not leak through the selector overlay.
require("interface SourceSelectorOverlayOwner" in overlay_owner and
        "ccaeroworks_isSourceSelectorPopupHovered" in overlay_owner,
        "source selector screens must expose popup hover state through a narrow client interface")
require("SourceSelectorOverlayOwner" in bindings and
        "override fun ccaeroworks_isSourceSelectorPopupHovered" in bindings,
        "ModuleScreen source binding mixin must implement popup hover ownership")
require('@Mixin(AbstractContainerScreen::class)' in tooltip_mixin,
        "item tooltip suppression must target vanilla AbstractContainerScreen")
require('method = ["renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"]' in tooltip_mixin and
        "callback.cancel()" in tooltip_mixin,
        "container item tooltip rendering must be cancellable beneath an open source popup")
require("ccaeroworks_isSourceSelectorPopupHovered" in tooltip_mixin,
        "tooltip cancellation must only occur when the mouse is actually inside the source popup")
require('"client.AbstractContainerScreenSourceSelectorTooltipMixin"' in mixins,
        "source-selector container tooltip mixin must be registered on the client")

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
    "Validated ControlDesk settings rows: resize-safe native height restoration, partially clipped source rows, "
    "overlay-rendered dropdowns without underlying item tooltips, sprite chevrons, and registry-backed radar source icons."
)
