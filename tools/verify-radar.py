#!/usr/bin/env python3
"""Validate optional radar resources, direct controller scenes and development dependencies."""

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
    require(required_keys <= english.keys(), "Missing radar item or direct-controller Ponder translations")

    for obsolete in ("ponder.cc_aeroworks.radar_routing.header", "ponder.cc_aeroworks.radar_data_link.header"):
        require(obsolete not in english, f"Legacy radar Ponder translation remains: {obsolete}")

    for name in ("small_radar_display", "large_radar_display"):
        load_json(MODELS / "block/module" / f"{name}.json")
        load_json(MODELS / "item" / f"{name}.json")
        recipe = load_json(RECIPES / f"{name}.json")
        require(
            recipe.get("neoforge:conditions") == [{"type": "neoforge:mod_loaded", "modid": "create_radar"}],
            f"{name} recipe must require Create: Radars",
        )
        require(recipe.get("result", {}).get("id") == f"cc_aeroworks:{name}", f"Wrong result for {name}")

    items = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCItems.kt")
    modules = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt")
    require("SMALL_RADAR_DISPLAY" in items and "LARGE_RADAR_DISPLAY" in items, "Radar items are not registered")
    require("SMALL_RADAR" in modules and "LARGE_RADAR" in modules, "Radar modules are not registered")

    plugin = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java")
    scene = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java")
    require("RadarDisplayScenes::controllerConnection" in plugin, "Controller connection scene is not registered")
    require("RadarDisplayScenes::directRadarDisplay" in plugin, "Direct radar scene is not registered")
    require('isLoaded("create_radar")' in plugin, "Radar Ponder registration is not optional")
    require(scene.count("showText(") == 10, "Radar Ponder scenes must contain ten explanation steps")
    require('"create_radar", "data_link"' in scene, "Radar Ponder does not show the connection tool")
    require('"create_radar", "network_filterer"' in scene, "Radar Ponder does not show the Network Controller")
    require('"create_radar", "monitor"' not in scene, "Radar Ponder still depends on a monitor block")
    require("PonderText.get" in scene and '.text("' not in scene, "Radar Ponder text is not fully localized")

    mixins = load_json(ROOT / "src/main/resources/cc_aeroworks.mixins.json")
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar state mixin is missing")
    require(
        "compat.CreateRadarNetworkControllerLinkMixin" in common_mixins,
        "Optional Network Controller item mixin is missing",
    )
    require(
        "compat.CreateRadarDataLinkMixin" not in common_mixins
        and "compat.CreateRadarDataLinkItemMixin" not in common_mixins,
        "Legacy Data Link mixins are still registered",
    )

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    require('putString("id", this@RadarDisplayTrack.id)' in snapshot, "Radar track serialization is unsafe")
    require(snapshot.count("Tag.TAG_COMPOUND.toInt()") == 4, "Radar NBT tag IDs must be Int values")
    require("RADAR_CONTROLLER_NBT_KEY" in radar_mixin, "Network Controller link is not persisted")
    require("CreateRadarCompat.refreshDesk" in radar_mixin, "Direct radar data is not refreshed")

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require('modId="create_radar"' in metadata, "Create: Radars metadata is missing")
    require('modId="createbigcannons"' in metadata, "Create Big Cannons metadata is missing")
    require(
        'modId="aeroworks"\n    type="required"\n    versionRange="[1.3.0,1.3.1)"' in metadata,
        "Aeroworks metadata must target the official 1.3.0 mod release",
    )
    require(
        'modId="create_radar"\n    type="optional"\n'
        '    reason="Enables radar displays linked directly to a Create: Radars Network Controller."\n'
        '    versionRange="[0.4.9.4,)"' in metadata,
        "Create: Radars metadata must describe direct controller displays and preserve the updated range",
    )
    require('1.4.1' not in metadata, "Aeroworks modpack version 1.4.1 leaked into mod metadata")
    require('versionRange="[0.4.9.4)"' not in metadata, "Metadata contains a malformed Maven range")

    manifest = load_json(ROOT / "libs/dependencies.json")
    dependencies = {
        dependency.get("modId"): dependency
        for dependency in manifest.get("dependencies", [])
        if isinstance(dependency, dict)
    }
    require(dependencies.get("aeroworks", {}).get("version") == "1.3.0", "Aeroworks mod version is not pinned")
    require(
        dependencies.get("create_radar", {}).get("version") == "0.4.9.4-1.21.1",
        "Create: Radars version is not pinned",
    )
    require(dependencies.get("createbigcannons", {}).get("version") == "5.11.7", "CBC version is not pinned")
    require(dependencies.get("ritchiesprojectilelib", {}).get("version") == "2.1.2", "RPL version is not pinned")
    require(dependencies.get("jei", {}).get("version") == "19.27.0.340", "JEI version is not pinned")

    build = read("build.gradle")
    properties = read("gradle.properties")
    for token in (
        "curse.maven:create-radars-1152836",
        "curse.maven:create-big-cannons-646668",
        "curse.maven:ritchies-projectile-library-1279407",
        "mezz.jei:jei-${minecraft_version}-neoforge",
    ):
        require(token in build, f"Missing optional local runtime: {token}")
    require("create_radars_version=0.4.9.4-1.21.1" in properties, "Create: Radars runtime version drifted")
    require("create_radars_curse_file_id=8227753" in properties, "Create: Radars CurseForge ID drifted")

    config = read("src/main/kotlin/de/teutonstudio/ccaeroworks/config/CCServerConfig.kt")
    require(config.count("Int.MAX_VALUE") == 4, "All display dimensions must remain unbounded positive integers")

    docs = read("docs/create-radars-integration.md")
    require("Network Controller" in docs, "Radar documentation does not explain the direct source")
    require("kein Data-Link-Block" in docs and "kein Monitorblock" in docs, "Removed source blocks remain documented")
    require("20 Ticks" in docs and "256" in docs and "runClient" in docs, "Radar documentation is incomplete")

    print(
        "Validated optional radar items, direct Network Controller snapshots, two localized Ponder scenes, "
        "official dependency metadata and removal of the monitor/Data Link block runtime path."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
