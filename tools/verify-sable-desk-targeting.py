#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


spatial = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/sable/SableSpatial.kt")
client_spatial = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/sable/SableClientSpatial.kt")
context = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputContext.kt")
control = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedLeverController.kt")
display = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedInputController.kt")
sample = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CombinedControlSamplePayload.kt")
display_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayPointerActionPayload.kt")
legacy = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetCombinedLeverValuePayload.kt")
workflow = read(".github/workflows/verify.yml")
doc = read("docs/sable-desk-targeting.md")

# Server/gameplay space must stay independent from client interpolation classes.
require("Sable.HELPER.projectOutOfSubLevel" in spatial,
        "gameplay spatial bridge must project plot positions for Vanilla world permission checks")
require("Sable.HELPER.distanceSquaredWithSubLevels" in spatial,
        "gameplay spatial bridge must delegate mixed-space reach checks to Sable")
require("ClientSubLevel" not in spatial and "renderPose" not in spatial,
        "server-safe spatial bridge must not depend on client interpolation classes")

# Client acquisition must match Sable GameRenderer picking, which uses renderPose during a frame.
require("import dev.ryanhcode.sable.sublevel.ClientSubLevel" in client_spatial,
        "client spatial bridge must operate on ClientSubLevel")
require(client_spatial.count("renderPose()") >= 2,
        "client local rays and intersected SubLevel rays must use the current render pose")
require("logicalPose()" not in client_spatial,
        "client gaze acquisition must not regress to the tick-only logical pose")
require("Sable.HELPER.getAllIntersecting" in client_spatial and "BoundingBox3d(from, to)" in client_spatial,
        "client acquisition must enumerate Sable SubLevels crossed by the world view ray")
require("transformPositionInverse(from)" in client_spatial and "transformPositionInverse(to)" in client_spatial,
        "world view rays must be inverse-projected into plot coordinates")
require("Sable.HELPER.getContaining(level, pos) === subLevel" in client_spatial,
        "plot-local corridor scans must remain inside the selected SubLevel")

# Vanilla/Sable BlockHitResult positions are deliberately plot-local. Preserve them for BE/network lookup.
require("val hit = minecraft.hitResult as? BlockHitResult" in context,
        "direct desk selection must continue to consume Sable's vanilla hit result")
require("level.getBlockEntity(hit.blockPos) as? ConsoleBlockEntity" in context,
        "vanilla SubLevel hit BlockPos must be used directly for desk BlockEntity lookup")
require("ConsoleMultiblockManager.resolve(level, hit.blockPos)" in context,
        "multiblock resolution must remain in plot coordinates after a vanilla Sable hit")
require("SableSpatial.worldBlockPos(level, hit.blockPos)" not in context,
        "direct vanilla hit BlockPos must not be projected before plot-local multiblock lookup")

# Fallback targeting for visual modules must inspect the rendered ship pose, not only the main world.
require("SableClientSpatial.raySpaces(level, from, to)" in context,
        "view-ray fallback must scan main-level and render-pose SubLevel ray spaces")
require("SableClientSpatial.belongsTo(level, pos, raySpace.subLevel)" in context,
        "view-ray fallback must reject blocks from unrelated plot spaces")
require("SableClientSpatial.localRay(desk, from, to)" in display,
        "display geometry ray must be transformed with the current Sable render pose")
require("DeskDisplayGeometry.resolveRay(desk, localRay.from, localRay.to)" in display,
        "display geometry must consume the localized render-pose ray")

# Every client/server reach gate for Combined desk interaction must understand plot coordinates.
for name, source in {
    "cached network context": context,
    "control watchdog": control,
    "display watchdog": display,
    "atomic Combined payload": sample,
    "display pointer payload": display_payload,
    "legacy Combined payload": legacy,
}.items():
    require("SableSpatial.distanceSquared(level, player.position(), it.pos.center)" in source,
            f"{name} must use Sable-aware mixed-space distance checks")
    require("player.distanceToSqr(it.pos.center)" not in source,
            f"{name} must not compare world player coordinates directly with plot desk coordinates")

# Payload identity remains plot-local, but Vanilla permission checks require a world position.
for name, source in {
    "atomic Combined payload": sample,
    "display pointer payload": display_payload,
    "legacy Combined payload": legacy,
}.items():
    require("level.hasChunkAt(payload.pos)" in source,
            f"{name} must keep chunk/BlockEntity identity in plot coordinates")
    require("level.mayInteract(player, SableSpatial.worldBlockPos(level, payload.pos))" in source,
            f"{name} must project the desk position for Vanilla permission checks")
    require("level.mayInteract(player, payload.pos)" not in source,
            f"{name} must not pass a Sable plot position to Vanilla permission checks")

require("render pose" in doc.lower() and "plot" in doc.lower() and "world" in doc.lower(),
        "Sable desk targeting architecture document must describe all coordinate spaces")
require("python3 tools/verify-sable-desk-targeting.py" in workflow,
        "repository workflow must enforce the Sable desk targeting contract")

print(
    "Validated Sable desk targeting: vanilla hit positions remain plot-local, custom gaze rays use "
    "ClientSubLevel render poses, and reach/permission checks cross coordinate spaces explicitly."
)
