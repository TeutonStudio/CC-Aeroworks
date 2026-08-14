#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


module_types = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt")
source = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputSource.kt")
control = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedLeverController.kt")
controller = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedInputController.kt")
context = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputContext.kt")
coordinator = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputCoordinator.kt")
sample = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CombinedControlSamplePayload.kt")
legacy = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetCombinedLeverValuePayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
combined_ui = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
dbw_guard = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireDisplaySourceMixin.java")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Large interactive display modules keep actual Aeroworks channels and remain Combined-only.
require("interactive = true" in module_types, "large display module registration must be interactive")
require('addClonedChannel(builder, YOKE_X_TEMPLATE, "x")' in module_types,
        "interactive displays must expose real Aeroworks x channel")
require('addClonedChannel(builder, YOKE_Y_TEMPLATE, "y")' in module_types,
        "interactive displays must expose real Aeroworks y channel")
require('"cc_aeroworks:three_digit_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source,
        "three-digit display must advertise x/y combined channels")
require('"cc_aeroworks:large_radar_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source,
        "large radar must advertise x/y combined channels")
require("fun isCombinedOnly(module: MountedModule): Boolean = isDisplayPointerModule(module)" in source,
        "display pointer channels must be Combined-only")
require("forceCombined(invoker, module, column, index)" in combined_ui,
        "ModuleScreen must force display x/y channels into Combined mode")

# One network context owns target discovery. The active mouse path must not re-run target acquisition.
require("ConsoleMultiblockManager.resolve" in context,
        "Combined input context must reuse the canonical ControlDesk multiblock resolver")
require("lastSelections" in context and "cachedContext" in context,
        "Combined input context must retain network and per-binding selection state")
require("fun directCandidate" in context,
        "ambiguous bindings must retain a gaze-based explicit tie breaker")
require("InputEvent.Key" in control and "InputEvent.MouseButton.Pre" in control,
        "normal Combined controls must activate from physical PRESS/RELEASE edges")
require("acquireTargetIfPossible" not in control,
        "client tick/turn paths must never reacquire a normal control target")
require("WATCHDOG_INTERVAL_TICKS = 5" in control and "WATCHDOG_INTERVAL_TICKS = 5" in controller,
        "active Combined sessions must move world validation to the low-frequency watchdog")
require("CombinedInputContext.candidates" in control and "CombinedInputContext.candidates" in controller,
        "control and display input must share the multiblock binding context")
require("for (x in center.x - radius" not in controller and "kotlin.math.ceil" not in controller,
        "display activation must not scan a world cube on every key press")

# Mouse ownership is exclusive and non-preemptive. Shift is the camera-only escape hatch.
require("enum class Owner" in coordinator and "CONTROL" in coordinator and "DISPLAY" in coordinator,
        "combined input coordinator must distinguish control and display ownership")
require("Owner.CONTROL -> false" in coordinator and "Owner.DISPLAY -> false" in coordinator,
        "active Combined ownership must be non-preemptive")
require("claimControl" in control and "claimDisplay" in controller,
        "both Combined session kinds must claim the shared mouse owner")
require("baselineDX" in control and "baselineDY" in control and "baselinePending" in control,
        "normal Combined control must subtract the activation-boundary mouse baseline")
require("baselineDX" in controller and "baselineDY" in controller and "baselinePending" in controller,
        "display Combined control must subtract the activation-boundary mouse baseline")

# Control updates are one atomic sample, final release bypasses throttling, and latest packets win.
require("CombinedControlSamplePayload" in control and "CombinedChannelValue" in control,
        "normal Combined control must send atomic multi-channel samples")
require("sendPending(force = true, finalSample = true)" in control,
        "release/abort must force the final pending control sample")
require("payload.values.forEach" in sample and "desk.setChannelFromController" in sample,
        "server must apply every validated channel from one Combined sample")
require("lastAcceptedTick" not in sample,
        "new Combined samples must not use first-value-wins same-tick rejection")
require("lastAcceptedTick" not in legacy,
        "legacy Combined packets must also allow later same-tick values to win")
require("network.members.none" in sample and "network.members.none" in legacy,
        "server authorization must accept a player standing at any member of the same desk multiblock")
require("CombinedControlSamplePayload.TYPE" in payloads,
        "atomic Combined control sample payload must be registered")

# Direct ControlDesk peripherals receive Combined events immediately while snapshot polling stays fallback.
require("fun queueImmediateInput" in peripheral_state,
        "Combined server updates must publish direct ControlDesk CC events immediately")
require("onServerTick" in peripheral_state and "InputSnapshotDiff.changed" in peripheral_state,
        "snapshot polling must remain as the fallback for non-Combined/native changes")

# Display pointer stays per-channel Combined input, without the removed global key.
require("CombinedInputSource.activationBinding" in controller,
        "display pointer controller must read per-channel activation bindings")
require("DisplayInteractionKey" not in controller and "DisplayInteractionKey" not in client,
        "display pointer must not depend on the removed global display key")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayInteractionKey.kt").exists(),
        "global DisplayInteractionKey source must remain removed")

# Real display ControlChannels are configuration-only and must not become Drive By Wire outputs.
require("CombinedInputSource.INSTANCE.isDisplayPointerModule(module)" in dbw_guard,
        "Drive By Wire source selection must reject display pointer channels")
require('"client.DriveByWireDisplaySourceMixin"' in mixins,
        "Drive By Wire display-source guard must be registered")

require((ROOT / "src/main/resources/assets/cc_aeroworks/textures/gui/combined_input_placeholder.png").is_file(),
        "Combined mode icon for ModuleScreen is missing")
require("python3 tools/verify-display-combined-input.py" in workflow,
        "workflow must enforce display combined-input contract")

print(
    "Validated Combined control focus: edge-driven sessions, cached multiblock binding context, "
    "exclusive mouse ownership, 5-tick watchdogs, mouse baselines, atomic latest-wins samples, "
    "forced release flush, immediate direct CC events, display pointer integration and DBW isolation."
)
