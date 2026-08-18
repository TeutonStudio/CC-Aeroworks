#!/usr/bin/env python3
"""Validate GPS information-source discovery without coupling it to the user's Lua event stream."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

def main() -> int:
    tracker = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/GpsSourceTracker.kt")
    builder = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceSnapshotBuilder.kt")
    state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/source/InformationSourceSnapshotState.kt")
    widget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/InformationSourceManagerWidget.kt")
    mod = read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt")

    require('InformationSourceKind("gps", "GPS", 30)' in state, "information-source core kinds must include GPS")
    require("GpsSourceTracker.request(owner)" in builder and "GpsSourceTracker.current(owner)" in builder,
            "information-source snapshot must trigger and read GPS discovery")
    require("InformationSourceKinds.GPS" in builder and 'id = "gps:${owner.deskId}"' in builder,
            "GPS source projection missing stable desk-scoped id")

    require("ComputerCraftAPI.getWirelessNetwork" in tracker,
            "GPS discovery must use CC:Tweaked's authoritative wireless packet network")
    require("PeripheralCapability.get()" in tracker and "WirelessModemPeripheral" in tracker,
            "GPS discovery must require a directly detectable wireless modem")
    require("ConsoleMultiblockManager.resolve" in tracker and "targetPos in deskMembers" in tracker,
            "GPS modem discovery must exclude internal ControlDesk multiblock neighbours like the embedded computer")
    require("private const val GPS_CHANNEL = 65534" in tracker and 'private const val GPS_PING = "PING"' in tracker,
            "GPS probe must follow CC:Tweaked's GPS channel/protocol")
    require("replyChannel" in tracker and "REPLY_CHANNEL_MIN" in tracker and "Packet(GPS_CHANNEL, replyChannel, GPS_PING, probe)" in tracker,
            "GPS probe must use a private reply channel instead of consuming the user's gps.locate responses")
    require("PacketReceiver, PacketSender" in tracker or ("PacketReceiver" in tracker and "PacketSender" in tracker),
            "GPS probe must participate directly in CC:Tweaked's packet network")
    require("override fun receiveDifferentDimension(packet: Packet) = Unit" in tracker,
            "GPS probe must ignore distance-less cross-dimensional replies like gps.lua")
    require("PROBE_TIMEOUT_TICKS = 40L" in tracker and "PROBE_INTERVAL_TICKS = 100L" in tracker,
            "GPS probes must be bounded and rate-limited")
    require("READY_AFTER_TICKS = 100L" in tracker and "DROP_AFTER_TICKS = 300L" in tracker,
            "GPS source freshness contract changed")
    require("trilaterate" in tracker and "narrow" in tracker and "abs(a2bNormal.dot(a2cNormal)) > 0.999" in tracker,
            "GPS fix must reject collinear hosts and resolve ambiguous trilateration")
    require("round(value.x * 100.0) / 100.0" in tracker,
            "GPS fix precision must match gps.lua's hundredth-block rounding")
    require("@SubscribeEvent" in tracker and "ServerTickEvent.Post" in tracker and "probe.close()" in tracker,
            "GPS probe receivers must have a bounded server-tick lifecycle")
    require("NeoForge.EVENT_BUS.register(GpsSourceTracker)" in mod,
            "GPS tracker must be registered on the NeoForge server event bus")

    require(
        "InformationSourceKinds.CORE + snapshot.sources.map { it.kind }" in widget and
        "distinctBy { it.id }" in widget and
        "SourceRow.Section" in widget and
        "if (kind in collapsedKinds) return@forEach" in widget and
        "InformationSourceKinds.GPS" not in widget,
        "GPS should use the generic collapsible information-source UI, not a parallel widget path"
    )
    require("CompoundTag" not in tracker and "DataComponent" not in tracker,
            "runtime GPS availability/fixes must not be persisted")

    print("Validated live CC:Tweaked GPS probing, cached source projection and generic information-source UI integration.")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
