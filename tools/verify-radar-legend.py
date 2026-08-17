#!/usr/bin/env python3
"""Validate the large RadarDisplay contact legend contract."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGEND = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/client/render/RadarLegendRenderer.kt"
OVERLAY = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/client/render/RadarOverlayRenderer.kt"
BYTECODE = ROOT / "tools/verify-create-radars-bytecode.py"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> int:
    legend = read(LEGEND)
    overlay = read(OVERLAY)
    bytecode = read(BYTECODE)

    require("import com.happysg.radar" not in legend, "Legend leaks optional Create: Radars classes into imports")
    for token in (
        "RadarDisplayType.LARGE",
        "RadarDisplaySnapshot.isFresh",
        "RADAR_TRACK_UTIL_CLASS",
        '"com.happysg.radar.block.radar.track.RadarTrackUtil"',
        '"com.happysg.radar.block.radar.track.RadarTrack"',
        'getMethod("deserializeListNBT", CompoundTag::class.java)',
        'getMethod("getTrackCategory")',
        '"PLAYER"',
        '"VS2"',
        '"CONTRAPTION"',
        '"MOB", "HOSTILE"',
        '"PROJECTILE"',
        '"ANIMAL"',
        '"ITEM"',
        '"PLY ${formatCount(counts.players)}"',
        '"SHP ${formatCount(counts.ships)}"',
        '"CTR ${formatCount(counts.contraptions)}"',
        '"MOB ${formatCount(counts.mobs)}"',
        '"PRJ ${formatCount(counts.projectiles)}"',
        '"ANI ${formatCount(counts.animals)}"',
        '"ITM ${formatCount(counts.items)}"',
        'value >= 100 -> "99+"',
        "Font.DisplayMode.POLYGON_OFFSET",
        "LightTexture.FULL_BRIGHT",
        "L10_LEGEND_RENDER",
    ):
        require(token in legend, f"Large radar legend contract is missing: {token}")

    require(
        "if (surface.type != RadarDisplayType.LARGE) continue" in legend,
        "Legend must stay exclusive to the large RadarDisplay",
    )
    require(
        "RadarLegendRenderer.render(desk, poseStack, buffers)" in overlay,
        "Shared radar overlay does not invoke the legend renderer",
    )
    require(
        "val legendRendered = if (rendered)" in overlay,
        "Legend must only render after a successful native radar surface",
    )

    for token in (
        "DESERIALIZE_TRACKS_DESCRIPTOR",
        '"(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/List;"',
        'method_section(output, "deserializeListNBT", DESERIALIZE_TRACKS_DESCRIPTOR)',
        '"getTrackCategory"',
        "method_section(radar_track, method)",
    ):
        require(token in bytecode, f"Pinned Create: Radars bytecode verifier does not cover legend dependency: {token}")

    print(
        "Validated large RadarDisplay contact legend: native filtered snapshot reuse, seven Network Filterer categories, "
        "HOSTILE-to-MOB grouping, compact count formatting, optional-mod isolation and shared overlay rendering."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
