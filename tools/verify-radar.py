#!/usr/bin/env python3
"""Validate RadarDisplay rendering, synchronization, resources, and optional dependencies."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MODELS = ROOT / "src/main/resources/assets/cc_aeroworks/models"
RECIPES = ROOT / "src/main/resources/data/cc_aeroworks/recipe"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def load_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), f"{path} must contain a JSON object")
    return value


def main() -> int:
    english = load_json(LANG / "en_us.json")
    german = load_json(LANG / "de_de.json")
    require(set(english) == set(german), "German and English language keys differ")

    required_keys = {
        "item.cc_aeroworks.small_radar_display",
        "item.cc_aeroworks.large_radar_display",
        "book.cc_aeroworks.page_7",
        "ponder.cc_aeroworks.radar_controller.header",
        *(f"ponder.cc_aeroworks.radar_controller.text_{index}" for index in range(1, 6)),
        "ponder.cc_aeroworks.radar_direct.header",
        *(f"ponder.cc_aeroworks.radar_direct.text_{index}" for index in range(1, 6)),
    }
    require(required_keys <= english.keys(), "Missing RadarDisplay manual or Ponder translations")
    require("Network Filterer" in english["book.cc_aeroworks.page_7"], "English manual omits the native filterer-first flow")
    require("Network Filterer" in german["book.cc_aeroworks.page_7"], "German manual omits the native filterer-first flow")
    for language in (english, german):
        radar_text = " ".join(
            language[key]
            for key in required_keys
            if key.startswith("ponder.cc_aeroworks.radar_")
        )
        require("Data Link" in radar_text, "Radar Ponder text omits the physical Data Link")
        require("adjacent" not in radar_text.lower() and "angrenzend" not in radar_text.lower(), "Radar Ponder still describes controller adjacency")

    for name in ("small_radar_display", "large_radar_display"):
        load_json(MODELS / "block/module" / f"{name}.json")
        load_json(MODELS / "item" / f"{name}.json")
        recipe = load_json(RECIPES / f"{name}.json")
        require(
            recipe.get("neoforge:conditions") == [{"type": "neoforge:mod_loaded", "modid": "create_radar"}],
            f"{name} recipe must remain optional",
        )
        require(recipe.get("result", {}).get("id") == f"cc_aeroworks:{name}", f"Wrong result for {name}")

    # Radar rendering now reuses the same local partials as the programmable
    # displays. These models are known block-atlas resources and therefore avoid
    # baking Create: Radars' renderer-only MonitorSprite textures.
    for local_model in (
        "display_segment_horizontal",
        "display_segment_vertical",
        "display_pixel",
        "radar_disconnected",
    ):
        load_json(MODELS / "block/module" / f"{local_model}.json")

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    compat = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt")
    desk_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
    models = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayModels.kt")
    surface = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarSurfaceRenderer.kt")
    fallback = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayRenderer.kt")
    flywheel = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleVisualMixin.kt")
    client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")

    for token in (
        "enum class RadarDisplayTrackSprite",
        "enum class RadarLinkStatus",
        "val radarPos: BlockPos?",
        "val detectionTag: CompoundTag",
        "val selectedTrackId: String?",
        "val receivedAtClientTick: Long = -1L",
        'putString("sprite"',
        'put("tracks"',
        "STALE_AFTER_TICKS",
        "snapshot.receivedAtClientTick",
    ):
        require(token in snapshot, f"Radar snapshot contract is missing: {token}")
    require("receivedAtClientTick = receivedAtClientTick" in snapshot, "Client receipt tick is not attached while decoding the snapshot")
    require("server and client gameTime are independent" in snapshot, "Freshness contract no longer documents the independent clocks")

    require("RADAR_CONTROLLER_NBT_KEY" not in radar_mixin, "Controller location is still persisted")
    require('method = ["tick"]' in radar_mixin and "CreateRadarCompat.refreshDesk" in radar_mixin, "Desk refresh is not attached to the actual block entity tick")
    require("if (!clientPacket) return" in radar_mixin, "Radar snapshot is not restricted to client update NBT")
    require("RadarDisplaySnapshot.fromTag(tag.getCompound(RADAR_NBT_KEY), clientTick)" in radar_mixin, "Client snapshot does not record its local receipt tick")
    require("Radar client snapshot desk={}" in radar_mixin, "Client-side radar receipt diagnostics are missing")

    for token in (
        "RadarDisplayTrackSprite.fromCategory",
        'invokeFirst(raw, "getPosition", "position")',
        'invokeFirst(raw, "getVelocity", "velocity")',
        'invokeFirst(raw, "getTrackCategory", "trackCategory")',
        "sortedBy { it.position.distanceToSqr(center) }",
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
    ):
        require(token in compat, f"Native RadarTrack synchronization is missing: {token}")
    require("filter(AeroworksDeskAccess::hasRadarDisplay)" not in compat, "Snapshot is still distributed through a desk-network fallback")
    require("ConsoleMultiblockManager" not in compat, "Radar synchronization still depends on the desk multiblock")

    require("radarSurfaces" in desk_access and "RadarSurfaceState" in desk_access, "Desk radar surfaces are not exposed")
    for token in (
        'val HORIZONTAL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_segment_horizontal"))',
        'val VERTICAL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_segment_vertical"))',
        'val PIXEL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_pixel"))',
    ):
        require(token in models, f"Known-safe local display partial is missing: {token}")
    require("block/module/radar_small_filler" not in models, "Runtime still registers the old external radar sprite partials")
    require("block/module/radar_large_filler" not in models, "Runtime still registers the old external radar sprite partials")
    require("create_radar:monitor_sprite" not in models, "Runtime model registration still depends on Create: Radars monitor sprites")

    require("RenderType.cutout()" in surface, "Classic radar surface is not using the proven local display render path")
    require("DeskDisplayModels.HORIZONTAL" in surface, "Radar frame no longer uses local horizontal segments")
    require("DeskDisplayModels.VERTICAL" in surface, "Radar frame/sweep no longer uses local vertical segments")
    require("DeskDisplayModels.PIXEL" in surface, "Radar contacts no longer use local display pixels")
    require("trackGlyph" in surface and "selectionGlyph" in surface, "Procedural contact glyphs are missing")
    require("spinning = true" in surface and "sweepAngle" in surface, "Radar sweep is not animated")
    require("RadarDisplaySnapshot.isFresh" in surface, "Disconnected X is not gated by the synchronized link state")
    require("snapshot.tracks.hashCode()" in surface, "Flywheel render key is not content based")
    require("snapshot?.hashCode()" not in surface, "Flywheel render key still churns on snapshot transport timestamps")
    require("create_radar:monitor_sprite" not in surface, "Radar surface still depends on renderer-only Create: Radars textures")

    require("RadarSurfaceRenderer.render" in fallback, "Classic renderer does not draw direct radar surfaces")
    require("RadarSurfaceRenderer.elements" in flywheel, "Flywheel does not use the same radar surface elements")
    require("RadarSurfaceRenderer.sweepAngle" in flywheel, "Flywheel sweep is not animated")
    require("SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())" in client, "Computer desk Flywheel visual is missing")
    require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplayRaster.kt").exists(), "Obsolete pixel radar renderer still exists")

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require('modId="create_radar"' in metadata, "Create: Radars metadata is missing")
    require('versionRange="[0.4.9.4,)"' in metadata, "Create: Radars metadata range drifted")

    manifest = load_json(ROOT / "libs/dependencies.json")
    dependencies = {
        dependency.get("modId"): dependency
        for dependency in manifest.get("dependencies", [])
        if isinstance(dependency, dict)
    }
    require(dependencies.get("aeroworks", {}).get("version") == "1.3.0", "Aeroworks mod version is not pinned")
    require(dependencies.get("create_radar", {}).get("version") == "0.4.9.4-1.21.1", "Create: Radars version is not pinned")
    require(dependencies.get("createbigcannons", {}).get("version") == "5.11.7", "CBC version is not pinned")
    require(dependencies.get("ritchiesprojectilelib", {}).get("version") == "2.1.2", "RPL version is not pinned")

    docs = read("docs/create-radars-integration.md")
    for token in (
        "NetworkData",
        "DetectionConfig.test",
        "Fünf-Tick-Zyklus",
        "256",
        "lokalen Client-Empfangstick",
        "runClient",
    ):
        require(token in docs, f"Radar documentation is incomplete: {token}")

    print(
        "Validated optional RadarDisplay resources, client-local freshness, native filtered track state, "
        "local classic/Flywheel rendering, selected targets and pinned Create: Radars dependencies."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
