#!/usr/bin/env python3
"""Validate libs/dependencies.json without configuring Gradle or resolving remote plugins."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "libs/dependencies.json"
REQUIRED_FIELDS = ("name", "modId", "version", "required", "filenamePattern", "source")
SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")


def fail(message: str) -> None:
    raise AssertionError(message)


def main() -> int:
    try:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"Could not read dependency manifest: {exc}")

    if not isinstance(manifest, dict):
        fail("libs/dependencies.json must contain a JSON object")
    if manifest.get("schemaVersion") != 1:
        fail(f"Unsupported dependency manifest schema: {manifest.get('schemaVersion')!r}")

    dependencies = manifest.get("dependencies")
    if not isinstance(dependencies, list) or not dependencies:
        fail("The dependency manifest must define at least one dependency")

    identities: set[str] = set()
    errors: list[str] = []
    for index, dependency in enumerate(dependencies):
        if not isinstance(dependency, dict):
            errors.append(f"Entry {index} is not an object")
            continue

        for key in REQUIRED_FIELDS:
            value = dependency.get(key)
            if value is None or (isinstance(value, str) and not value.strip()):
                errors.append(f"Entry {index} is missing '{key}'")
        if "required" in dependency and not isinstance(dependency.get("required"), bool):
            errors.append(f"Entry {index} field 'required' must be boolean")

        identity = f"{dependency.get('modId')}:{dependency.get('version')}"
        if identity in identities:
            errors.append(f"Duplicate dependency identity: {identity}")
        identities.add(identity)

        pattern_text = dependency.get("filenamePattern")
        pattern = None
        if isinstance(pattern_text, str):
            try:
                pattern = re.compile(pattern_text)
            except re.error as exc:
                errors.append(f"Invalid filenamePattern for {identity}: {exc}")

        examples = dependency.get("filenameExamples")
        if examples is not None:
            if not isinstance(examples, list) or not examples:
                errors.append(f"filenameExamples for {identity} must be a non-empty list when present")
            elif pattern is not None:
                for example_index, example in enumerate(examples):
                    if not isinstance(example, str) or not example.strip():
                        errors.append(
                            f"filenameExamples[{example_index}] for {identity} must be a non-empty string"
                        )
                    elif pattern.fullmatch(example) is None:
                        errors.append(
                            f"Known filename does not match filenamePattern for {identity}: {example}"
                        )

        checksum = dependency.get("sha256")
        if checksum is not None and (not isinstance(checksum, str) or SHA256.fullmatch(checksum) is None):
            errors.append(f"Invalid SHA-256 for {identity}: expected 64 hexadecimal characters")

    if errors:
        fail("Invalid local dependency manifest:\n - " + "\n - ".join(errors))

    print(f"Validated {len(dependencies)} dependency manifest entries without remote dependency resolution.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
