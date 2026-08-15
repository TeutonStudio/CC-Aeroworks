#!/usr/bin/env python3
"""Verify the exact Aeroworks 1.3.0 Drive By Wire integration contract."""
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
DBW_MANAGER = "edn/stratodonut/drivebywire/wire/WireNetworkManager"
CONSOLE_WIRE_CHANNELS = "com.mred231.aeroworks.compat.drivebywire.ConsoleWireChannels"

def require(condition: bool, message: str) -> None:
    if not condition: raise AssertionError(message)

def download(path: Path) -> None:
    request = urllib.request.Request(DOWNLOAD_URL, headers={"User-Agent": "CC-Aeroworks-bytecode-verifier/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        require(response.status == 200, f"Aeroworks download returned HTTP {response.status}")
        path.write_bytes(response.read())
    require(EXPECTED_MIN_BYTES <= path.stat().st_size <= EXPECTED_MAX_BYTES, f"Unexpected {FILE_NAME} size {path.stat().st_size}")

def javap(jar: Path, class_name: str) -> str:
    completed = subprocess.run(["javap", "-classpath", str(jar), "-p", "-s", "-c", class_name], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    require(completed.returncode == 0, f"javap failed for {class_name}: {completed.stderr.strip()}")
    return completed.stdout

def class_names(config: dict[str, object]) -> list[str]:
    package = str(config.get("package", "")).strip(".")
    names: list[str] = []
    for key in ("mixins", "client", "server"):
        values = config.get(key, [])
        if isinstance(values, list):
            for value in values:
                name = str(value); names.append(f"{package}.{name}" if package else name)
    return names

def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cc-aeroworks-aeroworks-dbw-") as directory:
        jar = Path(directory) / FILE_NAME
        download(jar)
        with zipfile.ZipFile(jar) as archive:
            require(MIXIN_CONFIG in archive.namelist(), f"{FILE_NAME} lacks {MIXIN_CONFIG}")
            config = json.loads(archive.read(MIXIN_CONFIG).decode("utf-8"))
            names = class_names(config)
            require(names, "Aeroworks DBW mixin config contains no mixins")
            require(CONSOLE_WIRE_CHANNELS.replace('.', '/') + ".class" in archive.namelist(), "Aeroworks release lacks ConsoleWireChannels")

        outputs = {name: javap(jar, name) for name in names}
        client_mixins = {name: output for name, output in outputs.items() if DBW_CLIENT in output or "ClientWireNetworkHandler" in output}
        require(client_mixins, "Aeroworks DBW integration no longer targets ClientWireNetworkHandler")
        client_combined = "\n".join(client_mixins.values())
        require("selectedSource" in client_combined and "currentChannel" in client_combined, "Aeroworks DBW client integration no longer owns selected source/channel state")
        require("ConsoleWireChannels.nextChannel" in client_combined, "Aeroworks DBW client integration no longer delegates modular cycling to ConsoleWireChannels")

        all_mixins = "\n".join(outputs.values())
        require(DBW_MANAGER in all_mixins or "WireNetworkManager" in all_mixins, "Aeroworks DBW integration no longer references WireNetworkManager")
        require("WireNetworkManager.trySetSignalAt" in all_mixins, "Aeroworks control output no longer publishes through WireNetworkManager.trySetSignalAt; update CC-Aeroworks display isolation hook")

        channels = javap(jar, CONSOLE_WIRE_CHANNELS)
        for signature in (
            "channelFor(int, java.lang.String, int)",
            "channelsFor(com.mred231.aeroworks.content.controls.ConsoleBlockEntity)",
            "nextChannel(com.mred231.aeroworks.content.controls.ConsoleBlockEntity, java.lang.String, boolean)",
            "parse(java.lang.String)",
        ):
            require(signature in channels, f"ConsoleWireChannels contract missing {signature}")
        for token in (
            "ControlChannel.kind", "ControlChannel.id", "iconst_1", "iconst_m1",
            "ConsoleWireChannels$WireChannel.socket", "ConsoleWireChannels$WireChannel.channelId", "ConsoleWireChannels$WireChannel.sign",
        ):
            require(token in channels, f"ConsoleWireChannels bytecode missing {token}")

    print(f"Validated exact Aeroworks {VERSION}: directional channel identity is stable and control values publish through WireNetworkManager.trySetSignalAt.")
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except (AssertionError, OSError, subprocess.SubprocessError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}"); raise SystemExit(1)
