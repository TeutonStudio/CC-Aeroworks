#!/usr/bin/env python3
"""Validate release-branch documentation and refactor hygiene."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


index = (ROOT / "README.md").read_text(encoding="utf-8")
german = (ROOT / "docs/overview-de.md").read_text(encoding="utf-8")
english = (ROOT / "docs/overview-en.md").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/verify.yml").read_text(encoding="utf-8")

require(len(index.split()) < 130, "README must remain a concise English introduction")
require("/wiki/English" in index and "/wiki)" in index, "README must link both Wiki languages")
require("Sprache / Language" not in index, "README must not remain a language index")
for name, content in (("German technical overview", german), ("English technical overview", english)):
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

print("Validated concise README and bilingual technical overviews, removed-monolith verifier hygiene and release-branch CI gates.")
