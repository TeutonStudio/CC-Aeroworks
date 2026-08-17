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
target = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedTarget.kt")
raw_mouse = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayPrimaryMouseCapture.kt")
mouse_guard = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/CombinedMouseButtonGuardMixin.kt")
context = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputContext.kt")
coordinator = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputCoordinator.kt")
lifecycle = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/AeroworksControlSessionMixin.kt")
sample = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CombinedControlSamplePayload.kt")
legacy = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SetCombinedLeverValuePayload.kt")
pointer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayPointerActionPayload.kt")
draw = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayDrawPayload.kt")
payloads = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/CCPayloads.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
display_input = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayInput.kt")
script_catalog = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DisplayScriptCatalog.kt")
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
touch_test = read("examples/cc/touch-test.lua")
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

# Regression guard: this three-source capture is what fixed the previously non-functional touch
# path. Draw may evolve behind it, but raw interception + NeoForge fallback + direct GLFW polling
# must all stay present and share one button state.
require(
    "@Mixin(MouseHandler::class)" in mouse_guard and
    'method = ["onPress(JIII)V"]' in mouse_guard and
    'at = [At("HEAD")]' in mouse_guard and
    "cancellable = true" in mouse_guard and
    "DisplayPrimaryMouseCapture.capture(windowPointer, button, action)" in mouse_guard and
    "callback.cancel()" in mouse_guard,
    "display primary buttons must still be intercepted at MouseHandler before NeoForge/vanilla routing",
)
require('"client.CombinedMouseButtonGuardMixin"' in mixins, "raw display mouse guard mixin is not registered")
require(
    "fun beginSession(active: DisplayCombinedTarget" in raw_mouse and
    "GLFW.glfwGetMouseButton" in raw_mouse and
    "fun poll(minecraft: Minecraft, active: DisplayCombinedTarget)" in raw_mouse and
    "fun captureFallback(button: Int, action: Int)" in raw_mouse and
    "leftOwnedByDisplay" in raw_mouse and "rightOwnedByDisplay" in raw_mouse,
    "display touch must keep physical baseline plus shared raw/event/poll edge state",
)
require(
    "DisplayPrimaryMouseCapture.beginSession(candidate, minecraft)" in controller and
    controller.count("DisplayPrimaryMouseCapture.poll(minecraft, active)") >= 2 and
    "DisplayPrimaryMouseCapture.captureFallback(event.button, event.action)" in controller and
    "DisplayPrimaryMouseCapture.observePointer(active)" in controller and
    "DisplayPrimaryMouseCapture.flushDrawSample(active)" in controller and
    "DisplayPrimaryMouseCapture.endSession(active)" in controller,
    "active display controller must preserve capture fallbacks while sampling draw movement",
)

# LEFT remains the proven single tap. RIGHT now starts/ends a draw gesture instead of emitting one
# hold packet. Legacy HOLD stays in the wire enum only to preserve existing ordinals.
require(
    "if (!leftDown)" in raw_mouse and
    "source=$source left false->true tapEdge=true" in raw_mouse and
    "sendPointerAction(active, DisplayPointerAction.TAP)" in raw_mouse,
    "left display button must remain a tap edge",
)
require(
    "if (!rightDown)" in raw_mouse and
    "source=$source right false->true drawEdge=true" in raw_mouse and
    "beginDraw(active)" in raw_mouse and
    "finishDraw(active, source)" in raw_mouse and
    "DisplayDrawPayload(" in raw_mouse and
    "sendPointerAction(active, DisplayPointerAction.HOLD)" not in raw_mouse,
    "right display button must own a stateful draw gesture, not emit legacy HOLD",
)
require(
    "drawActive" in target and "drawGestureId" in target and "drawSequence" in target and
    "drawLastSentU" in target and "drawLastSentV" in target and "drawDirty" in target,
    "display target must retain draw gesture sampling state",
)
require(
    '"mouse-gate"' in raw_mouse and '"button-sample"' in raw_mouse and
    '"send physical action=' in raw_mouse and '"send draw stage=' in raw_mouse and
    "PacketDistributor.sendToServer" in raw_mouse,
    "tap/draw physical path must remain visible in TouchTrace and send payloads directly",
)

