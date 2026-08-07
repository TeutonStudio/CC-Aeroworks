#!/usr/bin/env python3
"""Verify the exact Create: Radars 0.4.9.4-1.21.1 runtime bytecode contract.

This deliberately downloads the public release artifact instead of trusting a
source branch, later release, or decompiler memory. It verifies only stable
native extension points used by CC-Aeroworks.
"""

from __future__ import annotations

import re
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

FILE_ID = 8_227_753
FILE_NAME = "create_radar-0.4.9.4-1.21.1.jar"
DOWNLOAD_URL = (
    "https://mediafilez.forgecdn.net/files/8227/753/"
    "create_radar-0.4.9.4-1.21.1.jar"
)
EXPECTED_MIN_BYTES = 2_800_000
EXPECTED_MAX_BYTES = 3_100_000

DATA_LINK_ITEM = "com.happysg.radar.block.datalink.DataLinkBlockItem"
NETWORK_DATA = "com.happysg.radar.block.behavior.networks.NetworkData"
MONITOR = "com.happysg.radar.block.monitor.MonitorBlockEntity"
DATA_LINK_BLOCK = "com.happysg.radar.block.datalink.DataLinkBlock"
DATA_LINK_ENTITY = "com.happysg.radar.block.datalink.DataLinkBlockEntity"
RADAR_TRACK = "com.happysg.radar.block.radar.track.RadarTrack"
IRADAR = "com.happysg.radar.block.radar.behavior.IRadar"
DETECTION_CONFIG = "com.happysg.radar.block.behavior.networks.config.DetectionConfig"
PHYSICS_HANDLER = "com.happysg.radar.compat.vs2.PhysicsHandler"

