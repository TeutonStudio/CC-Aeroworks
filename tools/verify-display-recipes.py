#!/usr/bin/env python3
"""Validate display manufacturing recipes and flat-panel inventory models."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPES = ROOT / "src/main/resources/data/cc_aeroworks/recipe"
MODELS = ROOT / "src/main/resources/assets/cc_aeroworks/models"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), f"{path} must contain a JSON object")
    return value


def recipe_outputs(recipe: dict) -> set[str]:
    outputs: set[str] = set()
    result = recipe.get("result")
    if isinstance(result, dict) and isinstance(result.get("id"), str):
        outputs.add(result["id"])
    results = recipe.get("results")
    if isinstance(results, list):
        for entry in results:
            if isinstance(entry, dict) and isinstance(entry.get("id"), str):
                outputs.add(entry["id"])
    return outputs


def verify_recipe_contract() -> None:
    normal_displays = ("two_digit_display", "three_digit_display")
    radar_inputs = {
        "small_radar_display": "two_digit_display",
        "large_radar_display": "three_digit_display",
    }

    for name in normal_displays:
        require(
            not (RECIPES / f"{name}.json").exists(),
            f"{name} must not have the removed top-level crafting-table recipe",
        )

    recipes: list[tuple[Path, dict]] = []
    for path in sorted(RECIPES.rglob("*.json")):
        recipes.append((path, load_json(path)))

    for name in normal_displays:
        item_id = f"cc_aeroworks:{name}"
        crafting_recipes = [
            path
            for path, recipe in recipes
            if item_id in recipe_outputs(recipe)
            and str(recipe.get("type", "")).startswith("minecraft:crafting_")
        ]
        require(
            not crafting_recipes,
            f"{name} still has crafting-table recipes: {', '.join(map(str, crafting_recipes))}",
        )

    for name, base_display in radar_inputs.items():
        path = RECIPES / f"{name}.json"
        recipe = load_json(path)
        require(
            recipe.get("neoforge:conditions")
            == [{"type": "neoforge:mod_loaded", "modid": "create_radar"}],
            f"{name} recipe must remain conditional on Create: Radars",
        )
        require(recipe.get("type") == "create:deploying", f"{name} must use a Deployer recipe")
        require(
            recipe.get("ingredients")
            == [
                {"item": f"cc_aeroworks:{base_display}"},
                {"item": "create_radar:monitor"},
            ],
            f"{name} must deploy create_radar:monitor onto the matching normal display",
        )
        require(
            recipe.get("results") == [{"id": f"cc_aeroworks:{name}"}],
            f"{name} has the wrong Deployer output",
        )
        producers = [
            candidate
            for candidate, candidate_recipe in recipes
            if f"cc_aeroworks:{name}" in recipe_outputs(candidate_recipe)
        ]
        require(producers == [path], f"{name} must have exactly one production recipe")


def verify_template(name: str, expected_width: float) -> None:
    template = load_json(MODELS / "item" / f"template_{name}_display_panel.json")
    require(template.get("ambientocclusion") is False, f"{name} panel must disable ambient occlusion")
    require(template.get("gui_light") == "front", f"{name} panel must use front GUI lighting")

    display = template.get("display")
    require(isinstance(display, dict), f"{name} panel is missing item display transforms")
    required_contexts = {
        "gui",
        "ground",
        "fixed",
        "thirdperson_righthand",
        "thirdperson_lefthand",
        "firstperson_righthand",
        "firstperson_lefthand",
    }
    require(required_contexts <= display.keys(), f"{name} panel is missing item display contexts")

    elements = template.get("elements")
    require(isinstance(elements, list) and len(elements) == 3, f"{name} panel must have three elements")
    by_name = {element.get("name"): element for element in elements if isinstance(element, dict)}
    require(
        set(by_name) == {"screen_housing", "screen", "desk_connector"},
        f"{name} panel must contain housing, screen and desk connector",
    )

    housing = by_name["screen_housing"]
    screen = by_name["screen"]
    housing_from = housing.get("from")
    housing_to = housing.get("to")
    screen_from = screen.get("from")
    screen_to = screen.get("to")
    require(
        all(isinstance(value, list) and len(value) == 3 for value in (housing_from, housing_to, screen_from, screen_to)),
        f"{name} panel elements must use three-dimensional bounds",
    )
    require(housing_to[0] - housing_from[0] == expected_width, f"{name} panel has the wrong width")
    require(housing_to[2] - housing_from[2] <= 2, f"{name} panel housing is not flat")
    require(screen_to[2] <= housing_from[2], f"{name} panel screen must sit on the front face")
    require(screen.get("shade") is False, f"{name} panel screen must render without shading")


def verify_item_models() -> None:
    verify_template("small", 8)
    verify_template("large", 12)

    expected = {
        "two_digit_display": ("small", "minecraft:block/black_concrete"),
        "three_digit_display": ("large", "minecraft:block/black_concrete"),
        "small_radar_display": ("small", "minecraft:block/green_concrete"),
        "large_radar_display": ("large", "minecraft:block/green_concrete"),
    }
    for name, (size, screen_texture) in expected.items():
        model = load_json(MODELS / "item" / f"{name}.json")
        require(
            model.get("parent") == f"cc_aeroworks:item/template_{size}_display_panel",
            f"{name} does not use the {size} flat-panel item template",
        )
        textures = model.get("textures")
        require(isinstance(textures, dict), f"{name} is missing item textures")
        require(
            textures.get("frame") == "cc_aeroworks:block/display_base",
            f"{name} must reuse the display housing texture",
        )
        require(textures.get("screen") == screen_texture, f"{name} has the wrong screen texture")


def main() -> int:
    verify_recipe_contract()
    verify_item_models()
    print("Validated display recipe removal, radar Deployer conversion and flat-panel item models.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
