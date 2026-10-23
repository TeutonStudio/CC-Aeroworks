#!/usr/bin/env python3
"""Validate display manufacturing recipes and flat-panel inventory models."""

from __future__ import annotations

import json
import math
from itertools import product
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
    displays = {
        "small_display": (
            "pressing/small_display.json",
            "create:pressing",
            [{"item": "computercraft:monitor_normal"}],
        ),
        "advanced_small_display": (
            "pressing/advanced_small_display.json",
            "create:pressing",
            [{"item": "computercraft:monitor_advanced"}],
        ),
        "large_display": (
            "deploying/large_display.json",
            "create:deploying",
            [{"item": "cc_aeroworks:small_display"}, {"item": "create:iron_sheet"}],
        ),
        "advanced_large_display": (
            "deploying/advanced_large_display.json",
            "create:deploying",
            [{"item": "cc_aeroworks:advanced_small_display"}, {"item": "create:iron_sheet"}],
        ),
    }
    for name in displays:
        require(
            not (RECIPES / f"{name}.json").exists(),
            f"{name} must not have the removed top-level crafting-table recipe",
        )

    recipes: list[tuple[Path, dict]] = []
    for path in sorted(RECIPES.rglob("*.json")):
        recipes.append((path, load_json(path)))

    for name, (relative_path, recipe_type, ingredients) in displays.items():
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
        path = RECIPES / relative_path
        recipe = load_json(path)
        require(recipe.get("type") == recipe_type, f"{name} uses the wrong Create process")
        require(recipe.get("ingredients") == ingredients, f"{name} has the wrong tier/size ingredients")
        require(recipe.get("results") == [{"id": item_id}], f"{name} has the wrong recipe output")
        producers = [candidate for candidate, candidate_recipe in recipes if item_id in recipe_outputs(candidate_recipe)]
        require(producers == [path], f"{name} must have exactly one production recipe")

    for name in ("small_radar_display", "large_radar_display"):
        require(not (RECIPES / f"{name}.json").exists(),
                f"{name} must remain a pseudo-script, not a craftable item")


