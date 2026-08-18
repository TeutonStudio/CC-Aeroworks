#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


motion = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayPointerMotion.kt")
motion_test = read("src/test/kotlin/de/teutonstudio/ccaeroworks/input/DisplayPointerMotionTest.kt")
path_buffer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayDrawPathBuffer.kt")
target = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedTarget.kt")
controller = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedInputController.kt")
capture = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayPrimaryMouseCapture.kt")
payload = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/DisplayDrawPayload.kt")
gesture_state = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/SingleGestureSessionState.kt")
gesture_test = read("src/test/kotlin/de/teutonstudio/ccaeroworks/network/SingleGestureSessionStateTest.kt")
budget = read("src/main/kotlin/de/teutonstudio/ccaeroworks/network/PlayerTickBudget.kt")
budget_test = read("src/test/kotlin/de/teutonstudio/ccaeroworks/network/PlayerTickBudgetTest.kt")
model = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayInput.kt")
pixels = read("src/main/kotlin/de/teutonstudio/ccaeroworks/display/DeskDisplayPixels.kt")
service = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskService.kt")
desk_access = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/aeroworks/AeroworksDeskAccess.kt")
desk_peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
desk_handle = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralLuaHandles.kt")
dispatcher = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/DeskDisplayInputDispatcher.kt")
peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheralState.kt")
diagnostics = read("src/main/kotlin/de/teutonstudio/ccaeroworks/debug/TouchInputDiagnostics.kt")
display = read("src/main/resources/data/computercraft/lua/rom/modules/main/display.lua")
touchdisplay = read("src/main/resources/data/computercraft/lua/rom/modules/main/touchdisplay.lua")
runtime = read("src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_display_handlers.lua")
catalog = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/guide/ApiReferenceCatalog.kt")
touch_test = read("examples/cc/touch-test.lua")

