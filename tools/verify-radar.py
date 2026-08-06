#!/usr/bin/env python3
"""Validate optional radar resources, routing scenes and local development dependencies."""

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
        "ponder.cc_aeroworks.radar_routing.header",
        *(f"ponder.cc_aeroworks.radar_routing.text_{index}" for index in range(1, 7)),
        "ponder.cc_aeroworks.radar_data_link.header",
        *(f"ponder.cc_aeroworks.radar_data_link.text_{index}" for index in range(1, 6)),
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

    items = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCItems.kt")
    modules = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt")
    require("SMALL_RADAR_DISPLAY" in items and "LARGE_RADAR_DISPLAY" in items, "Radar items are not registered")
    require("SMALL_RADAR" in modules and "LARGE_RADAR" in modules, "Radar modules are not registered")

    plugin = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java")
    scene = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java")
    require("RadarDisplayScenes::automaticRouting" in plugin, "Automatic radar routing scene is not registered")
    require("RadarDisplayScenes::dataLinkCompatibility" in plugin, "Data Link compatibility scene is not registered")
    require('isLoaded("create_radar")' in plugin, "Radar Ponder registration is not optional")
    require(scene.count("showText(") == 11, "Radar Ponder scenes must contain eleven explanation steps")
    require('"create_radar", "data_link"' in scene, "Radar Ponder does not show the Data Link")
    require("PonderText.get" in scene and '.text("' not in scene, "Radar Ponder text is not fully localized")

    mixins = load_json(ROOT / "src/main/resources/cc_aeroworks.mixins.json")
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar snapshot mixin is missing")
    require("compat.CreateRadarDataLinkMixin" in common_mixins, "Optional Data Link mixin is missing")

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    require('putString("id", this@RadarDisplayTrack.id)' in snapshot, "Radar track serialization is unsafe")
    require(snapshot.count("Tag.TAG_COMPOUND.toInt()") == 4, "Radar NBT tag IDs must be Int values")
    require("Tag.TAG_COMPOUND.toInt()" in radar_mixin, "Radar mixin NBT tag IDs must be Int values")

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require('modId="create_radar"' in metadata, "Create: Radars metadata is missing")
    require('modId="createbigcannons"' in metadata, "Create Big Cannons metadata is missing")

    manifest = load_json(ROOT / "libs/dependencies.json")
    dependencies = {
        dependency.get("modId"): dependency
        for dependency in manifest.get("dependencies", [])
        if isinstance(dependency, dict)
    }
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
    require("create_radars_version=0.4.4-1.21.1" in properties, "Create: Radars version is not pinned")

    config = read("src/main/kotlin/de/teutonstudio/ccaeroworks/config/CCServerConfig.kt")
    require(config.count("Int.MAX_VALUE") == 4, "All display dimensions must remain unbounded positive integers")

    docs = read("docs/create-radars-integration.md")
    require("automatisch" in docs.lower(), "Radar documentation does not explain automatic routing")
    require("Data Link" in docs and "20 Ticks" in docs and "256" in docs, "Radar documentation is incomplete")
    require("runClient" in docs, "Radar local runtime documentation is missing")

    print(
        "Validated optional radar items, recipes, models, mixins, NBT interop, two localized Ponder scenes, "
        "network routing documentation and development dependencies."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
