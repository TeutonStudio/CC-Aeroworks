#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


desk_target = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayTarget.kt")
telemetry_target = read("src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryDisplayTarget.kt")
bounds = read("src/main/kotlin/de/teutonstudio/ccaeroworks/multiblock/ConsoleMultiblockDisplayBounds.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
workflow = read(".github/workflows/verify.yml")

# Use Create's native DisplayTarget extension point. DisplayLinkBlockItem asks the
# target for getMultiblockBounds() and renders the returned AABB with its Outliner.
require("override fun getMultiblockBounds" in desk_target,
        "DeskDisplayTarget must provide Create Display Link multiblock bounds")
require("ConsoleMultiblockDisplayBounds.resolve" in desk_target,
        "DeskDisplayTarget must reuse the shared ControlDesk bounds resolver")

# The ComputerControlDesk is registered as the telemetry target. Restrict the
# override to ConsoleBlockEntity so Simulated docking telemetry keeps normal bounds.
require("override fun getMultiblockBounds" in telemetry_target,
        "TelemetryDisplayTarget must expose ControlDesk multiblock bounds")
require("level.getBlockEntity(pos) !is ConsoleBlockEntity" in telemetry_target,
        "Telemetry target bounds must remain scoped to ControlDesk block entities")
require("ConsoleMultiblockDisplayBounds.resolve" in telemetry_target,
        "ComputerControlDesk telemetry target must reuse the shared bounds resolver")

# Bounds must come from the canonical multiblock resolver, not from a second scan.
require("ConsoleMultiblockManager.resolve" in bounds,
        "Display Link bounds must reuse the canonical ControlDesk multiblock resolver")
require("state.getShape(level, member.pos)" in bounds,
        "Display Link bounds must include each desk's actual selection shape")
require("AABB(" in bounds and "minOf(" in bounds and "maxOf(" in bounds,
        "Display Link bounds must merge member boxes into one exterior AABB")
require("ConsoleNetworkState.PARTIALLY_LOADED" in bounds and "ConsoleNetworkState.TOO_LARGE" in bounds,
        "Incomplete multiblocks must fall back instead of claiming incomplete outer bounds")

# The previous implementation intercepted Minecraft's normal block highlight. That
# is unrelated to Create's Display Link selection and must stay gone.
require("ConsoleMultiblockHighlightRenderer" not in client,
        "client must not register a Vanilla block-highlight replacement")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/ConsoleMultiblockHighlightRenderer.kt").exists(),
        "obsolete Vanilla highlight renderer must be removed")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/ConsoleMultiblockHighlightGeometry.kt").exists(),
        "obsolete Vanilla highlight geometry must be removed")
require(not (ROOT / "tools/verify-multiblock-highlight.py").exists(),
        "obsolete Vanilla highlight verifier must be removed")

require("python3 tools/verify-display-link-multiblock-highlight.py" in workflow,
        "workflow must enforce the Create Display Link multiblock highlight contract")

print("Validated Display Link multiblock highlight: native Create Outliner bounds, canonical ControlDesk resolution, ComputerControlDesk telemetry coverage, and no Vanilla highlight interception.")
