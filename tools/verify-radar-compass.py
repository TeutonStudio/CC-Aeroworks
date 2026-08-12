#!/usr/bin/env python3
"""Validate the fixed north-up RadarDisplay compass overlay contract."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def main() -> int:
    compass = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarCompassRenderer.kt")
    overlay = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarOverlayRenderer.kt")

    for token in (
        "object RadarCompassRenderer",
        'CardinalLabel("N", 0.0, -COMPASS_RADIUS)',
        'CardinalLabel("E", COMPASS_RADIUS, 0.0)',
        'CardinalLabel("S", 0.0, COMPASS_RADIUS)',
        'CardinalLabel("W", -COMPASS_RADIUS, 0.0)',
        "for (surface in AeroworksDeskAccess.radarSurfaces(desk))",
        "RadarDisplaySnapshot.isFresh(snapshot, gameTime)",
        "ConsoleBlock.rotationFor(desk.blockState)",
        "socket.orientation()",
        "H10_COMPASS_RENDER",
    ):
        require(token in compass, f"Radar compass contract is missing: {token}")

    for forbidden in (
        "effectiveFacing",
        "getGlobalAngle",
        "Direction.NORTH",
        "Direction.EAST",
        "Direction.SOUTH",
        "Direction.WEST",
        "import com.happysg.radar",
    ):
        require(forbidden not in compass, f"Radar compass must remain local north-up; found: {forbidden}")

    require(
        "RadarCompassRenderer.render(desk, poseStack, buffers)" in overlay,
        "Shared radar overlay does not invoke RadarCompassRenderer",
    )
    require(
        "if (rendered)" in overlay,
        "Compass overlay is not gated by successful native radar rendering",
    )
    require(
        "native=$rendered compass=$compassRendered legend=$legendRendered" in overlay,
        "Overlay diagnostics do not report compass rendering",
    )

    print("Validated fixed north-up N/E/S/W labels on shared RadarDisplay overlays.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
