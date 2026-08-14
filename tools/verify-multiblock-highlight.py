#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


renderer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ConsoleMultiblockHighlightRenderer.kt")
geometry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ConsoleMultiblockHighlightGeometry.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
workflow = read(".github/workflows/verify.yml")

# The client must use NeoForge's cancellable highlight hook instead of patching LevelRenderer.
require("RenderHighlightEvent.Block" in renderer,
        "multiblock outline must be driven by RenderHighlightEvent.Block")
require("ConsoleMultiblockHighlightRenderer::render" in client,
        "multiblock outline listener must be registered on the client event bus")
require("LevelRendererMixin" not in renderer,
        "multiblock outline must not introduce a LevelRenderer mixin")

# Physical membership comes from the shared resolver, never from a second client-side scan.
require("ConsoleMultiblockManager.resolve" in renderer,
        "highlight renderer must reuse the canonical multiblock resolver")
require("ConsoleNetworkState.PARTIALLY_LOADED" in renderer and "ConsoleNetworkState.TOO_LARGE" in renderer,
        "unknown/incomplete multiblock boundaries must fall back to Vanilla")
require("snapshot.members.size <= 1" in renderer,
        "single desks must keep the normal Vanilla outline")

# Member selection shapes are translated into anchor-local coordinates and boolean-unioned.
require("state.getShape(level, member.pos)" in geometry,
        "geometry must use each desk's normal Minecraft selection shape")
require("Shapes.or(combined, localShape)" in geometry,
        "member shapes must be boolean-unioned")
require("combined.optimize()" in geometry,
        "union shape must be optimized to remove internal shared seams")

# Rendering must stay in Minecraft's normal line pipeline and respect Sable sublevel transforms.
require("SableClientRenderPose.apply" in renderer,
        "multiblock outline must use the shared Sable render-space transform")
require("RenderType.lines()" in renderer,
        "multiblock outline must use the normal Minecraft line render type")
require("LevelRenderer.renderVoxelShape" in renderer,
        "multiblock outline must render the combined VoxelShape through LevelRenderer")
require("event.isCanceled = true" in renderer,
        "Vanilla single-block outline must be cancelled after custom rendering")
require("cachedGeometry" in renderer and "snapshot.revision" in renderer,
        "combined highlight geometry must be cached by multiblock revision")

require("python3 tools/verify-multiblock-highlight.py" in workflow,
        "workflow must enforce the multiblock highlight architecture")

print("Validated multiblock highlight: shared resolver, unioned selection shape, outer-edge rendering, Sable transform, Vanilla fallback and revision cache.")
