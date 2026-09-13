#!/usr/bin/env python3
"""Pin the Aeroworks ConsoleScreen preview method used by our client Mixin."""
from __future__ import annotations
import hashlib
import json
import subprocess
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "libs/dependencies.json").read_text())
DEPENDENCY = next(item for item in MANIFEST["dependencies"] if item["modId"] == "aeroworks")
VERSION = DEPENDENCY["version"]
FILE_NAME = DEPENDENCY["filenameExamples"][0]
DOWNLOAD_URL = DEPENDENCY["downloadUrl"]
EXPECTED_SHA256 = DEPENDENCY["sha256"]
CONSOLE_SCREEN = "com.mred231.aeroworks.content.controls.console.ConsoleScreen"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def download(path: Path) -> None:
    request = urllib.request.Request(
        DOWNLOAD_URL,
        headers={"User-Agent": "CC-Aeroworks-console-preview-verifier/1.0"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        require(response.status == 200, f"Aeroworks download returned HTTP {response.status}")
        path.write_bytes(response.read())
    require(hashlib.sha256(path.read_bytes()).hexdigest() == EXPECTED_SHA256, f"Unexpected SHA-256 for {FILE_NAME}")


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


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cc-aeroworks-console-preview-") as directory:
        jar = Path(directory) / FILE_NAME
        download(jar)
        screen = javap(jar, CONSOLE_SCREEN)

        for token in (
            "private void renderConsolePreview(net.minecraft.client.gui.GuiGraphics);",
            "descriptor: (Lnet/minecraft/client/gui/GuiGraphics;)V",
            "Method renderConsolePreview:(Lnet/minecraft/client/gui/GuiGraphics;)V",
            "float 42.0f",
            "float 30.0f",
            "float 225.0f",
            "Method net/createmod/catnip/render/CachedBuffers.block:",
            "Method com/mred231/aeroworks/content/controls/module/ModulePartRender.flatten:",
            "Method com/mred231/aeroworks/content/controls/module/ModulePartRender.displayValues:",
            "Method com/mred231/aeroworks/content/controls/module/ModulePartRender.apply:",
            "Method com/mojang/blaze3d/platform/Lighting.setupFor3DItems:()V",
            "Method net/minecraft/client/renderer/MultiBufferSource$BufferSource.endBatch:()V",
            "Field windowLeft:I",
            "Field windowTop:I",
        ):
            require(token in screen, f"Aeroworks ConsoleScreen preview bytecode missing {token}")

    print(
        f"Validated exact Aeroworks {VERSION} ConsoleScreen preview hook, native camera constants, "
        "desk/module render path and window anchors."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, subprocess.SubprocessError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
