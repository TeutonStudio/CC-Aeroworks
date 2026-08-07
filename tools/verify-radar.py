#!/usr/bin/env python3
"""Validate RadarDisplay native-monitor rendering, synchronization, diagnostics and optional dependency boundaries."""

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

    for obsolete in (
        "radar_background_small",
        "radar_background_large",
        "radar_pixel",
        "radar_selected_pixel",
        "radar_sweep",
        "radar_disconnected",
    ):
        require(
            not (MODELS / "block/module" / f"{obsolete}.json").exists(),
            f"Obsolete custom radar model remains: {obsolete}",
        )

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    compat = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/CreateRadarCompat.kt")
    trace = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/createradar/RadarTrace.kt")
    desk_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
    models = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayModels.kt")
    native_renderer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/CreateRadarNativeMonitorRenderer.kt")
    overlay = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarOverlayRenderer.kt")
    fallback = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/DeskDisplayRenderer.kt")
    flywheel = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleVisualMixin.kt")
    client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")

    require(
        not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/display/RadarSurfaceRenderer.kt").exists(),
        "Custom RadarSurfaceRenderer still exists",
    )

    for token in (
        "enum class RadarLinkStatus",
        "val radarPos: BlockPos?",
        "val detectionTag: CompoundTag",
        "val selectedTrackId: String?",
        "val nativeTracks: CompoundTag",
        "val trackCount: Int",
        "val receivedAtClientTick: Long = -1L",
        'put("tracks", nativeTracks.copy())',
        "snapshot.receivedAtClientTick",
    ):
        require(token in snapshot, f"Native monitor snapshot contract is missing: {token}")
    for forbidden in ("RadarDisplayTrack", "RadarDisplayTrackSprite", "Vec3"):
        require(forbidden not in snapshot, f"Snapshot still redefines native RadarTrack data: {forbidden}")

    require("RADAR_CONTROLLER_NBT_KEY" not in radar_mixin, "Controller location is still persisted")
    require('method = ["tick"]' in radar_mixin and "CreateRadarCompat.refreshDesk" in radar_mixin, "Desk refresh is not attached to block entity tick")
    require("RadarDisplaySnapshot.fromTag(rawPayload, clientTick)" in radar_mixin, "Client snapshot does not record local receipt tick")
    require("snapshot?.trackCount" in radar_mixin, "Client diagnostics do not report native track count")
    for stage in ("M00_DESK_TICK", "N10_SERVER_WRITE_ENTER", "N11_SERVER_WRITE_PAYLOAD", "N20_CLIENT_READ_ENTER", "N21_CLIENT_READ_DECODED"):
        require(stage in radar_mixin, f"Snapshot transport trace stage missing: {stage}")

    for token in (
        "RADAR_TRACK_UTIL_CLASS",
        '"com.happysg.radar.block.radar.track.RadarTrackUtil"',
        "serializeFilteredTracks",
        'invokeStatic(RADAR_TRACK_UTIL_CLASS, "serializeNBTList", filtered)',
        'invokePublic(filter, "test", raw)',
        'invokePublic(radar, "getTracks")',
        "RadarDisplaySnapshot.MAX_SYNCED_TRACKS",
        "nativeTracks = nativeTracks.tag",
        "trackCount = nativeTracks.count",
    ):
        require(token in compat, f"Native RadarTrack payload synchronization is missing: {token}")
    for forbidden in (
        "RadarDisplayTrack",
        "RadarDisplayTrackSprite",
        'invokeFirst(raw, "getPosition"',
        'invokeFirst(raw, "getVelocity"',
        "sortedBy { it.position",
    ):
        require(forbidden not in compat, f"Compat still rebuilds RadarTrack state itself: {forbidden}")
    for stage in (
        "S00_REFRESH_ENTER", "S10_NETWORK_DATA", "S11_FILTERER_LOOKUP", "S12_MONITOR_ENDPOINTS",
        "S13_GROUP_STATE", "S14_RADAR_BLOCK_ENTITY", "S15_RADAR_STATE", "S16_TRACKS_SERIALIZED",
        "S17_SNAPSHOT_RESULT", "S18_SNAPSHOT_DECISION", "S19_NOTIFY_UPDATE", "S99_API_ERROR",
    ):
        require(stage in compat, f"Server radar trace stage missing: {stage}")

    for token in ("[CCA-RADAR-TRACE]", "sessionId", "AtomicLong", "stage", "side", "dimension", "gameTime", "fun periodic", "fun tag"):
        require(token in trace, f"Structured RadarTrace contract is missing: {token}")

    require("radarSurfaces" in desk_access and "RadarSurfaceState" in desk_access, "Desk radar surfaces are not exposed")
    require("RADAR_" not in models, "DeskDisplayModels still registers custom radar partials")
    require("RadarDisplayTrack" not in models, "DeskDisplayModels still depends on custom radar track types")

    for token in (
        "CreateRadarNativeMonitorRenderer",
        'MONITOR_CLASS = "com.happysg.radar.block.monitor.MonitorBlockEntity"',
        'MONITOR_RENDERER_CLASS = "com.happysg.radar.block.monitor.MonitorRenderer"',
        'IRADAR_CLASS = "com.happysg.radar.block.radar.behavior.IRadar"',
        "ModList.get().isLoaded(MOD_ID)",
        "monitorClass.getDeclaredMethod(",
        '"read"',
        "monitorRendererClass.getDeclaredMethod(",
        '"renderRadarDisplay"',
        'put("Filter", snapshot.detectionTag.copy())',
        'put("tracks", snapshot.nativeTracks.copy())',
        "blockEntityRenderDispatcher.getRenderer(monitor)",
        "effectiveFacing",
        "applySurfaceTransform",
    ):
        require(token in native_renderer, f"Native MonitorRenderer bridge is missing: {token}")
    require("import com.happysg.radar" not in native_renderer, "Optional Create: Radars types leaked into bridge signatures")
    require("create_radar:monitor_sprite" not in native_renderer, "Bridge hardcodes native sprite resources instead of using MonitorRenderer")
    for stage in (
        "D00_RENDER_ENTER", "D02_CONTRACT_OK", "D10_SURFACE", "D12_SKIP_NOT_FRESH", "D15_CLIENT_RADAR",
        "D16_SOCKET_TRANSFORM", "D20_VIRTUAL_CREATE", "D22_HYDRATE_INPUT", "D23_HYDRATE_OK",
        "D24_RENDERER_LOOKUP", "D30_BEFORE_NATIVE_RENDER", "D31_NATIVE_RENDER_OK", "D99_NATIVE_RENDER_EXCEPTION",
    ):
        require(stage in native_renderer, f"Native renderer trace stage missing: {stage}")
    for method in ("getRadar", "getTracks", "getSize", "isLinked", "isController", "getShip"):
        require(f'monitorClass.getMethod("{method}")' in native_renderer, f"Native monitor diagnostics do not inspect {method}")

    for token in (
        "RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES",
        "CreateRadarNativeMonitorRenderer.render(",
        "WeakHashMap<ConsoleBlockEntity, Boolean>()",
        "buffers.endBatch()",
    ):
        require(token in overlay, f"Shared native radar overlay is missing: {token}")
    for stage in ("R00_TRACK_ADD", "R01_OVERLAY_STAGE", "R02_OVERLAY_EMPTY", "R04_OVERLAY_DESK", "R08_NATIVE_RETURN", "R09_END_BATCH", "R09_NO_DRAW"):
        require(stage in overlay, f"Overlay trace stage missing: {stage}")

    require("RadarOverlayRenderer.track(desk)" in fallback, "Classic renderer does not register RadarDisplay for native overlay")
    require("RadarSurfaceRenderer" not in fallback, "Classic renderer still invokes custom RadarSurfaceRenderer")
    require("RadarOverlayRenderer.track(blockEntity)" in flywheel, "Flywheel visual does not register RadarDisplay for native overlay")
    require("RadarSurfaceRenderer" not in flywheel, "Flywheel still contains custom radar rendering")
    require("RADAR_" not in flywheel, "Flywheel still creates radar partial models")
    require("NeoForge.EVENT_BUS.addListener(RadarOverlayRenderer::renderLevel)" in client, "Native radar overlay event is not registered")
    require("C00_OVERLAY_LISTENER_REGISTERED" in client and "C03_COMPUTER_VISUAL_REGISTERED" in client, "Client bootstrap trace is incomplete")
    require("SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())" in client, "Computer desk Flywheel visual is missing")

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
        "MonitorRenderer",
        "RadarTrackUtil",
        "runClient",
    ):
        require(token in docs, f"Radar documentation is incomplete: {token}")

    print(
        "Validated native Create: Radars MonitorRenderer reuse, native RadarTrack NBT payloads, "
        "structured end-to-end runtime tracing, shared classic/Flywheel overlay and optional-mod isolation."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
