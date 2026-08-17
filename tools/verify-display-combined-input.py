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
lifecycle = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AeroworksControlSessionMixin.kt")
sample = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CombinedControlSamplePayload.kt")
legacy = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetCombinedLeverValuePayload.kt")
pointer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayPointerActionPayload.kt")
sable_geometry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/sable/SableInteractionGeometry.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
override_guard = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityControlOverrideMixin.kt")
combined_ui = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
semantics = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/channel/ControlChannelSemantics.kt")
dbw_guard = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireDisplaySourceMixin.java")
catalog = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/ConsoleWireChannelsDisplayFilterMixin.java")
signal = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/compat/DriveByWireSignalFilterMixin.java")
touchdisplay = read("src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua")
handler_runtime = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

require(
    "interactive = true" in module_types and
    'addClonedChannel(builder, YOKE_X_TEMPLATE, "x")' in module_types and
    'addClonedChannel(builder, YOKE_Y_TEMPLATE, "y")' in module_types,
    "interactive displays must keep real Aeroworks x/y",
)
require(
    '"cc_aeroworks:three_digit_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source and
    '"cc_aeroworks:large_radar_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source,
    "display pointer channel declarations missing",
)
require(
    "fun isCombinedOnly(module: MountedModule): Boolean = isDisplayPointerModule(module)" in source and
    "forceCombined(invoker, module, column, index)" in combined_ui,
    "display x/y must remain Combined-only",
)
require(
    "ConsoleMultiblockManager.resolve" in context and "lastSelections" in context and "cachedContext" in context,
    "Combined input must keep canonical cached multiblock context",
)
require(
    "WATCHDOG_INTERVAL_TICKS = 5" in control and "WATCHDOG_INTERVAL_TICKS = 5" in controller and
    "claimControl" in control and "claimDisplay" in controller,
    "Combined ownership/watchdog contract changed",
)
require(
    "ConsoleControlClient.isActive()" in control and "ConsoleControlClient.isActive()" in controller and
    'method = ["exit(Ljava/lang/String;)V"]' in lifecycle,
    "Combined sessions must follow Aeroworks lifecycle",
)

# Once a display owns Combined input, primary mouse buttons must reach the pseudo pointer before
# mouse activation bindings or vanilla actions can consume them.
pointer_capture = controller.find("handlePointerButton(event, minecraft, active)")
activation_routing = controller.find("val binding = InputConstants.Type.MOUSE.getOrCreate(event.button).name")
require(
    pointer_capture >= 0 and activation_routing >= 0 and pointer_capture < activation_routing and
    "event.isCanceled = true" in controller,
    "active display pointer clicks must be captured before mouse activation routing",
)
require(
    "GLFW.GLFW_MOUSE_BUTTON_RIGHT ->" in controller and
    "sendPointerAction(active, DisplayPointerAction.TAP)" in controller and
    "GLFW.GLFW_MOUSE_BUTTON_LEFT -> when (event.action)" in controller and
    "active.holdActive = true" in controller and
    "sendPointerAction(active, DisplayPointerAction.HOLD)" in controller and
    "active.holdActive = false" in controller,
    "display pseudo pointer must distinguish right tap and left hold press/release",
)
require(
    'DOUBLE_TAP("double_tap"),\n    HOLD("hold")' in pointer,
    "display HOLD must be appended without changing existing pointer-action ordinals",
)
require(
    'event.action == "hold"' in handler_runtime and "handler.onHold or handler.onPointer" in handler_runtime and
    "function touchdisplay.isHold(event)" in touchdisplay and 'event.action == "hold"' in touchdisplay,
    "ComputerCraft display helpers must expose HOLD without breaking generic pointer handlers",
)

# Display-pointer reach is special on Sable: plot coordinates are not rendered world coordinates.
require(
    "Sable.HELPER.distanceSquaredWithSubLevels" in sable_geometry and
    "Sable.HELPER.projectOutOfSubLevel" in sable_geometry,
    "display interaction geometry must project reach and mayInteract through Sable",
)
require(
    "SableInteractionGeometry.withinReach" in pointer and
    "SableInteractionGeometry.mayInteract" in pointer,
    "server display pointer packets must use Sable-aware interaction validation",
)
require(
    "SableInteractionGeometry.withinReach" in controller and
    "SableInteractionGeometry.withinReach" in context,
    "client display pointer watchdog/context must use Sable-aware reach",
)
require(
    "player.distanceToSqr(it.pos.center)" not in pointer and
    "player.distanceToSqr(it.pos.center)" not in controller and
    "player.distanceToSqr(it.pos.center)" not in context,
    "display pointer paths must not compare players directly to Sable plot coordinates",
)

require(
    "desk.setChannelFromController" in sample and "previousValue" in sample and "effectiveValue" in sample and
    "previousValue" in legacy and "effectiveValue" in legacy,
    "server must keep real effective Aeroworks values",
)
require(
    "ControlOverrideManager.isHardOverridden" in override_guard and "fun queueImmediateInput" in peripheral and
    "CombinedInputSource.activationBinding" in controller,
    "control authority/event/binding contract changed",
)
require("CombinedControlModeTracker" not in client, "obsolete mirrored Combined session tracker returned")
require(
    "DISPLAY_POINTER" in semantics and
    "driveByWire = supported && kind == ControlChannelKind.VEHICLE_CONTROL" in semantics and
    "computerOverride = supported && kind == ControlChannelKind.VEHICLE_CONTROL" in semantics,
    "shared display semantics must reject DBW/vehicle override",
)
require("ControlChannelSemantics.INSTANCE.kind(module)" in dbw_guard, "standalone display DBW guard must use shared semantics")
require("filterExposedIds" in catalog and "ConsoleWireChannels" in catalog, "Aeroworks DBW catalogue must remove display pointer x/y")
require(
    "getCurrentSignal" in signal and '@At("RETURN")' in signal and
    "CallbackInfoReturnable<Integer>" in signal and "cir.setReturnValue(0)" in signal,
    "final DBW value lookup must force display pointer signals to zero after normal integrations",
)
require(
    "priority = 500" in signal and "isDriveByWireExposed" in signal,
    "display value guard must run as a late low-priority DBW integration",
)
for registered in (
    '"client.DriveByWireDisplaySourceMixin"',
    '"compat.ConsoleWireChannelsDisplayFilterMixin"',
    '"compat.DriveByWireSignalFilterMixin"',
):
    require(registered in mixins, f"missing display isolation mixin {registered}")
require(
    (ROOT / "src/main/resources/assets/cc_aeroworks/textures/gui/combined_input_placeholder.png").is_file(),
    "Combined icon missing",
)
require("python3 tools/verify-display-combined-input.py" in workflow, "workflow must enforce display Combined contract")
print("Validated Combined display input, tap/hold pointer routing, Sable-aware pointer reach, and DBW isolation.")
