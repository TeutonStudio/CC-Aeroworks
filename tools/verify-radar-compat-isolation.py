#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main"
BASES = [SRC / "kotlin/de/teutonstudio/ccaeroworks", SRC / "java/de/teutonstudio/ccaeroworks"]
FORBIDDEN = (
    "de.teutonstudio.ccaeroworks.radarcompat",
    "com.happysg.radar",
    "CreateRadarCompat",
    "RadarOverlayRenderer",
    "RadarSourceRegistry",
    "RadarDisplaySnapshot",
    "RadarDisplayType",
    "SetRadarDisplaySourcePayload",
)
STALE_COMPAT_IMPORTS = (
    "de.teutonstudio.ccaeroworks.compat.createradar",
    "de.teutonstudio.ccaeroworks.display.Radar",
)

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

def main() -> int:
    leaks = []
    for base in BASES:
        for path in list(base.rglob("*.kt")) + list(base.rglob("*.java")):
            if "radarcompat" in path.parts:
                continue
            text = path.read_text(encoding="utf-8")
            for token in FORBIDDEN:
                if token in text:
                    leaks.append(f"{path.relative_to(ROOT)}: {token}")
    require(not leaks, "Core -> radarcompat leaks:\n" + "\n".join(leaks))

    stale = []
    for base in BASES:
        compat_root = base / "radarcompat"
        if not compat_root.exists():
            continue
        for path in list(compat_root.rglob("*.kt")) + list(compat_root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            for token in STALE_COMPAT_IMPORTS:
                if token in text:
                    stale.append(f"{path.relative_to(ROOT)}: {token}")
    require(not stale, "Stale pre-refactor radar imports:\n" + "\n".join(stale))

    compat = ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat"
    require((compat / "RadarCompat.kt").exists(), "RadarCompat entrypoint missing")
    require((compat / "registry/RadarItems.kt").exists(), "Radar item registry missing")

    module_types = (compat / "registry/RadarModuleTypes.kt").read_text(encoding="utf-8")
    require(
        "import de.teutonstudio.ccaeroworks.registry.CCModuleTypes" in module_types,
        "RadarModuleTypes must depend on the generic core module registry",
    )
    require(
        "import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarModuleTypes" not in module_types,
        "RadarModuleTypes must not import itself",
    )

    metadata = (ROOT / "src/main/templates/META-INF/neoforge.mods.toml").read_text(encoding="utf-8")
    require('modId="${radarcompat_mod_id}"' in metadata, "separate radar compat mod metadata missing")
    require('config="cc_aeroworks_radarcompat.mixins.json"' in metadata, "radar mixin config missing")
    core_items = (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCItems.kt").read_text(encoding="utf-8")
    require("RADAR_DISPLAY" not in core_items, "core item registry still owns radar items")
    print("Validated one-way core <- radarcompat architecture, package moves, and separate NeoForge mod metadata.")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
