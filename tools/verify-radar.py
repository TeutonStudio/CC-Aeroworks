#!/usr/bin/env python3
"""Validate Data Link backed Radar Displays and Create: Radars monitor surfaces."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
MODELS = ROOT / "src/main/resources/assets/cc_aeroworks/models"
RECIPES = ROOT / "src/main/resources/data/cc_aeroworks/recipe"
BLOCK_ATLAS = ROOT / "src/main/resources/assets/minecraft/atlases/blocks.json"


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

    for name in ("small_radar_display", "large_radar_display"):
        load_json(MODELS / "block/module" / f"{name}.json")
        load_json(MODELS / "item" / f"{name}.json")
        recipe = load_json(RECIPES / f"{name}.json")
        require(
            recipe.get("neoforge:conditions") == [{"type": "neoforge:mod_loaded", "modid": "create_radar"}],
            f"{name} recipe must require Create: Radars",
        )
        require(recipe.get("result", {}).get("id") == f"cc_aeroworks:{name}", f"Wrong result for {name}")

    translucent_models = {
        "radar_small_filler": "create_radar:monitor_sprite/radar_bg_filler",
        "radar_small_circle": "create_radar:monitor_sprite/radar_bg_circle",
        "radar_small_sweep": "create_radar:monitor_sprite/radar_sweep",
        "radar_large_filler": "create_radar:monitor_sprite/radar_bg_filler",
        "radar_large_circle": "create_radar:monitor_sprite/radar_bg_circle",
        "radar_large_sweep": "create_radar:monitor_sprite/radar_sweep",
    }
    cutout_models = {
        "radar_track_entity": "create_radar:monitor_sprite/entity_hitbox",
        "radar_track_player": "create_radar:monitor_sprite/player",
        "radar_track_projectile": "create_radar:monitor_sprite/projectile",
        "radar_track_contraption": "create_radar:monitor_sprite/contraption_hitbox",
        "radar_track_selected": "create_radar:monitor_sprite/target_selected",
    }
    for model_name, texture in translucent_models.items():
        model = load_json(MODELS / "block/module" / f"{model_name}.json")
        require(model.get("render_type") == "minecraft:translucent", f"{model_name} must preserve texture alpha")
        require(model.get("textures", {}).get("sprite") == texture, f"{model_name} uses the wrong Create: Radars texture")
    for model_name, texture in cutout_models.items():
        model = load_json(MODELS / "block/module" / f"{model_name}.json")
        require(model.get("render_type") in {"cutout", "minecraft:cutout"}, f"{model_name} must use cutout rendering")
        require(model.get("textures", {}).get("sprite") == texture, f"{model_name} uses the wrong Create: Radars texture")
    load_json(MODELS / "block/module/radar_disconnected.json")

    atlas = load_json(BLOCK_ATLAS)
    require(
        {
            "type": "directory",
            "source": "monitor_sprite",
            "prefix": "monitor_sprite/",
        } in atlas.get("sources", []),
        "Create: Radars monitor sprites are not collected through the block atlas",
    )

    mixins = load_json(ROOT / "src/main/resources/cc_aeroworks.mixins.json")
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar state mixin is missing")
    require("compat.CreateRadarDataLinkItemMixin" in common_mixins, "Data Link target extension is missing")
    require("compat.CreateRadarNetworkControllerMixin" not in common_mixins, "Adjacent controller mixin remains")

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    surface_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarSurfaceState.kt")
    state_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/RadarDeskStateAccess.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    compat = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt")
    desk_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
    models = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayModels.kt")
    surface = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarSurfaceRenderer.kt")
    fallback = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayRenderer.kt")
    flywheel = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleVisualMixin.kt")
    computer_renderer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ComputerControlDeskRenderer.kt")
    client_setup = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
    test_plan = read("docs/radar-controller-test-plan.md")

    require("enum class RadarLinkStatus" in snapshot, "Radar link failures are collapsed into one boolean")
    for status in (
        "ACTIVE",
        "RADAR_NOT_LINKED",
        "RADAR_NOT_LOADED",
        "RADAR_NOT_RUNNING",
        "INVALID_RANGE",
        "API_INCOMPATIBLE",
        "STALE",
    ):
        require(status in snapshot, f"Missing radar link status: {status}")
    require('putString("status"' in snapshot, "Radar link status is not synchronized")
    require("fun contentHash()" in snapshot, "Radar snapshots do not expose a stable content hash")
    require('"VS2", "SABLE", "CONTRAPTION"' in snapshot, "VS2 tracks are not mapped correctly")
    require('putString("sprite"' in snapshot, "Track sprite categories are not serialized")

    for token in (
        "NATIVE_MONITOR_INTERVAL_TICKS",
        "SNAPSHOT_HEARTBEAT_TICKS",
        "getFiltererForEndpoint",
        "monitorEndpoints",
        "detectionTag",
        "selectedTargetId",
        "DetectionConfig#fromTag",
        "DetectionConfig#test",
        "PhysicsHandler#getWorldVec",
        "IRadar#getTracks",
        "IRadar#getRange",
        "IRadar#isRunning",
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
        "shouldSynchronize",
        "logStatusTransition",
        "reportAccessFailure",
        "TrackReadResult.Failure",
    ):
        require(token in compat, f"Native monitor synchronization is missing: {token}")
    require("refreshController" not in compat, "Obsolete controller polling remains")
    require("adjacentDeskNetworks" not in compat, "Obsolete adjacency discovery remains")
    require('@Inject(method = ["tick"]' in radar_mixin, "Desk endpoint is not ticked")
    require("CreateRadarCompat.refreshDesk" in radar_mixin, "Desk tick does not refresh the endpoint")
    require("RADAR_CONTROLLER_NBT_KEY" not in radar_mixin, "Controller location is still persisted")
    require("getRadarPixels" not in state_access and "RadarRasterCache" not in state_access, "Pixel radar state remains")

    require("val facing: Direction" in surface_state, "Radar surfaces do not carry desk orientation")
    require("BlockStateProperties.HORIZONTAL_FACING" in desk_access, "Desk orientation is not read")
    require("RadarSurfaceState(socket, type, snapshot, facing)" in desk_access, "Orientation is not passed")
    require("radarSmallFiller" in models and "radarTrackSelected" in models, "Radar partial models are missing")
    require("contentHash()" in surface and "effectiveStatus" in surface, "Radar keys omit content or freshness")
    require("surface.facing.axis" in surface, "Tracks are not projected into desk-local axes")
    require('key = "track:${track.id}:${track.sprite}"' in surface, "Track identities are unstable")
    require("element.translucent" in surface, "Classic layers are not split by render type")
    require("models.sweep" in surface and "spinning = true" in surface, "Sweep is not rendered")
    require("DeskDisplayModels.radarTrack(track.sprite)" in surface, "Track sprites are not rendered")
    require("RadarSurfaceRenderer.render" in fallback, "Classic renderer omits radar surfaces")

    require("RadarSurfaceRenderer.elements" in flywheel, "Flywheel does not share radar elements")
    require("RadarSurfaceRenderer.sweepAngle" in flywheel, "Flywheel sweep is not animated")
    require("radarElements: MutableMap" in flywheel, "Radar instances are not pooled")
    require("existing.x = desired.x" in flywheel and "existing.z = desired.z" in flywheel, "Moving tracks recreate instances")
    require("entry.value.instance.delete()" in flywheel, "Removed radar elements leak instances")

    require(
        "DeskDisplayRenderer.render(blockEntity" in computer_renderer and "display-only fallback" in computer_renderer,
        "Computer desk display fallback is missing",
    )
    require(
        "SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())" in client_setup
        and "ConsoleVisual(context, blockEntity, partialTick)" in client_setup,
        "Computer desks do not register the native Aeroworks visual",
    )
    require(
        "Alle vier Pultausrichtungen" in test_plan
        and "Data-Link-Endpoint" in test_plan
        and "Data Link entfernen" in test_plan,
        "Radar regression plan omits orientation or native endpoint cleanup",
    )
    require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplayRaster.kt").exists(), "Obsolete pixel radar renderer remains")

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require('modId="create_radar"' in metadata, "Create: Radars metadata is missing")
    require('versionRange="[0.4.9.4,)"' in metadata, "Create: Radars metadata range drifted")

    manifest = load_json(ROOT / "libs/dependencies.json")
    dependencies = {
        dependency.get("modId"): dependency
        for dependency in manifest.get("dependencies", [])
        if isinstance(dependency, dict)
    }
    require(dependencies.get("aeroworks", {}).get("version") == "1.3.0", "Aeroworks version is not pinned")
    require(dependencies.get("create_radar", {}).get("version") == "0.4.9.4-1.21.1", "Create: Radars version is not pinned")

    docs = read("docs/create-radars-integration.md")
    for token in (
        "NetworkData.attachMonitor",
        "getFiltererForEndpoint",
        "DetectionConfig",
        "Blockatlas",
        "Pultausrichtung",
        "Instanzpool",
        "RadarDisplayRaster",
        "256",
        "runClient",
    ):
        require(token in docs, f"Radar documentation is incomplete: {token}")
    require(
        "## Rückseitenplatzierung am ComputerControlDesk" not in docs,
        "Obsolete Network Controller placement section remains",
    )

    print("Validated native Data Link endpoints, filtered radar snapshots, pooled oriented surfaces and computer desks.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
