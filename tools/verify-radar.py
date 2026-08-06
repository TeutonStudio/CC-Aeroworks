#!/usr/bin/env python3
"""Validate optional radar resources and their local development runtime."""

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
        "ponder.cc_aeroworks.radar_display.header",
        *(f"ponder.cc_aeroworks.radar_display.text_{index}" for index in range(1, 6)),
    }
    require(required_keys <= english.keys(), "Missing radar item or Ponder translations")

    for name in ("small_radar_display", "large_radar_display"):
        load_json(MODELS / "block/module" / f"{name}.json")
        load_json(MODELS / "item" / f"{name}.json")
        recipe = load_json(RECIPES / f"{name}.json")
        conditions = recipe.get("neoforge:conditions")
        require(
            conditions == [{"type": "neoforge:mod_loaded", "modid": "create_radar"}],
            f"{name} recipe must require Create: Radars",
        )
        require(recipe.get("result", {}).get("id") == f"cc_aeroworks:{name}", f"Wrong result for {name}")

    items = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCItems.kt")
    modules = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt")
    require("SMALL_RADAR_DISPLAY" in items and "LARGE_RADAR_DISPLAY" in items, "Radar items are not registered")
    require("SMALL_RADAR" in modules and "LARGE_RADAR" in modules, "Radar module types are not registered")

    creative = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/creative/AeroworksCreativeSections.kt")
    creative_accessor = read(
        "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/CreativeModeTabAccessor.kt"
    )
    require(
        '@Accessor("displayItemsSearchTab")' in creative_accessor
        and "ccaeroworks_getSearchTabDisplayItems" in creative_accessor,
        "Creative search entries are not accessible",
    )
    require(
        "ccaeroworks_getSearchTabDisplayItems().removeIf(::isRadarDisplay)" in creative,
        "Radar items are not removed from creative search when Create: Radars is absent",
    )
    require(
        "namespace == CCAeroworks.MOD_ID && !isRadarDisplay(it)" in creative,
        "Radar displays are not classified into the Aeroworks section",
    )
    require(
        "appendMissing(aeroworksItems, CCItems.SMALL_RADAR_DISPLAY" in creative
        and "appendMissing(aeroworksItems, CCItems.LARGE_RADAR_DISPLAY" in creative,
        "Loaded radar displays are not added to the Aeroworks section",
    )

    plugin = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java")
    scene = read("src/main/java/de/teutonstudio/ccaeroworks/client/ponder/RadarDisplayScenes.java")
    require("RadarDisplayScenes::dataLink" in plugin, "Radar Ponder scene is not registered")
    require('isLoaded("create_radar")' in plugin, "Radar Ponder registration is not optional")
    require(scene.count("showText(") == 5, "Radar Ponder scene must contain five explanation steps")
    require('"create_radar", "data_link"' in scene, "Radar Ponder scene does not show the Data Link")

    mixins = load_json(ROOT / "src/main/resources/cc_aeroworks.mixins.json")
    common_mixins = set(mixins.get("mixins", []))
    require("ConsoleBlockEntityRadarMixin" in common_mixins, "Radar snapshot mixin is missing")
    require("compat.CreateRadarDataLinkMixin" in common_mixins, "Optional Data Link mixin is missing")

    snapshot = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/RadarDisplaySnapshot.kt")
    radar_mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityRadarMixin.kt")
    require(
        'putString("id", this@RadarDisplayTrack.id)' in snapshot,
        "Radar track serialization must not resolve CompoundTag.id instead of the track ID",
    )
    require(
        snapshot.count("Tag.TAG_COMPOUND.toInt()") == 4,
        "Radar snapshot NBT calls must pass Int tag IDs on NeoForge 1.21.1",
    )
    require(
        "Tag.TAG_COMPOUND.toInt()" in radar_mixin,
        "Radar mixin NBT calls must pass Int tag IDs on NeoForge 1.21.1",
    )

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require(
        'modId="create_radar"' in metadata and 'reason="Enables the small and large Data Link radar displays."' in metadata,
        "Create: Radars metadata is not optional",
    )
    require(
        'modId="createbigcannons"' in metadata
        and 'versionRange="[5.11.7,5.12)"' in metadata
        and 'reason="Supplies the Create Big Cannons runtime required by Create: Radars."' in metadata,
        "Create Big Cannons metadata is not optional or has the wrong version range",
    )

    manifest = load_json(ROOT / "libs/dependencies.json")
    dependencies = {
        dependency.get("modId"): dependency
        for dependency in manifest.get("dependencies", [])
        if isinstance(dependency, dict)
    }
    require(
        dependencies.get("createbigcannons", {}).get("version") == "5.11.7"
        and dependencies.get("createbigcannons", {}).get("required") is False,
        "Create Big Cannons is not registered as an optional local dependency",
    )
    require(
        dependencies.get("ritchiesprojectilelib", {}).get("version") == "2.1.2"
        and dependencies.get("ritchiesprojectilelib", {}).get("required") is False,
        "Ritchie's Projectile Library is not registered as the optional CBC runtime library",
    )
    require(
        dependencies.get("jei", {}).get("version") == "19.27.0.340"
        and dependencies.get("jei", {}).get("required") is False,
        "JEI is not registered as an optional development dependency",
    )

    build = read("build.gradle")
    properties = read("gradle.properties")
    require("https://cursemaven.com" in build, "CurseMaven repository is missing")
    require("https://maven.blamejared.com" in build, "Official JEI Maven repository is missing")
    require(
        'localRuntime("curse.maven:create-radars-1152836:${create_radars_curse_file_id}")' in build,
        "Create: Radars is not included in local Gradle runtimes",
    )
    require(
        'localRuntime("curse.maven:create-big-cannons-646668:${create_big_cannons_curse_file_id}")' in build,
        "Create Big Cannons is not included in local Gradle runtimes",
    )
    require(
        'localRuntime("curse.maven:ritchies-projectile-library-1279407:${ritchies_projectile_lib_curse_file_id}")' in build,
        "Ritchie's Projectile Library is not included in local Gradle runtimes",
    )
    require(
        'localRuntime("mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}")' in build,
        "JEI is not included in local Gradle runtimes",
    )
    require(
        "create_radar-*.jar" in build
        and "createbigcannons-*.jar" in build
        and "ritchiesprojectilelib-*.jar" in build
        and "jei-*.jar" in build,
        "Automatically resolved development JARs are not excluded from the generic dependency file tree",
    )
    require(
        "create_radars_version=0.4.4-1.21.1" in properties
        and "create_radars_curse_file_id=8041200" in properties,
        "Create: Radars development runtime artifact is not pinned",
    )
    require(
        "create_big_cannons_version=5.11.7" in properties
        and "create_big_cannons_curse_file_id=8303106" in properties,
        "Create Big Cannons development runtime artifact is not pinned",
    )
    require(
        "ritchies_projectile_lib_version=2.1.2" in properties
        and "ritchies_projectile_lib_curse_file_id=7587771" in properties,
        "Ritchie's Projectile Library development runtime artifact is not pinned",
    )
    require(
        "jei_version=19.27.0.340" in properties,
        "JEI development runtime artifact is not pinned",
    )

    config = read("src/main/kotlin/de/teutonstudio/ccaeroworks/config/CCServerConfig.kt")
    require(config.count("Int.MAX_VALUE") == 4, "All four display dimensions must use the unbounded integer maximum")
    require("defineInRange" in config, "Display dimensions must remain positive validated integers")

    docs = read("docs/create-radars-integration.md")
    require("Data Link" in docs and "20 Ticks" in docs and "256" in docs, "Radar integration documentation is incomplete")
    require("Kreativsuche" in docs and "runClient" in docs, "Creative search or development runtime documentation is missing")
    require(
        "Create Big Cannons `5.11.7`" in docs and "Projectile Library `2.1.2`" in docs,
        "CBC or RPL radar dependency documentation is missing",
    )

    dependency_docs = read("libs/README.md")
    require(
        "Just Enough Items" in dependency_docs
        and "19.27.0.340" in dependency_docs
        and "Crafting- und Create-Verarbeitungsrezepte" in dependency_docs,
        "JEI recipe inspection documentation is missing",
    )

    print(
        "Validated optional Create: Radars items, creative visibility, CBC/RPL/JEI development runtime, recipes, "
        "models, mixins, NBT interop, Ponder scene, metadata and display limits."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
