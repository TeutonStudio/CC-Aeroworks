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

# A live display pointer owns left/right gesture input even when another control listener already
# cancelled the NeoForge mouse event. Gesture dispatch must happen before generic activation-key
# handling so mouse bindings cannot consume the display tap/double-tap edge.
mouse_handler = controller.find("fun onMouseButton(event: InputEvent.MouseButton.Pre)")
require(mouse_handler >= 0, "display Combined mouse handler missing")
mouse_annotation = controller[max(0, mouse_handler - 120):mouse_handler]
require(
    "@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)" in mouse_annotation,
    "display pointer must receive cancelled mouse events before competing Combined handlers",
)
mouse_body_end = controller.find("private fun sendPointerAction", mouse_handler)
require(mouse_body_end > mouse_handler, "display pointer action helper missing")
mouse_body = controller[mouse_handler:mouse_body_end]
semantic_position = mouse_body.find("activeBeforeActivation != null && pointerButton")
activation_position = mouse_body.find("val activationEdge")
require(
    semantic_position >= 0 and activation_position > semantic_position,
    "active display gestures must be handled before generic Combined activation edges",
)
require(
    "GLFW.GLFW_PRESS -> if (basicSessionValid(minecraft))" in mouse_body and
    "sendPointerAction(activeBeforeActivation, event.button)" in mouse_body and
    "GLFW.GLFW_RELEASE -> onBindingReleased(binding)" in mouse_body,
    "display gestures must send on press and still retire mouse activation bindings on release",
)
require(
    "button == GLFW.GLFW_MOUSE_BUTTON_RIGHT" in controller and
    "DisplayPointerAction.TAP" in controller and
    "DisplayPointerAction.DOUBLE_TAP" in controller and
    "DisplayPointerActionPayload(active.pos, active.socket, active.u, active.v, action)" in controller,
    "right click must remain tap and left click must remain double-tap",
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
print("Validated Combined display input, pointer click ownership, Sable-aware pointer reach, and DBW isolation.")
