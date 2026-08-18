#!/usr/bin/env python3
"""Validate release-branch documentation and refactor hygiene."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


index = (ROOT / "README.md").read_text(encoding="utf-8")
german = (ROOT / "README_GER.md").read_text(encoding="utf-8")
english = (ROOT / "README_ENG.md").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/verify.yml").read_text(encoding="utf-8")

require("README_GER.md" in index and "README_ENG.md" in index, "README language index must link both language variants")
require(index.index("README_GER.md") < index.index("README_ENG.md"), "German README must be listed before English")
for name, content in (("German README", german), ("English README", english)):
    require("peripherals.find" in content, f"{name} must document the current peripherals API")
    require("aeroworks.getDesks" not in content, f"{name} still documents the removed aeroworks.getDesks API")
    require("setDeskDisplay" not in content, f"{name} still documents removed network-wide display methods")

removed_monolith = "PeripheralNetwork" + ".kt"
stale = []
for path in sorted((ROOT / "tools").glob("*.py")):
    if path.name == Path(__file__).name:
        continue
    if removed_monolith in path.read_text(encoding="utf-8"):
        stale.append(path.name)
require(not stale, "Verifier scripts still reference the removed peripheral monolith: " + ", ".join(stale))

require('      - "release/**"' in workflow, "Verify workflow must run for release branches")
require("refs/heads/release/" in workflow, "Protected full build must run on release-branch pushes")
require("github.head_ref" in workflow and "release/" in workflow,
        "Protected full build must cover same-repository release pull requests")

print("Validated bilingual README entry point, removed-monolith verifier hygiene and release-branch CI gates.")
