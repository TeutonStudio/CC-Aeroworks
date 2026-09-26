#!/usr/bin/env python3
"""Verify the exact Drive By Wire runtime contract used by CC-Aeroworks."""

from __future__ import annotations

import hashlib
import json
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "libs/dependencies.json").read_text())
DEPENDENCY = next(item for item in MANIFEST["dependencies"] if item["modId"] == "drivebywire")
VERSION = DEPENDENCY["version"]
FILE_NAME = DEPENDENCY["filenameExamples"][0]
DOWNLOAD_URL = DEPENDENCY["downloadUrl"]
EXPECTED_SHA256 = DEPENDENCY["sha256"]

CLIENT = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler"
MANAGER = "edn.stratodonut.drivebywire.wire.WireNetworkManager"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def download(path: Path) -> None:
    request = urllib.request.Request(
        DOWNLOAD_URL,
        headers={"User-Agent": "CC-Aeroworks-bytecode-verifier/1.0"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        require(response.status == 200, f"Drive By Wire download returned HTTP {response.status}")
        path.write_bytes(response.read())
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    require(actual == EXPECTED_SHA256, f"Unexpected SHA-256 for {FILE_NAME}: {actual}")


def javap(jar: Path, class_name: str) -> str:
    completed = subprocess.run(
        ["javap", "-classpath", str(jar), "-p", "-s", class_name],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(completed.returncode == 0, f"javap failed for {class_name}: {completed.stderr.strip()}")
    return completed.stdout


def require_contract(output: str, declarations: tuple[tuple[str, str], ...]) -> None:
    for declaration, descriptor in declarations:
        require(declaration in output, f"Missing bytecode declaration: {declaration}")
        require(descriptor in output, f"Missing bytecode descriptor for {declaration}: {descriptor}")


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cc-aeroworks-drivebywire-") as directory:
        jar = Path(directory) / FILE_NAME
        download(jar)
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            require("META-INF/neoforge.mods.toml" in names, "Drive By Wire metadata is missing")
            metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
            require(f'version="{VERSION}"' in metadata, f"Drive By Wire metadata is not version {VERSION}")
            for class_name in (CLIENT, MANAGER):
                require(class_name.replace(".", "/") + ".class" in names, f"Missing runtime class {class_name}")

        client = javap(jar, CLIENT)
        require_contract(
            client,
            (
                ("private static net.minecraft.core.BlockPos selectedSource;", "descriptor: Lnet/minecraft/core/BlockPos;"),
                ("private static java.lang.String currentChannel;", "descriptor: Ljava/lang/String;"),
                ("public static void clearSource();", "descriptor: ()V"),
                ("private static void handleWireUse(net.minecraft.world.entity.player.Player, net.minecraft.world.item.ItemStack, net.minecraft.world.level.Level, net.minecraft.core.BlockPos, net.minecraft.core.Direction);", "descriptor: (Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V"),
                ("private static void syncManager();", "descriptor: ()V"),
                ("private static void changeChannel(net.minecraft.world.level.block.Block, boolean);", "descriptor: (Lnet/minecraft/world/level/block/Block;Z)V"),
                ("private static void drawOutline(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, int);", "descriptor: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)V"),
            ),
        )

        manager = javap(jar, MANAGER)
        require_contract(
            manager,
            (
                ("public static edn.stratodonut.drivebywire.wire.WireNetworkManager$ConnectionResult createConnection", "descriptor: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Ljava/lang/String;)Ledn/stratodonut/drivebywire/wire/WireNetworkManager$ConnectionResult;"),
                ("public static boolean removeConnection", "descriptor: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Ljava/lang/String;)Z"),
                ("public static void trySetSignalAt", "descriptor: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/lang/String;I)V"),
                ("public void clearSourceSignals", "descriptor: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"),
                ("public java.util.Map<java.lang.Long, java.util.Map<java.lang.String, java.util.Set<edn.stratodonut.drivebywire.wire.graph.WireNetworkNode$WireNetworkSink>>> getNetwork();", "descriptor: ()Ljava/util/Map;"),
                ("private int getCurrentSignal", "descriptor: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/lang/String;)I"),
            ),
        )

    print(f"Validated exact Drive By Wire {VERSION}: client mixin targets and wire-network API descriptors are compatible.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, subprocess.SubprocessError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
