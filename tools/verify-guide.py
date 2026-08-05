#!/usr/bin/env python3
"""Validate guide, translations, item orientation, Ponder and wiki entry points."""

from __future__ import annotations

import gzip
import io
import json
import re
import struct
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = ROOT / "src/main/resources/assets/cc_aeroworks/lang"
AEROWORKS_LANG_DIR = ROOT / "src/main/resources/assets/aeroworks/lang"
GUIDE_CONTENT = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/GuideBookContent.kt"
CLIENT_ENTRY = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt"
ITEM_ORIENTATION = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/client/ControlDeskItemOrientation.kt"
PONDER_PLUGIN = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/CCAeroworksPonderPlugin.java"
PONDER_SCENE = ROOT / "src/main/java/de/teutonstudio/ccaeroworks/client/ponder/ComputerControlDeskScenes.java"
PONDER_STRUCTURE = ROOT / "src/main/resources/assets/cc_aeroworks/ponder/computer_control_desk.nbt"
WIKI_CONTROLS = ROOT / "wiki/Bedienung.md"
WIKI_SIDEBAR = ROOT / "wiki/_Sidebar.md"
FORMAT_PLACEHOLDER = re.compile(r"%\d+\$[a-zA-Z]")

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12
MINECRAFT_1_21_1_DATA_VERSION = 3955
AEROWORKS_1_3_0_LANGUAGE_KEY_COUNT = 260


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


class NbtReader:
    """Small strict reader for the Minecraft structure tags used by this repository."""

    def __init__(self, data: bytes) -> None:
        self.stream = io.BytesIO(data)

    def read_root(self) -> dict[str, Any]:
        tag_type = self.read_u8()
        require(tag_type == TAG_COMPOUND, "Ponder structure root must be TAG_Compound")
        self.read_utf()
        root = self.read_payload(tag_type)
        require(isinstance(root, dict), "Ponder structure root payload must be a compound")
        require(self.stream.read(1) == b"", "Ponder structure contains trailing bytes")
        return root

    def read_exact(self, length: int) -> bytes:
        require(length >= 0, "NBT length must not be negative")
        value = self.stream.read(length)
        if len(value) != length:
            raise EOFError("Unexpected end of NBT data")
        return value

    def unpack(self, format_string: str) -> Any:
        size = struct.calcsize(format_string)
        return struct.unpack(format_string, self.read_exact(size))[0]

    def read_u8(self) -> int:
        return self.unpack(">B")

    def read_i32(self) -> int:
        return self.unpack(">i")

    def read_utf(self) -> str:
        length = self.unpack(">H")
        return self.read_exact(length).decode("utf-8")

    def read_payload(self, tag_type: int) -> Any:
        if tag_type == TAG_BYTE:
            return self.unpack(">b")
        if tag_type == TAG_SHORT:
            return self.unpack(">h")
        if tag_type == TAG_INT:
            return self.read_i32()
        if tag_type == TAG_LONG:
            return self.unpack(">q")
        if tag_type == TAG_FLOAT:
            return self.unpack(">f")
        if tag_type == TAG_DOUBLE:
            return self.unpack(">d")
        if tag_type == TAG_BYTE_ARRAY:
            return self.read_exact(self.read_i32())
        if tag_type == TAG_STRING:
            return self.read_utf()
        if tag_type == TAG_LIST:
            element_type = self.read_u8()
            length = self.read_i32()
            require(length >= 0, "NBT list length must not be negative")
            return [self.read_payload(element_type) for _ in range(length)]
        if tag_type == TAG_COMPOUND:
            result: dict[str, Any] = {}
            while True:
                child_type = self.read_u8()
                if child_type == TAG_END:
                    return result
                name = self.read_utf()
                require(name not in result, f"Duplicate NBT compound key: {name}")
                result[name] = self.read_payload(child_type)
        if tag_type == TAG_INT_ARRAY:
            length = self.read_i32()
            require(length >= 0, "NBT int array length must not be negative")
            return [self.read_i32() for _ in range(length)]
        if tag_type == TAG_LONG_ARRAY:
            length = self.read_i32()
            require(length >= 0, "NBT long array length must not be negative")
            return [self.unpack(">q") for _ in range(length)]
        raise ValueError(f"Unsupported NBT tag type: {tag_type}")


def load_language(directory: Path, name: str) -> dict[str, str]:
    path = directory / name
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{path} must contain a JSON object")
    require(
        all(isinstance(key, str) and isinstance(value, str) for key, value in data.items()),
        f"{path} must contain string keys and values",
    )
    return data


def verify_language_pair(directory: Path, label: str) -> tuple[dict[str, str], dict[str, str]]:
    german = load_language(directory, "de_de.json")
    english = load_language(directory, "en_us.json")
    require(
        set(german) == set(english),
        f"{label} de_de.json and en_us.json must expose the same keys",
    )
    for key in sorted(english):
        english_placeholders = sorted(FORMAT_PLACEHOLDER.findall(english[key]))
        german_placeholders = sorted(FORMAT_PLACEHOLDER.findall(german[key]))
        require(
            english_placeholders == german_placeholders,
            f"{label} placeholder mismatch for {key}: "
            f"{english_placeholders} != {german_placeholders}",
        )
    return german, english


