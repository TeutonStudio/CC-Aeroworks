#!/usr/bin/env python3
"""Validate repository files that do not require proprietary mod JARs."""

from __future__ import annotations

import hashlib
import json
import re
import stat
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_PATHS = (
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/GradleBootstrap.java",
    "gradle/wrapper/gradle-wrapper.properties",
    "libs/README.md",
    "libs/dependencies.json",
    "docs/cc-peripheral-api.md",
    "docs/manual-test-plan.md",
    "examples/cc/README.md",
    "examples/cc/dashboard.lua",
    "examples/cc/input-monitor.lua",
    "examples/cc/pixel-test.lua",
    "src/test/kotlin",
)

REQUIRED_DEPENDENCY_FIELDS = {
    "name",
    "modId",
    "version",
    "required",
    "filenamePattern",
    "sha256",
    "source",
    "notes",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def load_manifest() -> dict[str, Any]:
    manifest_path = ROOT / "libs/dependencies.json"
    with manifest_path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        fail("libs/dependencies.json must contain a JSON object")
    return data


def verify_paths() -> None:
    missing = [path for path in REQUIRED_PATHS if not (ROOT / path).exists()]
    if missing:
        fail("Missing required repository paths: " + ", ".join(missing))

    if sys.platform != "win32":
        mode = (ROOT / "gradlew").stat().st_mode
        if not mode & stat.S_IXUSR:
            fail("gradlew is not executable for its owner")


def verify_manifest() -> None:
    data = load_manifest()
    if data.get("schemaVersion") != 1:
        fail("Unsupported dependency manifest schema")

    dependencies = data.get("dependencies")
    if not isinstance(dependencies, list) or not dependencies:
        fail("Dependency manifest must contain a non-empty dependencies list")

    identities: set[tuple[str, str]] = set()
    for index, dependency in enumerate(dependencies):
        if not isinstance(dependency, dict):
            fail(f"Dependency entry {index} is not an object")
        missing = REQUIRED_DEPENDENCY_FIELDS - dependency.keys()
        if missing:
            fail(f"Dependency entry {index} is missing: {sorted(missing)}")
        if not isinstance(dependency["required"], bool):
            fail(f"Dependency entry {index} has a non-boolean required flag")

        identity = (str(dependency["modId"]), str(dependency["version"]))
        if identity in identities:
            fail(f"Duplicate dependency identity: {identity[0]}:{identity[1]}")
        identities.add(identity)

        try:
            filename_pattern = re.compile(str(dependency["filenamePattern"]))
        except re.error as exception:
            fail(f"Invalid filename pattern for {identity}: {exception}")

        filename_examples = dependency.get("filenameExamples")
        if filename_examples is not None:
            if not isinstance(filename_examples, list) or not filename_examples:
                fail(f"filenameExamples for {identity[0]}:{identity[1]} must be a non-empty list")
            for example_index, example in enumerate(filename_examples):
                if not isinstance(example, str) or not example:
                    fail(
                        f"filenameExamples[{example_index}] for "
                        f"{identity[0]}:{identity[1]} must be a non-empty string"
                    )
                if filename_pattern.fullmatch(example) is None:
                    fail(
                        f"Known filename does not match filenamePattern for "
                        f"{identity[0]}:{identity[1]}: {example}"
                    )

        checksum = dependency["sha256"]
        if checksum is not None and not re.fullmatch(r"[0-9a-fA-F]{64}", str(checksum)):
            fail(f"Invalid SHA-256 for {identity[0]}:{identity[1]}")


def verify_wrapper() -> None:
    properties: dict[str, str] = {}
    for raw_line in (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            fail(f"Invalid wrapper property line: {raw_line}")
        properties[key] = value.replace("\\:", ":")

    url = properties.get("distributionUrl", "")
    checksum = properties.get("distributionSha256Sum", "")
    if not url.startswith("https://services.gradle.org/distributions/gradle-"):
        fail("Gradle distributionUrl must use the official HTTPS distribution host")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", checksum):
        fail("Gradle distributionSha256Sum must contain 64 hexadecimal characters")


def verify_no_tracked_jars() -> None:
    result = subprocess.run(
        ["git", "ls-files", "--", "*.jar", "libs/*.jar"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    tracked = [line for line in result.stdout.splitlines() if line]
    if tracked:
        fail("Third-party or generated JARs are tracked: " + ", ".join(tracked))


def verify_safe_lua_tables() -> None:
    """Keep main-thread peripheral methods away from CC:Tweaked's zero-copy table API."""
    kotlin_root = ROOT / "src/main/kotlin"
    unsafe_calls: list[str] = []
    for path in sorted(kotlin_root.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        if "getTableUnsafe(" in text or "optTableUnsafe(" in text:
            unsafe_calls.append(str(path.relative_to(ROOT)))

    if unsafe_calls:
        fail(
            "Unsafe CC:Tweaked Lua table access is forbidden in main sources; "
            "use getTable()/optTable() and ObjectLuaTable instead: "
            + ", ".join(unsafe_calls)
        )


def verify_lua_examples() -> None:
    """Keep the shipped Lua examples topology-aware and useful as regression tests."""
    root = ROOT / "examples/cc"
    scripts = {path.name: path.read_text(encoding="utf-8") for path in sorted(root.glob("*.lua"))}
    required = {"dashboard.lua", "input-monitor.lua", "pixel-test.lua"}
    missing = sorted(required - scripts.keys())
    if missing:
        fail("Missing required CC:Tweaked Lua examples: " + ", ".join(missing))

    contracts = {
        "dashboard.lua": (
            "getInputs",
            "getDisplays",
            "Select input",
            "Select display",
            "cc_aeroworks_console_input",
            "cc_aeroworks_console_changed",
            "cc_aeroworks_desk_input",
            "peripheral_detach",
            "os.pullEventRaw",
            "restoreDisplay",
        ),
        "input-monitor.lua": (
            "getInputs",
            "cc_aeroworks_console_input",
            "cc_aeroworks_console_changed",
            "cc_aeroworks_desk_input",
            "peripheral_detach",
            "os.pullEventRaw",
            "Periodic validation",
            "No numeric input channels found",
        ),
        "pixel-test.lua": (
            "getDisplays",
            "No CC-Aeroworks displays found",
            "Select display",
            "getDisplaySize",
            "setDisplayPixels",
        ),
    }
    for filename, snippets in contracts.items():
        content = scripts[filename]
        absent = [snippet for snippet in snippets if snippet not in content]
        if absent:
            fail(f"{filename} lost required robust-example contracts: {', '.join(absent)}")

    brittle_patterns = {
        'local DISPLAY_SOCKET = "big"': "hard-coded display socket selection",
        "local address, desk = next(desks)": "arbitrary first-desk selection",
        "assert(next(desks)": "first-match-only desk assumption",
    }
    for filename, content in scripts.items():
        violations = [description for pattern, description in brittle_patterns.items() if pattern in content]
        if violations:
            fail(f"{filename} contains brittle example logic: {', '.join(violations)}")


def verify_text_files() -> None:
    for relative in REQUIRED_PATHS:
        path = ROOT / relative
        if path.is_dir():
            continue
        content = path.read_bytes()
        if b"\r\n" in content and relative not in {"gradlew.bat"}:
            fail(f"Unexpected CRLF line endings in {relative}")
        try:
            content.decode("utf-8")
        except UnicodeDecodeError as exception:
            fail(f"{relative} is not valid UTF-8: {exception}")


def main() -> int:
    checks = (
        verify_paths,
        verify_manifest,
        verify_wrapper,
        verify_no_tracked_jars,
        verify_safe_lua_tables,
        verify_lua_examples,
        verify_text_files,
    )
    for check in checks:
        check()
        print(f"PASS {check.__name__}")

    digest = hashlib.sha256((ROOT / "libs/dependencies.json").read_bytes()).hexdigest()
    print(f"Repository verification passed; dependency manifest SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, subprocess.CalledProcessError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        raise SystemExit(1)
