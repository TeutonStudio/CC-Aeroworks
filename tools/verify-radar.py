#!/usr/bin/env python3
"""Validate optional radar resources, adjacent controllers and direct monitor surfaces."""

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
        "ponder.cc_aeroworks.radar_controller.header",
        *(f"ponder.cc_aeroworks.radar_controller.text_{index}" for index in range(1, 6)),
        "ponder.cc_aeroworks.radar_direct.header",
        *(f"ponder.cc_aeroworks.radar_direct.text_{index}" for index in range(1, 6)),
    }
    require(required_keys <= english.keys(), "Missing radar item or Ponder translations")

    for obsolete in (
        "ponder.cc_aeroworks.radar_routing.header",
        "ponder.cc_aeroworks.radar_data_link.header",
        "message.cc_aeroworks.radar_controller_linked",
    ):
        require(obsolete not in english, f"Legacy radar translation remains: {obsolete}")

    for name in ("small_radar_display", "large_radar_display"):
        load_json(MODELS / "block/module" / f"{name}.json")
        load_json(MODELS / "item" / f"{name}.json")
        recipe = load_json(RECIPES / f"{name}.json")
        require(
            recipe.get("neoforge:conditions") == [{"type": "neoforge:mod_loaded", "modid": "create_radar"}],
            f"{name} recipe must require Create: Radars",
        )
        require(recipe.get("result", {}).get("id") == f"cc_aeroworks:{name}", f"Wrong result for {name}")

    radar_models = {
        "radar_small_filler": "create_radar:monitor_sprite/radar_bg_filler",
        "radar_small_circle": "create_radar:monitor_sprite/radar_bg_circle",
        "radar_small_sweep": "create_radar:monitor_sprite/radar_sweep",
        "radar_large_filler": "create_radar:monitor_sprite/radar_bg_filler",
        "radar_large_circle": "create_radar:monitor_sprite/radar_bg_circle",
        "radar_large_sweep": "create_radar:monitor_sprite/radar_sweep",
        "radar_track_entity": "create_radar:monitor_sprite/entity_hitbox",
        "radar_track_player": "create_radar:monitor_sprite/player",
        "radar_track_projectile": "create_radar:monitor_sprite/projectile",
        "radar_track_contraption": "create_radar:monitor_sprite/contraption_hitbox",
        "radar_track_selected": "create_radar:monitor_sprite/target_selected",
    }
    for model_name, texture in radar_models.items():
        model = load_json(MODELS / "block/module" / f"{model_name}.json")
        require(model.get("render_type") == "cutout", f"{model_name} must use cutout rendering")
        require(model.get("textures", {}).get("sprite") == texture, f"{model_name} uses the wrong Create: Radars texture")
    load_json(MODELS / "block/module/radar_disconnected.json")

    items = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCItems.kt")
    modules = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt")
    require("SMALL_RADAR_DISPLAY" in items and "LARGE_RADAR_DISPLAY" in items, "Radar items are not registered")
    require("SMALL_RADAR" in modules and "LARGE_RADAR" in modules, "Radar modules are not registered")

    plugin = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java")
    scene = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java")
    require("RadarDisplayScenes::controllerConnection" in plugin, "Controller scene is not registered")
    require("RadarDisplayScenes::directRadarDisplay" in plugin, "Direct radar scene is not registered")
    require('isLoaded("create_radar")' in plugin, "Radar Ponder registration is not optional")
    require(scene.count("showText(") == 10, "Radar Ponder scenes must contain ten explanation steps")
    require('"create_radar", "data_link"' in scene, "Radar Ponder does not show native radar linking")
    require('"create_radar", "network_filterer"' in scene, "Radar Ponder does not show the Network Controller")
    require('"create_radar", "monitor"' not in scene, "Radar Ponder still depends on a monitor block")

    mixins = load_json(ROOT / "src/main/resources/cc_aeroworks.mixins.json")
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar state mixin is missing")
    require(
        "compat.CreateRadarNetworkControllerMixin" in common_mixins
        and "compat.CreateRadarNetworkControllerLinkMixin" not in common_mixins
        and "compat.CreateRadarDataLinkMixin" not in common_mixins
        and "compat.CreateRadarDataLinkItemMixin" not in common_mixins,
        "Controller tick mixin is missing or a Data Link mixin remains",
    )

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    state_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/RadarDeskStateAccess.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    compat = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt")
    desk_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
    models = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayModels.kt")
    surface = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarSurfaceRenderer.kt")
    fallback = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayRenderer.kt")
    flywheel = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleVisualMixin.kt")

    require('putString("id", this@RadarDisplayTrack.id)' in snapshot, "Radar track serialization is unsafe")
    require("enum class RadarDisplayTrackSprite" in snapshot, "Track sprite categories are not synchronized")
    require('putString("sprite"' in snapshot, "Track sprite categories are not serialized")
    require("RadarDisplaySnapshot.isFresh" in surface, "Direct surface does not reject stale snapshots")
    require("RadarDisplayTrackSprite.fromCategory" in compat, "Create: Radars categories are not mapped")
    require('invokeAny(raw, "getPosition", "position")' in compat, "Track position compatibility fallback is missing")
    require("RADAR_CONTROLLER_NBT_KEY" not in radar_mixin, "Controller location is still persisted")
    require("getRadarPixels" not in state_access and "RadarRasterCache" not in state_access, "Radar state still exposes pixel raster APIs")
    require("radarSurfaces" in desk_access and "RadarSurfaceState" in desk_access, "Desk radar surfaces are not exposed")
    require("radarSmallFiller" in models and "radarTrackSelected" in models, "Direct radar partial models are not registered")
    require("models.sweep" in surface and "spinning = true" in surface, "Radar sweep is not rendered directly")
    require("DeskDisplayModels.radarTrack(track.sprite)" in surface, "Track sprites are not rendered directly")
    require("RadarSurfaceRenderer.render" in fallback, "Classic renderer does not draw direct radar surfaces")
    require("RadarSurfaceRenderer.elements" in flywheel, "Flywheel does not use the direct radar surface elements")
    require("RadarSurfaceRenderer.sweepAngle" in flywheel, "Flywheel sweep is not animated")
    require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplayRaster.kt").exists(), "Obsolete pixel radar renderer still exists")

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require('modId="create_radar"' in metadata, "Create: Radars metadata is missing")
    require('modId="createbigcannons"' in metadata, "Create Big Cannons metadata is missing")
    require(
        'modId="aeroworks"\n    type="required"\n    versionRange="[1.3.0,1.3.1)"' in metadata,
        "Aeroworks metadata must target the official 1.3.0 mod release",
    )
    require('versionRange="[0.4.9.4,)"' in metadata, "Create: Radars metadata range drifted")
    require('versionRange="[0.4.9.4)"' not in metadata, "Metadata contains a malformed Maven range")

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

    build = read("build.gradle")
    properties = read("gradle.properties")
    for token in (
        "curse.maven:create-radars-1152836",
        "curse.maven:create-big-cannons-646668",
        "curse.maven:ritchies-projectile-library-1279407",
    ):
        require(token in build, f"Missing optional local runtime: {token}")
    require("create_radars_version=0.4.9.4-1.21.1" in properties, "Create: Radars runtime version drifted")
    require("create_radars_curse_file_id=8227753" in properties, "Create: Radars CurseForge ID drifted")

    docs = read("docs/create-radars-integration.md")
    require("direkt angrenzenden Network Controller" in docs, "Radar docs do not explain automatic adjacency")
    require("Direkte Monitoroberfläche" in docs, "Radar docs do not explain direct surface rendering")
    require("RadarDisplayRaster" in docs and "Pixelmatrix" in docs, "Radar docs do not retire the pixel renderer")
    require("20 Ticks" in docs and "256" in docs and "runClient" in docs, "Radar documentation is incomplete")

    print(
        "Validated adjacent Network Controller snapshots and direct Create: Radars monitor surfaces in both render paths."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