# Draw network stream is ordered and server authoritative. The client sends normalized points;
# server resolves pixels and derives delta from the last accepted event, exactly so Lua does not
# have to maintain previous-event state merely to draw a segment.
require(
    "DisplayDrawPayload.TYPE" in payloads and 'CCAeroworks.id("display_draw")' in draw and
    "val gestureId: Long" in draw and "val sequence: Int" in draw and "val isEnd: Boolean" in draw,
    "draw payload must be registered with gesture ordering and explicit end state",
)
require(
    "DeskDisplayGeometry.touch" in draw and
    "payload.sequence != state.lastSequence + 1" in draw and
    "current.x - state.lastTouch.x" in draw and
    "current.y - state.lastTouch.y" in draw and
    "startX = state.startTouch.x" in draw and
    "startY = state.startTouch.y" in draw,
    "server must resolve draw pixels and provide delta relative to the immediately previous event",
)
require(
    "SableInteractionGeometry.withinReach" in draw and "SableInteractionGeometry.mayInteract" in draw and
    "STALE_GESTURE_TICKS" in draw,
    "draw stream must retain Sable-aware validation and bounded stale server state",
)
require(
    'val action: String' in display_input and 'get() = action == "draw"' in display_input and
    "gestureId" in display_input and "sequence" in display_input and
    "startX" in display_input and "deltaX" in display_input and "isEnd" in display_input,
    "dispatcher input model must expose semantic draw metadata",
)
require(
    "ControlDeskPeripheralState.queueDisplayInput(desk, input)" in dispatcher and
    "input.deltaX ?: 0" in dispatcher and "input.deltaY ?: 0" in dispatcher and
    "input.isEnd" in dispatcher and
    'if (input.action == DisplayPointerAction.TAP.eventName)' in dispatcher,
    "embedded dispatch must append draw metadata while preserving tap compatibility",
)
require(
    "input.deltaX ?: 0" in peripheral and "input.deltaY ?: 0" in peripheral and
    'if (input.action == "tap")' in peripheral,
    "external ComputerCraft events must expose draw deltas without turning draw into monitor_touch",
)

# Existing action ordinals remain intact for compatibility, but new combined input exposes tap/draw.
require(
    'DOUBLE_TAP("double_tap"),\n    HOLD("hold")' in pointer,
    "legacy pointer-action ordinals must remain stable",
)
require(
    'event.action == "draw"' in handler_runtime and "handler.onDraw or handler.onPointer" in handler_runtime and
    "event[24] == true" in handler_runtime and
    "function touchdisplay.isDraw(event)" in touchdisplay and
    "function touchdisplay.drawDelta(event)" in touchdisplay and
    "function touchdisplay.drawEnded(event)" in touchdisplay and
    '"onDraw"' in script_catalog,
    "ComputerCraft handler/runtime/catalog must expose draw gestures",
)
require(
    "onDraw = function(event)" in touch_test and
    "drawLine(event, x - dx, y - dy, x, y)" in touch_test and
    "touchdisplay.drawEnded(event)" in touch_test,
    "manual regression handler must exercise per-event draw deltas and explicit end",
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
    "server tap packets must use Sable-aware interaction validation",
)
require(
    "SableInteractionGeometry.withinReach" in controller and
    "SableInteractionGeometry.withinReach" in context,
    "client display pointer watchdog/context must use Sable-aware reach",
)
require(
    "player.distanceToSqr(it.pos.center)" not in pointer and
    "player.distanceToSqr(it.pos.center)" not in draw and
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
print("Validated resilient raw/event/poll display capture with left tap and ordered right-button draw gestures.")