def verify_item_orientation() -> None:
    source = ITEM_ORIENTATION.read_text(encoding="utf-8")
    require(
        "Axis.YP.rotationDegrees(180.0F)" in source,
        "Computer Control Desk item models must rotate 180 degrees around their vertical axis",
    )
    expected_contexts = (
        "ItemDisplayContext.GUI",
        "ItemDisplayContext.FIRST_PERSON_LEFT_HAND",
        "ItemDisplayContext.FIRST_PERSON_RIGHT_HAND",
        "ItemDisplayContext.THIRD_PERSON_LEFT_HAND",
        "ItemDisplayContext.THIRD_PERSON_RIGHT_HAND",
    )
    missing = [context for context in expected_contexts if context not in source]
    require(
        not missing,
        "Computer Control Desk item rotation is missing display contexts: " + ", ".join(missing),
    )


def verify_ponder_structure() -> None:
    compressed_structure = PONDER_STRUCTURE.read_bytes()
    require(
        compressed_structure[:2] == b"\x1f\x8b",
        "Ponder structure must be gzip-compressed Minecraft structure NBT",
    )
    structure = NbtReader(gzip.decompress(compressed_structure)).read_root()

    require(
        structure.get("DataVersion") == MINECRAFT_1_21_1_DATA_VERSION,
        "Ponder structure DataVersion must match Minecraft 1.21.1",
    )
    require(structure.get("size") == [5, 3, 5], "Ponder structure size must be 5x3x5")
    palette = structure.get("palette")
    require(isinstance(palette, list) and len(palette) == 3, "Ponder palette must contain three states")
    expected_names = [
        "minecraft:smooth_stone",
        "aeroworks:control_desk",
        "cc_aeroworks:computer_control_desk",
    ]
    require([entry.get("Name") for entry in palette] == expected_names, "Unexpected Ponder block palette")
    require(palette[1].get("Properties") == {"facing": "north"}, "Normal desk must face north")
    require(palette[2].get("Properties") == {"facing": "north"}, "Computer desk must face north")

    blocks = structure.get("blocks")
    require(isinstance(blocks, list) and len(blocks) == 28, "Ponder structure must contain 28 blocks")
    states = {tuple(block.get("pos", [])): block.get("state") for block in blocks}
    require(len(states) == 28, "Ponder structure block positions must be unique")
    for x in range(5):
        for z in range(5):
            require(states.get((x, 0, z)) == 0, f"Missing base plate block at {x},0,{z}")
    require(states.get((1, 1, 2)) == 2, "Computer Control Desk is missing from the scene")
    require(states.get((2, 1, 2)) == 1, "First normal Aeroworks desk is missing from the scene")
    require(states.get((3, 1, 2)) == 1, "Second normal Aeroworks desk is missing from the scene")
    require(structure.get("entities") == [], "Ponder structure must not contain entities")


def main() -> int:
    german, _ = verify_language_pair(LANG_DIR, "CC-Aeroworks")
    aeroworks_german, aeroworks_english = verify_language_pair(AEROWORKS_LANG_DIR, "Aeroworks")
    require(
        len(aeroworks_english) == AEROWORKS_1_3_0_LANGUAGE_KEY_COUNT,
        "Aeroworks language overrides must cover the complete 1.3.0 key set",
    )
    for key in (
        "block.aeroworks.control_desk",
        "gui.aeroworks.module.config.title",
        "aeroworks.ponder.console_control.header",
        "message.aeroworks.console.entered_mouse",
    ):
        require(key in aeroworks_german, f"Missing representative Aeroworks translation: {key}")

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
    require(
        "PonderIndex.addPlugin(CCAeroworksPonderPlugin())" in client_source,
        "CCAeroworksPonderPlugin is not registered during client setup",
    )
    require(
        '"computer_control_desk"' in plugin_source and '"advanced_computer_control_desk"' in plugin_source,
        "Both Computer Control Desk variants must share the Ponder scene",
    )
    require(
        "ComputerControlDeskScenes::overview" in plugin_source,
        "Ponder plugin does not reference the Computer Control Desk scene",
    )
    require(
        scene_source.count("showText(") == 8,
        "Ponder scene text count must match text_1 through text_8",
    )
    verify_item_orientation()
    verify_ponder_structure()

    require(WIKI_CONTROLS.is_file(), "wiki/Bedienung.md is missing")
    require(
        "[[Bedienung]]" in WIKI_SIDEBAR.read_text(encoding="utf-8"),
        "Wiki sidebar does not link the central controls page",
    )

    print(
        "Validated CC-Aeroworks and complete Aeroworks 1.3.0 German/English languages, "
        "item display contexts, 8 fallback pages, 8 Ponder steps, both Computer Control Desk "
        "variants, parsed structure NBT and wiki navigation."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        AssertionError,
        EOFError,
        OSError,
        UnicodeDecodeError,
        ValueError,
        gzip.BadGzipFile,
        json.JSONDecodeError,
        struct.error,
    ) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
