#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

VERSION_URL = "https://cdn.modrinth.com/data/P26k79kP/versions/EYVmBa7H/aeroworks-1.3.0.jar"
EXPECTED_SHA256 = "f836748d2bbad5b60fffef559418b74688621bf8e77710e6e0d5437c56ed2c78"
TARGET = b"com/mred231/aeroworks/content/controls/OrientedBoxOutline"


def javap(jar: Path, class_name: str) -> str:
    completed = subprocess.run(
        ["javap", "-classpath", str(jar), "-p", "-c", "-s", class_name],
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="ccaeroworks-aeroworks-") as tmp:
        jar = Path(tmp) / "aeroworks-1.3.0.jar"
        with urllib.request.urlopen(VERSION_URL, timeout=60) as response:
            jar.write_bytes(response.read())

        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        print(f"Aeroworks SHA-256: {digest}")
        if digest != EXPECTED_SHA256:
            raise SystemExit(f"unexpected Aeroworks artifact: {digest}")

        references: list[str] = []
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if not name.endswith(".class"):
                    continue
                data = archive.read(name)
                if TARGET in data:
                    references.append(name[:-6].replace("/", "."))

        print("\n=== Classes referencing OrientedBoxOutline ===")
        for class_name in references:
            print(class_name)

        classes = [
            "com.mred231.aeroworks.content.controls.OrientedBoxOutline",
            "com.mred231.aeroworks.content.controls.ConsoleBlockEntity",
            *references,
        ]
        seen: set[str] = set()
        for class_name in classes:
            if class_name in seen:
                continue
            seen.add(class_name)
            print(f"\n=== javap {class_name} ===")
            print(javap(jar, class_name))


if __name__ == "__main__":
    main()