TARGET_DESCRIPTOR = (
    "(Lnet/minecraft/world/level/block/entity/BlockEntity;"
    "Lnet/minecraft/world/level/block/state/BlockState;)"
    "Lcom/happysg/radar/block/datalink/DataLinkBlockItem$FilterTarget;"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def download(path: Path) -> None:
    request = urllib.request.Request(
        DOWNLOAD_URL,
        headers={"User-Agent": "CC-Aeroworks-bytecode-verifier/1.0"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        require(response.status == 200, f"Create: Radars download returned HTTP {response.status}")
        path.write_bytes(response.read())
    size = path.stat().st_size
    require(
        EXPECTED_MIN_BYTES <= size <= EXPECTED_MAX_BYTES,
        f"Unexpected {FILE_NAME} size {size} for CurseForge file {FILE_ID}",
    )


def javap(jar: Path, class_name: str) -> str:
    completed = subprocess.run(
        ["javap", "-classpath", str(jar), "-p", "-s", "-c", class_name],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(
        completed.returncode == 0,
        f"javap failed for {class_name}: {completed.stderr.strip()}",
    )
    return completed.stdout


def class_entry(class_name: str) -> str:
    return class_name.replace(".", "/") + ".class"


def resolve_simple_class(names: set[str], simple_name: str) -> str:
    suffix = f"/{simple_name}.class"
    matches = sorted(name for name in names if name.endswith(suffix) and "$" not in name)
    require(
        len(matches) == 1,
        f"Expected exactly one {simple_name} in exact runtime JAR, found {matches}",
    )
    return matches[0][:-6].replace("/", ".")


def readable_strings(data: bytes) -> set[str]:
    return {
        match.decode("utf-8", errors="ignore")
        for match in re.findall(rb"[A-Za-z][A-Za-z0-9_:$./+ -]{3,}", data)
    }


def is_method_declaration(line: str) -> bool:
    indentation = len(line) - len(line.lstrip(" "))
    stripped = line.strip()
    return indentation == 2 and stripped.endswith(";") and "(" in stripped


def method_section(output: str, method_name: str, descriptor: str | None = None) -> str:
    lines = output.splitlines()
    candidates: list[str] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not is_method_declaration(line) or f" {method_name}(" not in stripped:
            continue
        end = index + 1
        while end < len(lines):
            if end > index + 1 and is_method_declaration(lines[end]):
                break
            end += 1
        section = "\n".join(lines[index:end])
        if descriptor is None or f"descriptor: {descriptor}" in section:
            candidates.append(section)
    require(candidates, f"Missing method {method_name} {descriptor or ''}".strip())
    require(len(candidates) == 1, f"Ambiguous method {method_name}: {len(candidates)} matches")
    return candidates[0]


def verify_data_link_item(output: str, class_strings: set[str]) -> None:
    target = method_section(output, "getFilterTarget", TARGET_DESCRIPTOR)
    monitor_instruction = re.findall(
        r"instanceof\s+#[0-9]+\s+// class com/happysg/radar/block/monitor/MonitorBlockEntity",
        target,
    )
    require(
        len(monitor_instruction) == 1,
        "getFilterTarget must contain exactly one native MonitorBlockEntity INSTANCEOF",
    )

    selection_strings = sorted(
        value
        for value in class_strings
        if "select" in value.lower() or "filterer" in value.lower()
    )
    print("Exact DataLinkBlockItem selection strings: " + ", ".join(selection_strings))
    require("SelectedFiltererPos" in selection_strings, "Exact runtime Data Link key SelectedFiltererPos is missing")
    for cleared_key in ("SelectedMountPos", "SelectedYawPos", "SelectedPitchPos", "SelectedFiringPos"):
        require(cleared_key in selection_strings, f"Exact runtime Data Link cleanup key {cleared_key} is missing")

    use_on = method_section(
        output,
        "useOn",
        "(Lnet/minecraft/world/item/context/UseOnContext;)"
        "Lnet/minecraft/world/InteractionResult;",
    )
    for token in (
        "NetworkData.getOrCreateGroup",
        "NetworkData.canAttachMonitor",
        "NetworkData.attachMonitor",
        "NetworkData.addDataLinkToGroup",
        "BlockItem.useOn",
        "radarLinkRange",
    ):
        require(token in use_on, f"Native DataLinkBlockItem.useOn contract missing {token}")


def verify_network_data(output: str) -> None:
    for name in (
        "getOrCreateGroup",
        "canAttachMonitor",
        "attachMonitor",
        "addDataLinkToGroup",
        "getFiltererForEndpoint",
        "getGroup",
        "removeDataLinkAndCleanup",
        "onEndpointRemoved",
    ):
        method_section(output, name)


def verify_monitor(output: str) -> None:
    for token in (
        "getFiltererForEndpoint",
        "getGroup",
        "DetectionConfig.fromTag",
        "getTracks",
        "java/util/stream/Stream.filter",
        "radarPos",
        "selectedTargetId",
        "getGameTime",
        "lrem",
    ):
        require(token in output, f"MonitorBlockEntity bytecode missing {token}")
    require(
        "long 5l" in output.lower(),
        "MonitorBlockEntity has no visible long five-tick divisor",
    )


def verify_cleanup(output: str) -> None:
    on_remove = method_section(output, "onRemove")
    require("removeDataLinkAndCleanup" in on_remove, "DataLinkBlock.onRemove omits native data-link cleanup")
    require("onEndpointRemoved" in on_remove, "DataLinkBlock.onRemove omits endpoint cleanup")


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cc-aeroworks-radar-bytecode-") as directory:
        jar = Path(directory) / FILE_NAME
        download(jar)

        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            for class_name in (
                DATA_LINK_ITEM,
                NETWORK_DATA,
                MONITOR,
                DATA_LINK_BLOCK,
                DATA_LINK_ENTITY,
                RADAR_TRACK,
                IRADAR,
                DETECTION_CONFIG,
                PHYSICS_HANDLER,
            ):
                require(class_entry(class_name) in names, f"Exact runtime JAR lacks {class_name}")
            filterer_class = resolve_simple_class(names, "NetworkFiltererBlockEntity")
            data_link_strings = readable_strings(archive.read(class_entry(DATA_LINK_ITEM)))

        print(f"Exact runtime NetworkFiltererBlockEntity: {filterer_class}")
        verify_data_link_item(javap(jar, DATA_LINK_ITEM), data_link_strings)
        verify_network_data(javap(jar, NETWORK_DATA))
        verify_monitor(javap(jar, MONITOR))
        verify_cleanup(javap(jar, DATA_LINK_BLOCK))

        filterer = javap(jar, filterer_class)
        for token in ("detectionTag", "radarPos", "selectedTargetId"):
            require(token in filterer, f"NetworkFiltererBlockEntity bytecode missing {token}")

        detection = javap(jar, DETECTION_CONFIG)
        method_section(detection, "fromTag")
        method_section(
            detection,
            "test",
            "(Lcom/happysg/radar/block/radar/track/RadarTrack;)Z",
        )

        radar_track = javap(jar, RADAR_TRACK)
        for method in ("getId", "getPosition", "getVelocity", "getTrackCategory"):
            method_section(radar_track, method)

        iradar = javap(jar, IRADAR)
        for method in ("getTracks", "getRange", "isRunning", "getWorldPos"):
            method_section(iradar, method)

        physics = javap(jar, PHYSICS_HANDLER)
        method_section(physics, "getWorldVec")

    print(
        f"Validated exact CurseForge file {FILE_ID} ({FILE_NAME}): native monitor target descriptor, "
        "single monitor INSTANCEOF, original Data Link registration, NetworkData endpoint APIs, "
        "five-tick monitor state, DetectionConfig filtering, RadarTrack accessors, PhysicsHandler world center, "
        "and physical-link cleanup."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