require(
    "SMOOTHING_TIME_SECONDS = 0.016" in motion and
    "sampleVelocityU = deltaU / deltaSeconds" in motion and
    "sampleVelocityV = deltaV / deltaSeconds" in motion and
    "1.0 - exp(-deltaSeconds / SMOOTHING_TIME_SECONDS)" in motion and
    "directionU = velocityU / magnitude" in motion and
    "directionV = velocityV / magnitude" in motion,
    "pointer motion must expose time-invariant smoothed velocity and normalized direction",
)
require(
    'listOf(20.0, 60.0, 144.0, 240.0)' in motion_test and
    "runTurnAtRate" in motion_test,
    "pointer tests must compare equal wall-clock motion across multiple frame rates",
)
require(
    "maxSamples: Int = 16" in path_buffer and
    "removeLeastImportantInteriorPoint" in path_buffer and
    "geometricDistance + directionChange * 0.002" in path_buffer,
    "draw path buffer must remain bounded while preserving geometrically important bends",
)
require(
    "val drawPath: DisplayDrawPathBuffer" in target and "pointerMotionSampleNanos" in target,
    "combined display target must retain high-frequency path and timing state",
)
require(
    "val movementU = active.u - previousU" in controller and
    "val movementV = active.v - previousV" in controller and
    "active.pointerMotion.observe(movementU, movementV, elapsedSeconds)" in controller and
    "DisplayPrimaryMouseCapture.observePointer(active)" in controller,
    "direction must use actual clamped display motion and record high-frequency pointer samples",
)
require(
    "active.drawPath.record(currentPathSample(active))" in capture and
    "samples = samples" in capture and
    "send at most one packet per client tick" in capture.lower(),
    "client draw capture must batch the high-frequency path into one tick-bounded packet",
)
require(
    "MAX_BATCH_SAMPLES: Int = 16" in payload and
    "val samples: List<DisplayDrawSamplePayload>" in payload and
    "payload.samples.forEachIndexed" in payload and
    "add(state.lastSample.toDeskSample())" in payload,
    "server must validate every batched sample and prepend the previous accepted endpoint",
)
require(
    "MAX_PACKETS_PER_PLAYER_TICK = 8" in payload and
    "MAX_SAMPLES_PER_PLAYER_TICK" in payload and
    "ingressBudget.tryConsume(player.uuid, tick, payload.samples.size)" in payload and
    "class PlayerTickBudget" in budget and
    "current.packets >= maxPacketsPerTick" in budget and
    "current.units + units > maxUnitsPerTick" in budget and
    "packet budget resets on the next tick" in budget_test,
    "draw ingress must be bounded per player and server tick before expensive dispatch work",
)
require(
    "SingleGestureSessionState<GestureKey, GestureData>" in payload and
    "val key = GestureKey(player.uuid, payload.pos.asLong(), payload.socket)" in payload and
    "gestures.start(" in payload and
    "gestures.advance(" in payload and
    "class SingleGestureSessionState" in gesture_state and
    "existing?.gestureId == gestureId" in gesture_state and
    "existing.gestureId != gestureId" in gesture_state and
    "sequence != expected" in gesture_state and
    "new gesture replaces an abandoned gesture in the same slot" in gesture_test and
    "missing and skipped sequences are rejected without advancing state" in gesture_test,
    "draw state must keep one tested active gesture per player/display/socket and reject stale or out-of-sequence packets",
)
require(
    "data class DeskDisplayStrokeSample" in model and
    "val samples: List<DeskDisplayStrokeSample>" in model and
    "fun luaSamples()" in model,
    "display input model must expose server-resolved stroke samples",
)
require(
    "input.luaSamples()" in dispatcher and "input.luaSamples()" in peripheral,
    "embedded and external display events must append the resolved sample table",
)
require(
    "samples = type(event[28]) == \"table\" and event[28] or nil" in runtime,
    "automatic display handler must map the appended sample table",
)
require(
    "local drawHandlers = {}" in runtime and
    "local function handlerFor(path, event)" in runtime and
    "cached.gesture ~= gesture" in runtime and
    "if drawKey and descriptor.isEnd then drawHandlers[drawKey] = nil end" in runtime,
    "automatic runtime must load a draw handler once per gesture instead of once per sample",
)
require(
    "local function shouldBridgeRoutine(event)" in runtime and
    'if event.action ~= "draw" then return true end' in runtime and
    "tonumber(event.sequence) == 0 or event.isEnd == true" in runtime,
    "routine main-thread diagnostics must be restricted to draw boundaries",
)
require(
    "fun withPixels(points: Iterable<Pair<Int, Int>>, enabled: Boolean)" in pixels and
    "val next = packedBits.copyOf()" in pixels and
    "DeskDisplayPixelPatch" in pixels,
    "native pixel batching must copy the packed raster once and report actual changes",
)
require(
    "fun setDisplayPixelBatch(" in service and
    "pixels.withPixels(zeroBased, enabled)" in service and
    "if (patch.changed > 0)" in service,
    "desk service must persist one native packed-raster patch only when pixels changed",
)
require(
    "desk.setModuleName(socket, \"\", Component.literal(pixels.encode()))" in desk_access and
    "readBack = display(desk, socket)" not in desk_access,
    "packed raster writes must not immediately decode themselves again on every stroke sample",
)
require(
    "fun setDisplayPixelBatch(arguments: IArguments): Int" in desk_peripheral and
    "fun setDisplayPixelBatch(arguments: IArguments): Int" in desk_handle,
    "external and embedded ControlDesk Lua surfaces must expose native pixel batches",
)
require(
    "isDrawHotPathNoise" in diagnostics and
    '"pixels" -> true' in diagnostics and
    'message.contains("send draw stage=sample")' in diagnostics and
    'message.contains("accepted draw SAMPLE")' in diagnostics,
    "TouchTrace must suppress high-frequency draw log spam while keeping boundary diagnostics",
)
require(
    "function display.setPixelBatch(event, points, enabled)" in display and
    "desk.setDisplayPixelBatch(socket, points, enabled ~= false)" in display,
    "display Lua module must route pixel batches directly to the native desk method",
)
require(
    "function touchdisplay.drawSamples(event)" in touchdisplay and
    "previousEventSample(event, current)" in touchdisplay and
    "table.insert(samples, 1, previous)" in touchdisplay and
    "function touchdisplay.drawStroke(event)" in touchdisplay and
    "rasterizeHermite" in touchdisplay and
    "display.setPixelBatch(event, points, true)" in touchdisplay and
    "TANGENT_SCALE = 0.34" in touchdisplay,
    "touchdisplay must retain a delta fallback, Hermite-rasterize the batch and use one native pixel patch",
)
require(
    "local strokeStates = {}" in touchdisplay and
    "local function continuousSample(sample, width, height)" in touchdisplay and
    "if state and state.lastSample" in touchdisplay and
    "table.insert(samples, 1, state.lastSample)" in touchdisplay and
    "if key and touchdisplay.drawEnded(event) then strokeStates[key] = nil end" in touchdisplay,
    "drawStroke must preserve continuous sub-pixel state across event boundaries and bridge skipped events",
)
require(
    "MIN_DIRECTION_ALIGNMENT = -0.15" in touchdisplay and
    "if tx * cx + ty * cy < MIN_DIRECTION_ALIGNMENT" in touchdisplay,
    "fast strokes must reject backwards stale tangents which would create loops or mini-strokes",
)
require(
    '"setPixelBatch(event, points, enabled?)"' in catalog and
    '"setDisplayPixelBatch(socket, points, enabled?)"' in catalog and
    '"drawSamples(event)"' in catalog and '"drawStroke(event)"' in catalog,
    "in-game API catalog must expose native pixel batching and stroke helpers",
)
require(
    "touchdisplay.drawStroke(event)" in touch_test and
    "touchdisplay.drawSamples(event)" in touch_test and
    "LOG_EVERY = 10" in touch_test and
    "SEQ GAP" in touch_test,
    "manual touch regression handler must exercise strokes without becoming a logging bottleneck",
)

print("Validated time-invariant draw velocity, bounded per-player ingress, tested single-slot gesture sequencing, sub-tick batching, cached handlers, continuous Hermite stroke stitching and native pixel patches.")
