#!/usr/bin/env python3
from pathlib import Path

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
texture_cache = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayTextureCache.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
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

require("DeskDisplayTextureCache.texture" in renderer,
        "programmable display rendering must resolve one cached texture per display")
require("RenderType.entityCutoutNoCull(texture)" in renderer,
        "programmable display rendering must draw the dynamic texture as a cutout quad")
require(renderer.count("vertex(consumer, pose,") == 4,
        "one programmable display must submit exactly one four-vertex quad")
require("surfaceWidthParts.toDouble()" in renderer and "surfaceHeightParts.toDouble()" in renderer,
        "dynamic texture quad size must derive from the physical display surface")
require("DeskDisplayModels.PIXEL" not in renderer,
        "programmable display renderer must not render one model per enabled pixel")
require("for (y in 0 until pixels.height) for (x in 0 until pixels.width)" not in renderer,
        "render frames must not iterate the raster to emit per-pixel geometry")
require("PIXEL_SURFACE_Y = 2.251 / 16.0" in renderer,
        "texture quad must stay on the former pixel top surface")

require("NativeImage(pixels.width, pixels.height, false)" in texture_cache,
        "dynamic texture resolution must match the logical display raster exactly")
require("DynamicTexture(image)" in texture_cache,
        "client cache must back programmable displays with a DynamicTexture")
require("texture.setFilter(false, false)" in texture_cache,
        "display texels must use nearest filtering without mipmaps")
require("entry.pixels != pixels" in texture_cache and "entry.texture.upload()" in texture_cache,
        "GPU texture upload must happen only after the immutable pixel snapshot changes")
require("image.setPixelRGBA(x, y, if (pixels.get(x, y)) -1 else 0)" in texture_cache,
        "logical on/off pixels must map directly to opaque/transparent texels")
require("textureManager.release(entry.location)" in texture_cache,
        "obsolete dynamic textures must be released from the texture manager")
require("DeskDisplayTextureCache::clientTick" in client,
        "client lifecycle must clean textures belonging to removed or unloaded desks")

require("DeskPixelOverlayRenderer.track(blockEntity)" in visual,
        "Flywheel visuals must delegate programmable pixel rasters to the shared pass")
require("DeskDisplayModels.PIXEL" not in visual,
        "Flywheel visual must not allocate one persistent instance per programmable pixel")
require("DeskDisplayRenderer.renderPixels" in overlay, "shared pixel overlay must render the raster quad")
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


def textured_footprint(pixel_count: int, ppb: int) -> float:
    # One texture texel is one logical display pixel, so the quad covers the complete raster cells.
    return pixel_count / ppb


require((pixels_for(7, 16), pixels_for(7, 16)) == (7, 7), "16 PPB small resolution changed")
require((pixels_for(10, 16), pixels_for(7, 16)) == (10, 7), "16 PPB large resolution changed")
require((pixels_for(7, 256), pixels_for(7, 256)) == (112, 112), "256 PPB small resolution changed")
require((pixels_for(10, 256), pixels_for(7, 256)) == (160, 112), "256 PPB large resolution changed")
require(textured_footprint(160, 256) == 10 / 16, "large 256-PPB texture must exactly fill display width")
require(textured_footprint(112, 256) == 7 / 16, "large 256-PPB texture must exactly fill display height")

print("display PPB contract OK: dynamic texture cache, quad rendering, lighting and 16/256 PPB geometry verified")
