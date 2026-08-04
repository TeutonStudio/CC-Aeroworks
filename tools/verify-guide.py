#!/usr/bin/env python3
"""Validate the in-game guide, Ponder registration and wiki entry points."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
GUIDE_CONTENT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/GuideBookContent.kt"
CLIENT_ENTRY = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt"
PONDER_PLUGIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java"
PONDER_SCENE = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/ComputerControlDeskScenes.java"
PONDER_STRUCTURE = ROOT / "src/main/resources/assets/cc_aeroworks/ponder/computer_control_desk.nbt"
WIKI_CONTROLS = ROOT / "wiki/Bedienung.md"
WIKI_SIDEBAR = ROOT / "wiki/_Sidebar.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_language(name: str) -> dict[str, str]:
    path = LANG_DIR / name
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{path} must contain a JSON object")
    require(all(isinstance(key, str) and isinstance(value, str) for key, value in data.items()),
            f"{path} must contain string keys and values")
    return data


def main() -> int:
    german = load_language("de_de.json")
    english = load_language("en_us.json")
    require(set(german) == set(english), "de_de.json and en_us.json must expose the same keys")

    guide_source = GUIDE_CONTENT.read_text(encoding="utf-8")
    guide_keys = set(re.findall(r'"(guide\.cc_aeroworks\.[a-z0-9_.]+)"', guide_source))
    require(guide_keys, "GuideBookContent.kt contains no translation keys")
    missing = sorted(guide_keys - german.keys())
    require(not missing, f"Missing guide translations: {', '.join(missing)}")

    for page in range(1, 9):
        key = f"book.cc_aeroworks.page_{page}"
        require(key in german, f"Missing fallback book page: {key}")

    ponder_keys = ["ponder.cc_aeroworks.computer_control_desk.header"] + [
        f"ponder.cc_aeroworks.computer_control_desk.text_{index}" for index in range(1, 9)
    ]
    missing_ponder = [key for key in ponder_keys if key not in german]
    require(not missing_ponder, f"Missing Ponder translations: {', '.join(missing_ponder)}")

    client_source = CLIENT_ENTRY.read_text(encoding="utf-8")
    plugin_source = PONDER_PLUGIN.read_text(encoding="utf-8")
    scene_source = PONDER_SCENE.read_text(encoding="utf-8")
    require("PonderIndex.addPlugin(CCAeroworksPonderPlugin())" in client_source,
            "CCAeroworksPonderPlugin is not registered during client setup")
    require('"computer_control_desk"' in plugin_source and '"advanced_computer_control_desk"' in plugin_source,
            "Both Computer Control Desk variants must share the Ponder scene")
    require("ComputerControlDeskScenes::overview" in plugin_source,
            "Ponder plugin does not reference the Computer Control Desk scene")
    require(scene_source.count("showText(") == 8,
            "Ponder scene text count must match text_1 through text_8")

    structure = PONDER_STRUCTURE.read_bytes()
    require(len(structure) > 256, "Ponder structure is unexpectedly small")
    require(structure[:1] == b"\x0a", "Ponder structure must be an uncompressed root TAG_Compound")
    require(b"cc_aeroworks:computer_control_desk" in structure,
            "Ponder structure does not contain the Computer Control Desk")
    require(b"aeroworks:control_desk" in structure,
            "Ponder structure does not contain normal Aeroworks desks")

    require(WIKI_CONTROLS.is_file(), "wiki/Bedienung.md is missing")
    require("[[Bedienung]]" in WIKI_SIDEBAR.read_text(encoding="utf-8"),
            "Wiki sidebar does not link the central controls page")

    print(
        "Validated guide translations, 8 fallback pages, 8 Ponder steps, "
        "both Computer Control Desk variants, structure NBT and wiki navigation."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
