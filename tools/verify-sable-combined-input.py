#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


spatial = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/sable/SableSpatial.kt")
context = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputContext.kt")
display = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedInputController.kt")
control = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedLeverController.kt")
display_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayPointerActionPayload.kt")
sample_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CombinedControlSamplePayload.kt")
legacy_payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetCombinedLeverValuePayload.kt")

require("Sable.HELPER.getContaining(blockEntity)" in spatial,
        "Sable coordinate bridge must resolve the SubLevel containing a desk")
require("transformPositionInverse(from)" in spatial and "transformPositionInverse(to)" in spatial,
        "world interaction rays must be inverse-projected into SubLevel plot space")
require("Sable.HELPER.getAllIntersecting" in spatial and "BoundingBox3d(from, to)" in spatial,
        "visual target acquisition must enumerate SubLevels intersecting the view ray")
require("Sable.HELPER.distanceSquaredWithSubLevels" in spatial,
        "reach checks must delegate coordinate normalization to Sable")

require("SableSpatial.raySpaces(level, from, to)" in context,
        "Combined context corridor must scan main-level and SubLevel ray spaces")
require("SableSpatial.distanceSquared(level, player.position(), it.pos.center)" in context,
        "cached Combined context reach must be SubLevel-aware")
require("SableSpatial.localRay(desk, from, to)" in display,
        "display geometry ray must be localized before resolving u/v")
require("DeskDisplayGeometry.resolveRay(desk, localRay.from, localRay.to)" in display,
        "display geometry must consume the localized ray")

for name, source in {
    "display watchdog": display,
    "control watchdog": control,
    "display server payload": display_payload,
    "Combined sample payload": sample_payload,
    "legacy Combined payload": legacy_payload,
}.items():
    require("SableSpatial.distanceSquared(level, player.position(), it.pos.center)" in source,
            f"{name} must validate reach in world-aware coordinates")
    require("player.distanceToSqr(it.pos.center)" not in source,
            f"{name} must not compare player world coordinates directly to a Sable plot position")

print(
    "Validated Sable Combined input coordinates: inverse-projected display rays, sublevel-aware "
    "view-ray acquisition, and world-space reach authorization on client and server."
)
