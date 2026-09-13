#!/usr/bin/env python3
"""Verify the exact Aeroworks Drive By Wire channel integration contract."""
from __future__ import annotations
import hashlib, json, subprocess, tempfile, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "libs/dependencies.json").read_text())
DEPENDENCY = next(item for item in MANIFEST["dependencies"] if item["modId"] == "aeroworks")
VERSION = DEPENDENCY["version"]
FILE_NAME = DEPENDENCY["filenameExamples"][0]
DOWNLOAD_URL = DEPENDENCY["downloadUrl"]
EXPECTED_SHA256 = DEPENDENCY["sha256"]
MIXIN_CONFIG = "aeroworks-drivebywire.mixins.json"
DBW_CLIENT = "edn/stratodonut/drivebywire/client/ClientWireNetworkHandler"
CONSOLE_WIRE_CHANNELS = "com.mred231.aeroworks.compat.drivebywire.ConsoleWireChannels"

def require(condition: bool, message: str) -> None:
    if not condition: raise AssertionError(message)

def download(path: Path) -> None:
    request = urllib.request.Request(DOWNLOAD_URL, headers={"User-Agent": "CC-Aeroworks-bytecode-verifier/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        require(response.status == 200, f"Aeroworks download returned HTTP {response.status}")
        path.write_bytes(response.read())
    require(hashlib.sha256(path.read_bytes()).hexdigest() == EXPECTED_SHA256, f"Unexpected SHA-256 for {FILE_NAME}")

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
        combined = "\n".join(client_mixins.values())
        require("selectedSource" in combined and "currentChannel" in combined, "Aeroworks DBW client integration no longer owns selected source/channel state")
        require("ConsoleWireChannels.nextChannel" in combined, "Aeroworks DBW client integration no longer delegates modular cycling to ConsoleWireChannels")

        channels = javap(jar, CONSOLE_WIRE_CHANNELS)
        for signature in (
            "channelsFor(com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity)",
            "nextChannel(com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity, java.lang.String, boolean)",
            "parse(com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity, java.lang.String)",
        ):
            require(signature in channels, f"ConsoleWireChannels contract missing {signature}")
        for token in (
            "ControlChannel.kind", "ControlChannel.id", "iconst_1", "iconst_m1",
            "ConsoleWireChannels$WireChannel.socket", "ConsoleWireChannels$WireChannel.channelId", "ConsoleWireChannels$WireChannel.sign",
        ):
            require(token in channels, f"ConsoleWireChannels bytecode missing {token}")

    print(f"Validated exact Aeroworks {VERSION}: ConsoleWireChannels preserves socket/channel/sign identity and modular client cycling.")
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except (AssertionError, OSError, subprocess.SubprocessError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}"); raise SystemExit(1)
