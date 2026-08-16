#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")

renderer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ConsoleMultiblockPreviewRenderer.kt")
mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenMultiblockPreviewMixin.kt")
accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenAccessor.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

require(
    'method = ["renderConsolePreview(Lnet/minecraft/client/gui/GuiGraphics;)V"]' in mixin,
    "ConsoleScreen preview replacement must target the exact native method descriptor",
)
require('at = [At("HEAD")]' in mixin and "cancellable = true" in mixin, "native preview hook must be cancellable at HEAD")
require("ConsoleMultiblockPreviewRenderer.render" in mixin and "callback.cancel()" in mixin, "native preview must only be cancelled after custom rendering succeeds")
require('@Accessor("windowTop")' in accessor and '@Accessor("windowLeft")' in accessor, "preview needs exact native window anchor")
require('"client.ConsoleScreenMultiblockPreviewMixin"' in mixins, "preview mixin is not registered")

require("ConsoleMultiblockManager.resolve(level, console.blockPos)" in renderer, "preview must reuse canonical multiblock resolution")
require("snapshot.members.size <= 1" in renderer, "single desks must retain Aeroworks' native preview")
require("ConsoleNetworkState.PARTIALLY_LOADED" in renderer and "ConsoleNetworkState.TOO_LARGE" in renderer, "incomplete/oversized networks need safe native fallback")
require('it.name == "facing"' in renderer and "canonicalFacing = Direction.NORTH" in renderer, "world facing must be normalized")
require('it.name == "ceiling"' in renderer and "state.setValue(ceiling, false)" in renderer, "ceiling placement must be normalized like Aeroworks")
require("right = facing.clockWise" in renderer and "World-forward maps to canonical NORTH" in renderer, "member positions must be transformed into desk-local coordinates")

for token in (
    "NATIVE_SCALE = 42.0F",
    "NATIVE_PITCH = 30.0F",
    "NATIVE_YAW = 225.0F",
    "MAX_YAW = 250.0F",
    "MIN_PITCH = 18.0F",
    "projectedSize(bounds, yaw, pitch)",
    "VIEWPORT_WIDTH / projected.first",
    "VIEWPORT_HEIGHT / projected.second",
):
    require(token in renderer, f"adaptive preview layout missing {token}")

require("snapshot.revision" in renderer and "cachedLayout" in renderer, "preview layout must be revision-cached")
require("graphics.enableScissor" in renderer and "graphics.disableScissor()" in renderer, "preview must be clipped to its native UI region")
require("CachedBuffers.block(state)" in renderer, "desk baked model rendering missing")
require("ModulePartRender.flatten" in renderer and "ModulePartRender.apply" in renderer, "mounted Aeroworks module rendering missing")
require("ModulePartRender.displayValues(mounted, restValues)" in renderer, "module preview must retain native REST-value semantics")
require("import net.createmod.catnip.render.SuperByteBuffer" in renderer, "Kotlin renderer must import SuperByteBuffer for explicit Flywheel self types")
require(renderer.count(".light<SuperByteBuffer>(FULL_BRIGHT)") == 2, "both Flywheel light transforms must specify SuperByteBuffer so Kotlin can infer Self")
require(".light(FULL_BRIGHT)" not in renderer, "raw Flywheel light calls regress Kotlin Self-type inference")
require(renderer.count("buffers.endBatch()") == 1, "multiblock preview must flush its shared buffer exactly once")
require("Lighting.setupFor3DItems()" in renderer and "Lighting.setupForFlatItems()" in renderer, "native GUI lighting transition must be preserved")

require("python3 tools/verify-console-multiblock-preview.py" in workflow, "workflow must enforce multiblock preview source contract")
require("python3 tools/verify-aeroworks-console-preview-bytecode.py" in workflow, "workflow must pin the Aeroworks preview bytecode contract")

print("Validated adaptive full-multiblock Aeroworks ConsoleScreen preview, Kotlin-safe Flywheel lighting, native single-desk fallback and rendering contract.")