def verify_tier_contract() -> None:
    items = (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCItems.kt").read_text(encoding="utf-8")
    module_types = (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt").read_text(encoding="utf-8")
    dispatcher = (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt").read_text(encoding="utf-8")
    service = (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskService.kt").read_text(encoding="utf-8")
    color_settings = (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayColorSettings.kt").read_text(encoding="utf-8")

    require(module_types.count("interactive = false") >= 2,
            "both normal display sizes must remain output-only")
    require(module_types.count("interactive = true") >= 2 and "ADVANCED_TWO_DIGIT" in module_types and "ADVANCED_THREE_DIGIT" in module_types,
            "both advanced display sizes must expose touch axes")
    require("CCModuleTypes.isInteractiveDisplay(module.type())" in dispatcher,
            "server dispatch must reject forged touch from normal displays")
    require("fun setDisplayColor" in service and "colorCapable" in service and "0xFFFFFF" in color_settings,
            "advanced display color API or RGB validation is missing")
    for legacy, current in {
        "two_digit_display": "small_display",
        "three_digit_display": "large_display",
        "advanced_two_digit_display": "advanced_small_display",
        "advanced_three_digit_display": "advanced_large_display",
    }.items():
        require(
            f'ITEMS.addAlias(CCAeroworks.id("{legacy}"), CCAeroworks.id("{current}"))' in items,
            f"missing item migration alias {legacy} -> {current}",
        )


def verify_module_model(name: str, path: str, outer: tuple[list[float], list[float]],
                        screen_bounds: tuple[list[float], list[float]]) -> list[dict]:
    model = load_json(MODELS / "block" / "module" / f"{path}.json")
    require(model.get("ambientocclusion") is False, f"{name} module must disable ambient occlusion")
    textures = model.get("textures")
    require(isinstance(textures, dict), f"{name} module is missing textures")
    require(textures.get("base") == "cc_aeroworks:block/display_base",
            f"{name} module must retain its Aeroworks mounting base")
    require(textures.get("frame") == "computercraft:block/monitor_normal_15" and
            textures.get("frame_side") == "computercraft:block/monitor_normal_4",
            f"{name} normal frame must use the grey CC:Tweaked monitor textures")
    require(textures.get("screen") == "computercraft:block/monitor_normal_15",
            f"{name} module must use the powered-off normal monitor face")

    elements = model.get("elements")
    require(isinstance(elements, list), f"{name} module is missing geometry")
    by_name = {element.get("name"): element for element in elements if isinstance(element, dict)}
    require(set(by_name) == {
        "mounted_base", "screen_off", "frame_north", "frame_south", "frame_west", "frame_east"
    }, f"{name} module must contain a recessed screen and four-piece frame")
    require((by_name["mounted_base"].get("from"), by_name["mounted_base"].get("to")) == outer,
            f"{name} module has the wrong outer housing bounds")
    screen = by_name["screen_off"]
    require((screen.get("from"), screen.get("to")) == screen_bounds,
            f"{name} powered-off screen must preserve the programmable surface bounds")
    require(screen.get("shade") is False and set(screen.get("faces", {})) == {"up"},
            f"{name} powered-off screen must be a non-shaded upward face")
    require(all(frame.get("to", [0, 0])[1] == 2.45 for key, frame in by_name.items() if key.startswith("frame_")),
            f"{name} CC:Tweaked frame must stand above the dynamic display surface")
    return elements


def verify_item_icon(name: str, parent: str, elements: list[dict]) -> None:
    model = load_json(MODELS / "item" / f"{name}.json")
    require(model.get("parent") == f"cc_aeroworks:block/module/{parent}",
            f"{name} item icon must reuse its mounted 3D module model")
    require("textures" not in model and "elements" not in model,
            f"{name} item icon must not substitute a lit surface or duplicate module geometry")
    require(model.get("gui_light") == "front", f"{name} item icon must use front GUI lighting")
    display = model.get("display")
    require(isinstance(display, dict), f"{name} item icon is missing display transforms")
    required_contexts = {
        "gui", "ground", "fixed", "thirdperson_righthand", "thirdperson_lefthand",
        "firstperson_righthand", "firstperson_lefthand",
    }
    require(required_contexts <= display.keys(), f"{name} item icon is missing display contexts")

    gui = display.get("gui")
    require(isinstance(gui, dict), f"{name} item icon is missing its GUI transform")
    rotation = gui.get("rotation")
    translation = gui.get("translation")
    scale = gui.get("scale")
    require(
        all(isinstance(value, list) and len(value) == 3 for value in (rotation, translation, scale)),
        f"{name} item GUI transform must use three-dimensional vectors",
    )

    def transform(point: tuple[float, float, float]) -> tuple[float, float]:
        x, y, z = (coordinate - 8 for coordinate in point)
        rx, ry, rz = (math.radians(angle) for angle in rotation)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        return x * scale[0] + translation[0], y * scale[1] + translation[1]

    bounds_from = [min(element["from"][axis] for element in elements) for axis in range(3)]
    bounds_to = [max(element["to"][axis] for element in elements) for axis in range(3)]
    projected = [
        transform(point)
        for point in product(*(zip(bounds_from, bounds_to)))
    ]
    projected_x = [point[0] for point in projected]
    projected_y = [point[1] for point in projected]
    center_x = (min(projected_x) + max(projected_x)) / 2
    center_y = (min(projected_y) + max(projected_y)) / 2
    require(abs(center_x) < 0.01 and abs(center_y) < 0.01,
            f"{name} 3D item icon is not centered in its slot")
    require(max(map(abs, projected_x)) <= 8 and max(map(abs, projected_y)) <= 8,
            f"{name} 3D item icon exceeds its 16x16 slot")


def verify_item_models() -> None:
    small = verify_module_model(
        "small", "small_display", ([4, 0, 4], [12, 2, 12]), ([4.5, 2, 4.5], [11.5, 2.18, 11.5])
    )
    large = verify_module_model(
        "large", "large_display", ([2.5, 0, 4], [13.5, 2, 12]), ([3, 2, 4.5], [13, 2.18, 11.5])
    )

    for path, parent in {
        "advanced_small_display": "small_display",
        "advanced_large_display": "large_display",
    }.items():
        model = load_json(MODELS / "block" / "module" / f"{path}.json")
        require(model.get("parent") == f"cc_aeroworks:block/module/{parent}",
                f"{path} must inherit the matching 3D display geometry")
        textures = model.get("textures", {})
        require(textures.get("frame") == "computercraft:block/monitor_advanced_15" and
                textures.get("frame_side") == "computercraft:block/monitor_advanced_4" and
                textures.get("screen") == "computercraft:block/monitor_advanced_15",
                f"{path} must use the yellow powered-off CC:Tweaked monitor textures")

    verify_item_icon("small_display", "small_display", small)
    verify_item_icon("advanced_small_display", "advanced_small_display", small)
    verify_item_icon("large_display", "large_display", large)
    verify_item_icon("advanced_large_display", "advanced_large_display", large)

    for obsolete in (
        MODELS / "item" / "template_small_display_panel.json",
        MODELS / "item" / "template_large_display_panel.json",
        ROOT / "src/main/resources/assets/cc_aeroworks/textures/item/small_display_active.png",
        ROOT / "src/main/resources/assets/cc_aeroworks/textures/item/large_display_active.png",
        MODELS / "block" / "module" / "two_digit_display.json",
        MODELS / "block" / "module" / "three_digit_display.json",
        MODELS / "block" / "module" / "advanced_two_digit_display.json",
        MODELS / "block" / "module" / "advanced_three_digit_display.json",
    ):
        require(not obsolete.exists(), f"obsolete lit display icon asset remains: {obsolete}")


def main() -> int:
    verify_recipe_contract()
    verify_tier_contract()
    verify_item_models()
    print("Validated normal/advanced small and large display recipes and item models; radar remains a pseudo-script source.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
