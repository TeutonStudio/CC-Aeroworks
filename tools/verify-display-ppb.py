#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"display PPB verification failed: {message}")


types = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayType.kt")
config = read("src/main/kotlin/de/teutonstudio/ccaeroworks/config/CCServerConfig.kt")
pixels = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayPixels.kt")
renderer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayRenderer.kt")
visual = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleVisualMixin.kt")
overlay = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskPixelOverlayRenderer.kt")
touch = read("src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua")
handler = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua")

require('TWO_DIGIT(2, "two_digit_display", 7, 7)' in types, "small surface must be 7/16 by 7/16")
require('THREE_DIGIT(3, "three_digit_display", 10, 7)' in types, "large surface must be 10/16 by 7/16")
require("const val VANILLA_PARTS_PER_BLOCK: Int = 16" in types, "vanilla PPB reference must remain 16")
require("const val DEFAULT_PARTS_PER_BLOCK: Int = 256" in types, "default PPB must remain 256")
require("surfaceParts.toLong() * partsPerBlock.toLong() / VANILLA_PARTS_PER_BLOCK" in types,
        "resolution must derive from physical surface parts and one PPB value")

require('defineInRange(\n                "ppb"' in config or 'defineInRange("ppb"' in config,
        "server config must expose display.ppb")
require("smallDisplayPixelWidth" not in config and "largeDisplayPixelWidth" not in config,
        "independent width/height config fields must not return")

require("@cca_pixels_2:" in pixels, "pixel persistence must use version 2")
require("Base64.getUrlEncoder" in pixels, "pixel persistence must remain bit-packed before text encoding")
require("fun isEncoded" in pixels, "migration must distinguish encoded rasters from display text")

require("type.pixelPitchBlocks" in renderer, "both pixel axes must use the PPB pitch")
require("type.pixelModelScale" in renderer, "pixel model must scale with PPB")
# The renderer now shares one renderPixel helper between persisted rasters and reactive frames.
# Accept either the original direct-raster call or the generalized width/height helper, but keep
# enforcing the important invariant: raster placement is applied before local model scaling.
pixel_position_candidates = [
    renderer.find("pixelOffsetX(display.type, pixels.width, x)"),
    renderer.find("pixelOffsetX(display.type, width, x)"),
]
pixel_positions = [position for position in pixel_position_candidates if position >= 0]
pixel_position = min(pixel_positions) if pixel_positions else -1
pixel_scale = renderer.find(".scale(scale, 1.0f, scale)")
require(pixel_position >= 0 and pixel_scale >= 0 and pixel_position < pixel_scale,
        "pixel model scaling must happen after raster placement so PPB scale cannot collapse offsets")
require(".translate(0.5, 0.0, 0.5)\n                    .scale(scale, 1.0f, scale)\n                    .translate(-0.5, 0.0, -0.5)" in renderer,
        "pixel model must scale locally around its X/Z centre")
require("ReactiveDisplayFrames.snapshot" in renderer,
        "reactive display frames must render through the same PPB-aware pixel pass")

require("DeskPixelOverlayRenderer.track(blockEntity)" in visual,
        "Flywheel visuals must delegate programmable pixel rasters to the shared pass")
require("DeskDisplayModels.PIXEL" not in visual,
        "Flywheel visual must not allocate one persistent instance per programmable pixel")
require("DeskDisplayRenderer.renderPixels" in overlay, "shared pixel overlay must render the raster batch")
require("LevelRenderer.getLightColor(level, desk.blockPos)" in overlay,
        "shared pixel overlay must use world lighting")
require("LightTexture.FULL_BRIGHT" not in overlay,
        "programmable display pixels must not be forced to hologram-like full brightness")

require("function touchdisplay.normalizedPosition(event)" in touch,
        "touchdisplay must expose resolution-independent pointer coordinates")
require("u = event[13]" in handler and "v = event[14]" in handler,
        "automatic handlers must receive normalized pointer coordinates")

# Keep the arithmetic executable here as a cheap independent contract check.
def pixels_for(parts: int, ppb: int) -> int:
    return parts * ppb // 16


def raster_footprint(pixel_count: int, ppb: int) -> float:
    # display_pixel.json is 0.56 vanilla model units wide. After the vanilla-16-PPB
    # partial is scaled by 16/ppb, the final pixel geometry is 0.56/ppb blocks wide.
    return (pixel_count - 1 + 0.56) / ppb


require((pixels_for(7, 16), pixels_for(7, 16)) == (7, 7), "16 PPB small resolution changed")
require((pixels_for(10, 16), pixels_for(7, 16)) == (10, 7), "16 PPB large resolution changed")
require((pixels_for(7, 256), pixels_for(7, 256)) == (112, 112), "256 PPB small resolution changed")
require((pixels_for(10, 256), pixels_for(7, 256)) == (160, 112), "256 PPB large resolution changed")
require(raster_footprint(160, 256) <= 10 / 16, "large 256-PPB raster must remain inside display width")
require(raster_footprint(112, 256) <= 7 / 16, "large 256-PPB raster must remain inside display height")

print("display PPB contract OK: persisted/reactive transform order, lighting and 16/256 PPB geometry verified")
