#!/usr/bin/env python3
"""Validate optional Create: Radars resources without requiring third-party JARs."""

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

    metadata = read("src/main/templates/META-INF/neoforge.mods.toml")
    require('modId="create_radar"' in metadata and 'type="optional"' in metadata, "Create: Radars metadata is not optional")

    config = read("src/main/kotlin/de/teutonstudio/ccaeroworks/config/CCServerConfig.kt")
    require(config.count("Int.MAX_VALUE") == 4, "All four display dimensions must use the unbounded integer maximum")
    require("defineInRange" in config, "Display dimensions must remain positive validated integers")

    docs = read("docs/create-radars-integration.md")
    require("Data Link" in docs and "20 Ticks" in docs and "256" in docs, "Radar integration documentation is incomplete")

    print("Validated optional Create: Radars items, recipes, models, mixins, Ponder scene, metadata and display limits.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
