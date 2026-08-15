#!/usr/bin/env python3
"""Inspect and verify the exact Aeroworks 1.3.0 Drive By Wire integration contract."""

from __future__ import annotations

import json
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

VERSION = "1.3.0"
FILE_NAME = "aeroworks-1.3.0.jar"
DOWNLOAD_URL = "https://cdn.modrinth.com/data/P26k79kP/versions/EYVmBa7H/aeroworks-1.3.0.jar"
EXPECTED_MIN_BYTES = 650_000
EXPECTED_MAX_BYTES = 700_000
MIXIN_CONFIG = "aeroworks-drivebywire.mixins.json"
DBW_CLIENT = "edn/stratodonut/drivebywire/client/ClientWireNetworkHandler"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def download(path: Path) -> None:
    request = urllib.request.Request(
        DOWNLOAD_URL,
        headers={"User-Agent": "CC-Aeroworks-bytecode-verifier/1.0"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        require(response.status == 200, f"Aeroworks download returned HTTP {response.status}")
        path.write_bytes(response.read())
    require(
        EXPECTED_MIN_BYTES <= path.stat().st_size <= EXPECTED_MAX_BYTES,
        f"Unexpected {FILE_NAME} size {path.stat().st_size}",
    )


def javap(jar: Path, class_name: str) -> str:
    completed = subprocess.run(
        ["javap", "-classpath", str(jar), "-p", "-s", "-c", class_name],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(completed.returncode == 0, f"javap failed for {class_name}: {completed.stderr.strip()}")
    return completed.stdout


def class_names(config: dict[str, object]) -> list[str]:
    package = str(config.get("package", "")).strip(".")
    names: list[str] = []
    for key in ("mixins", "client", "server"):
        values = config.get(key, [])
        if not isinstance(values, list):
            continue
        for value in values:
            name = str(value)
            names.append(f"{package}.{name}" if package else name)
    return names


def interesting(output: str) -> list[str]:
    needles = (
        "ClientWireNetworkHandler",
        "selectedSource",
        "currentChannel",
        "changeChannel",
        "handleWireUse",
        "ConsoleBlockEntity",
        "MountedModule",
        "channel",
        "wire$",
    )
    return [line for line in output.splitlines() if any(needle in line for needle in needles)]


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cc-aeroworks-aeroworks-dbw-") as directory:
        jar = Path(directory) / FILE_NAME
        download(jar)
        with zipfile.ZipFile(jar) as archive:
            require(MIXIN_CONFIG in archive.namelist(), f"{FILE_NAME} lacks {MIXIN_CONFIG}")
            config = json.loads(archive.read(MIXIN_CONFIG).decode("utf-8"))
            names = class_names(config)
            require(names, "Aeroworks DBW mixin config contains no mixins")
            print("Aeroworks DBW mixin config: " + json.dumps(config, sort_keys=True))

        outputs: dict[str, str] = {}
        for name in names:
            output = javap(jar, name)
            outputs[name] = output
            lines = interesting(output)
            print(f"--- {name} ---")
            print("\n".join(lines[:240]))

        client_mixins = {
            name: output
            for name, output in outputs.items()
            if DBW_CLIENT in output or "ClientWireNetworkHandler" in output
        }
        require(client_mixins, "Aeroworks DBW integration no longer targets ClientWireNetworkHandler")
        combined = "\n".join(client_mixins.values())
        require("selectedSource" in combined, "Aeroworks DBW client integration no longer reads selectedSource")
        require("currentChannel" in combined, "Aeroworks DBW client integration no longer handles currentChannel")
        require(
            "ConsoleBlockEntity" in combined or "MountedModule" in combined,
            "Aeroworks DBW channel selection no longer appears to inspect modular desk state",
        )

    print(
        f"Validated exact Aeroworks {VERSION} release: optional DBW mixins still integrate modular ControlDesk state with ClientWireNetworkHandler."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, subprocess.SubprocessError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
