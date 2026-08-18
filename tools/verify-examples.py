#!/usr/bin/env python3
"""Validate shipped CC:Tweaked examples and their documentation links."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLE_ROOT = ROOT / "examples/cc"
ROOT_README = ROOT / "README.md"
LANGUAGE_READMES = (ROOT / "README_GER.md", ROOT / "README_ENG.md")
EXAMPLE_README = EXAMPLE_ROOT / "README.md"

REQUIRED_QUICKSTARTS = {
    "local-desk.lua",
    "network-basics.lua",
    "channels-demo.lua",
    "wires-demo.lua",
    "telemetry-read.lua",
    "control-override-demo.lua",
    "pixel-test.lua",
    "touch-test.lua",
}

REMOVED_PATHS = {
    "examples/cc/display-binding-router.lua",
    "display-binding-router.lua",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def markdown_links(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    return re.findall(r"\[[^\]]*\]\(([^)]+)\)", text)


def verify_links(path: Path) -> None:
    for target in markdown_links(path):
        clean = target.split("#", 1)[0]
        if not clean or "://" in clean or clean.startswith("mailto:"):
            continue
        resolved = (path.parent / clean).resolve()
        try:
            resolved.relative_to(ROOT)
        except ValueError:
            fail(f"{path.relative_to(ROOT)} links outside the repository: {target}")
        if not resolved.exists():
            fail(f"Broken Markdown link in {path.relative_to(ROOT)}: {target}")


def verify_example_index() -> None:
    scripts = sorted(path.name for path in EXAMPLE_ROOT.glob("*.lua"))
    if not scripts:
        fail("No CC:Tweaked Lua examples found")

    missing_quickstarts = sorted(REQUIRED_QUICKSTARTS - set(scripts))
    if missing_quickstarts:
        fail("Missing required quickstart examples: " + ", ".join(missing_quickstarts))

    index = EXAMPLE_README.read_text(encoding="utf-8")
    undocumented = [name for name in scripts if f"]({name})" not in index]
    if undocumented:
        fail("Lua examples missing from examples/cc/README.md: " + ", ".join(undocumented))


def verify_readmes() -> None:
    index = ROOT_README.read_text(encoding="utf-8")
    if "README_GER.md" not in index or "README_ENG.md" not in index:
        fail("README.md must be the language index for README_GER.md and README_ENG.md")
    if index.index("README_GER.md") > index.index("README_ENG.md"):
        fail("README.md must list German before English")
    if "```lua" in index.lower():
        fail("README.md must remain a lightweight language index")

    for path in LANGUAGE_READMES:
        if not path.exists():
            fail(f"Missing language README: {path.name}")

    documentation = "\n".join(path.read_text(encoding="utf-8") for path in LANGUAGE_READMES)
    required_links = {
        "examples/cc/README.md",
        "examples/cc/local-desk.lua",
        "examples/cc/network-basics.lua",
        "examples/cc/channels-demo.lua",
        "examples/cc/wires-demo.lua",
        "examples/cc/telemetry-read.lua",
        "examples/cc/control-override-demo.lua",
        "examples/cc/pixel-test.lua",
        "examples/cc/touch-test.lua",
    }
    missing = sorted(link for link in required_links if link not in documentation)
    if missing:
        fail("Language READMEs are missing example links: " + ", ".join(missing))


def verify_removed_and_deprecated_content() -> None:
    documents = [ROOT_README, *LANGUAGE_READMES, EXAMPLE_README]
    documents.extend(sorted(EXAMPLE_ROOT.glob("*.lua")))

    for path in documents:
        text = path.read_text(encoding="utf-8")
        for removed in REMOVED_PATHS:
            if removed in text:
                fail(f"{path.relative_to(ROOT)} still references removed example {removed}")

        if path.suffix == ".lua" and re.search(r"(?<![A-Za-z0-9_])aeroworks\.", text):
            fail(f"{path.relative_to(ROOT)} uses the removed global aeroworks API")


def main() -> int:
    verify_example_index()
    verify_readmes()
    for path in (ROOT_README, *LANGUAGE_READMES, EXAMPLE_README):
        verify_links(path)
    verify_removed_and_deprecated_content()
    print("Example documentation verification passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        raise SystemExit(1)
