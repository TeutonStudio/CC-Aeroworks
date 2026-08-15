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
lifecycle_hook = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AeroworksControlSessionMixin.kt")
sample = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CombinedControlSamplePayload.kt")
legacy = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetCombinedLeverValuePayload.kt")
peripheral_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
override_guard = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityControlOverrideMixin.kt")
combined_ui = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
semantics = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ControlChannelSemantics.kt")
dbw_guard = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireDisplaySourceMixin.java")
dbw_catalog_guard = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/ConsoleWireChannelsDisplayFilterMixin.java")
dbw_signal_guard = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/DriveByWireSignalFilterMixin.java")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Interactive displays deliberately keep real Aeroworks x/y channels for ModuleScreen/Combined input.
require("interactive = true" in module_types, "large display module registration must remain interactive")
require('addClonedChannel(builder, YOKE_X_TEMPLATE, "x")' in module_types, "interactive displays need Aeroworks x")
require('addClonedChannel(builder, YOKE_Y_TEMPLATE, "y")' in module_types, "interactive displays need Aeroworks y")
require('"cc_aeroworks:three_digit_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source, "three digit display must advertise x/y")
require('"cc_aeroworks:large_radar_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source, "large radar must advertise x/y")
require("fun isCombinedOnly(module: MountedModule): Boolean = isDisplayPointerModule(module)" in source, "display pointer channels must remain Combined-only")
require("forceCombined(invoker, module, column, index)" in combined_ui, "display x/y must be forced to Combined mode")

# Existing Combined session lifecycle and mouse ownership must remain intact.
require("ConsoleMultiblockManager.resolve" in context and "lastSelections" in context and "cachedContext" in context, "Combined input must share cached multiblock context")
require("InputEvent.Key" in control and "InputEvent.MouseButton.Pre" in control, "Combined controls must use physical input edges")
require("WATCHDOG_INTERVAL_TICKS = 5" in control and "WATCHDOG_INTERVAL_TICKS = 5" in controller, "Combined watchdog cadence changed")
require("enum class Owner" in coordinator and "CONTROL" in coordinator and "DISPLAY" in coordinator, "mouse coordinator must distinguish control/display")
require("claimControl" in control and "claimDisplay" in controller, "Combined controllers must claim shared mouse ownership")
require("baselineDX" in control and "baselineDX" in controller, "activation-boundary mouse baseline must remain")
require("ConsoleControlClient.isActive()" in control and "ConsoleControlClient.isActive()" in controller, "Combined sessions must follow Aeroworks lifecycle")
require('method = ["exit(Ljava/lang/String;)V"]' in lifecycle_hook, "Aeroworks exit(String) lifecycle hook missing")
require("CombinedLeverController.abortControlMode()" in lifecycle_hook and "DisplayCombinedInputController.abortControlMode()" in lifecycle_hook, "Aeroworks exit must abort both Combined owners")
require("CombinedControlModeTracker" not in client, "removed mirrored session tracker must stay removed")

# Server updates stay real Aeroworks values. DBW isolation must happen after this boundary.
require("desk.setChannelFromController" in sample and "payload.values.forEach" in sample, "Combined sample must still update Aeroworks channels")
require("previousValue" in sample and "effectiveValue" in sample, "Combined sample must publish effective values")
require("previousValue" in legacy and "effectiveValue" in legacy, "legacy sample must publish effective values")
require("ControlOverrideManager.isHardOverridden" in override_guard, "HARD override guard missing")
require("fun queueImmediateInput" in peripheral_state, "direct peripheral event bridge missing")
require("CombinedInputSource.activationBinding" in controller, "display pointer must use per-channel activation bindings")

# A display pointer is a semantic control kind: valid for Combined, invalid for DBW/vehicle overrides.
require("DISPLAY_POINTER" in semantics and "VEHICLE_CONTROL" in semantics, "shared channel semantics must distinguish display pointer and vehicle controls")
require("driveByWire = supported && kind == ControlChannelKind.VEHICLE_CONTROL" in semantics, "display pointers must be non-DBW by semantic contract")
require("computerOverride = supported && kind == ControlChannelKind.VEHICLE_CONTROL" in semantics, "display pointers must not be vehicle overrides")
require("ControlChannelSemantics.INSTANCE.kind(module)" in dbw_guard, "standalone DBW source guard must use shared semantics")
require("filterExposedIds" in dbw_catalog_guard and "ConsoleWireChannels" in dbw_catalog_guard, "Aeroworks DBW catalogue must remove display pointer channels")
require("trySetSignalAt" in dbw_signal_guard and "isDriveByWireExposed" in dbw_signal_guard, "DBW signal publication must reject display pointer channels")
require("WireNetworkManager" in dbw_signal_guard, "runtime DBW guard must target WireNetworkManager")
for registered in (
    '"client.DriveByWireDisplaySourceMixin"',
    '"compat.ConsoleWireChannelsDisplayFilterMixin"',
    '"compat.DriveByWireSignalFilterMixin"',
):
    require(registered in mixins, f"missing DBW display isolation mixin: {registered}")

require((ROOT / "src/main/resources/assets/cc_aeroworks/textures/gui/combined_input_placeholder.png").is_file(), "Combined mode icon missing")
require("python3 tools/verify-display-combined-input.py" in workflow, "workflow must enforce display Combined contract")

print("Validated Combined display input and three-layer DBW isolation: semantic classification, Aeroworks catalogue filtering and WireNetworkManager signal blocking.")
