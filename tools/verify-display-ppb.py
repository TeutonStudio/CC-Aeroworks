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
require("DeskPixelOverlayRenderer.track(blockEntity)" in visual,
        "Flywheel visuals must delegate programmable pixel rasters to the shared pass")
require("DeskDisplayModels.PIXEL" not in visual,
        "Flywheel visual must not allocate one persistent instance per programmable pixel")
require("DeskDisplayRenderer.renderPixels" in overlay, "shared pixel overlay must render the raster batch")

require("function touchdisplay.normalizedPosition(event)" in touch,
        "touchdisplay must expose resolution-independent pointer coordinates")
require("u = event[13]" in handler and "v = event[14]" in handler,
        "automatic handlers must receive normalized pointer coordinates")

# Keep the arithmetic executable here as a cheap independent contract check.
def pixels_for(parts: int, ppb: int) -> int:
    return parts * ppb // 16

require((pixels_for(7, 16), pixels_for(7, 16)) == (7, 7), "16 PPB small resolution changed")
require((pixels_for(10, 16), pixels_for(7, 16)) == (10, 7), "16 PPB large resolution changed")
require((pixels_for(7, 256), pixels_for(7, 256)) == (112, 112), "256 PPB small resolution changed")
require((pixels_for(10, 256), pixels_for(7, 256)) == (160, 112), "256 PPB large resolution changed")

print("display PPB contract OK: 16 -> 7x7/10x7, 256 -> 112x112/160x112")
