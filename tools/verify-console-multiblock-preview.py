#!/usr/bin/env python3
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


renderer = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/ConsoleMultiblockPreviewRenderer.kt")
mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenMultiblockPreviewMixin.kt")
accessor = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ConsoleScreenAccessor.kt")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

require(
    'method = ["renderConsolePreview(Lnet/minecraft/client/gui/GuiGraphics;)V"]' in mixin,
    "ConsoleScreen preview replacement must target the exact native method descriptor",
)
require('at = [At("HEAD")]' in mixin and "cancellable = true" in mixin, "native preview hook must be cancellable at HEAD")
require("ConsoleMultiblockPreviewRenderer.render" in mixin and "callback.cancel()" in mixin, "native preview must only be cancelled after custom rendering succeeds")
require('@Accessor("windowTop")' in accessor and '@Accessor("windowLeft")' in accessor, "preview needs exact native window anchor")
require('"client.ConsoleScreenMultiblockPreviewMixin"' in mixins, "preview mixin is not registered")

require("ConsoleMultiblockManager.resolve(level, console.blockPos)" in renderer, "preview must reuse canonical multiblock resolution")
require("snapshot.members.size <= 1" in renderer, "single desks must retain Aeroworks' native preview")
require("ConsoleNetworkState.PARTIALLY_LOADED" in renderer and "ConsoleNetworkState.TOO_LARGE" in renderer, "incomplete/oversized networks need safe native fallback")
require('it.name == "facing"' in renderer and "canonicalFacing = Direction.NORTH" in renderer, "world facing must be normalized")
require('it.name == "ceiling"' in renderer and "state.setValue(ceiling, false)" in renderer, "ceiling placement must be normalized like Aeroworks")
require("right = facing.clockWise" in renderer and "World-forward maps to canonical NORTH" in renderer, "member positions must be transformed into desk-local coordinates")

for token in (
    "VIEWPORT_WIDTH = 92",
    "VIEWPORT_HEIGHT = 72",
    "FIT_WIDTH = 84.0",
    "FIT_HEIGHT = 58.0",
    "PREVIEW_VERTICAL_BIAS = 2.0F",
    "NATIVE_SCALE = 42.0F",
    "NATIVE_PITCH = 30.0F",
    "NATIVE_YAW = 225.0F",
    "MAX_SIDE_YAW = 262.0F",
    "MIN_SIDE_PITCH = 6.0F",
    "MAX_CAMERA_SPAN = 64.0",
    "PERSPECTIVE_DEPTH_FRACTION = 0.30",
    "MIN_PERSPECTIVE_W = 0.65",
    "val cameraProgress = cameraProgress(scene)",
    "val yaw = lerp(NATIVE_YAW, MAX_SIDE_YAW, cameraProgress)",
    "val pitch = lerp(NATIVE_PITCH, MIN_SIDE_PITCH, cameraProgress)",
    "val projected = projectedBounds(scene, yaw, pitch, perspectiveDistance)",
    "FIT_WIDTH / projected.width",
    "FIT_HEIGHT / projected.height",
):
    require(token in renderer, f"length-adaptive perspective preview missing {token}")

require("private fun cameraProgress(scene: Scene): Float" in renderer, "length-dependent camera progression missing")
require("ln(horizontalSpan / 2.0)" in renderer and "sqrt(normalized).toFloat()" in renderer, "camera rotation must grow smoothly and visibly with multiblock span")
require("MAX_OPTIMIZED_YAW" not in renderer and "optimizedYaw(" not in renderer, "old aspect-only yaw solver must not override the visible length rotation")
require("ProjectionScore" not in renderer, "obsolete aspect-score camera path remains in renderer")

# The scene must be rotated once in camera space before any per-member translation/render.
require(
    renderer.index("cameraPose.mulPose(Axis.YP.rotationDegrees(layout.yaw))")
    < renderer.index("scene.members.forEach { member ->"),
    "collective Y rotation must happen before individual member transforms",
)
require("val cameraPose = PoseStack()" in renderer, "preview needs a camera-space PoseStack")
require("graphics.pose()" not in renderer, "GUI PoseStack would bake an orthographic screen transform before perspective")

# Catnip SuperByteBuffer discards homogeneous w before forwarding vertices. Perspective must
# therefore happen in a VertexConsumer wrapper after the shared camera transform.
require("private class PerspectiveVertexConsumer(" in renderer, "vertex-level perspective consumer missing")
require("PerspectiveVertexConsumer(" in renderer and "deskBuffer.renderInto(cameraPose, perspectiveConsumer)" in renderer, "desk vertices must pass through the perspective consumer")
require("partBuffer.renderInto(poseStack, consumer)" in renderer, "module vertices must share the same perspective consumer")
require("1.0F - z / layout.perspectiveDistance" in renderer, "runtime perspective denominator must enlarge positive-Z near geometry")
require("1.0F + z / layout.perspectiveDistance" not in renderer, "runtime perspective depth direction is inverted")
require("val projectedX = x / denominator" in renderer and "val projectedY = y / denominator" in renderer, "runtime x/y perspective divide missing")
require("projectedX - layout.projectedCenterX" in renderer and "projectedY - layout.projectedCenterY" in renderer, "perspective projection must be re-centered after asymmetric depth scaling")
require("GUI_Z + layout.scale * projectedZ" in renderer, "perspective depth must be retained for depth testing")
require("delegate.setNormal(x, y, z)" in renderer, "perspective must not incorrectly project normals")

# CPU-side bounds must mirror the runtime perspective formula exactly.
require("private fun projectedBounds(" in renderer, "perspective projection bounds solver missing")
require("val cameraZ = sinPitch * localY + cosPitch * yawZ" in renderer, "solver must calculate camera-space depth")
require("val perspective = perspectiveFactor(cameraZ, perspectiveDistance)" in renderer, "solver must use camera-space depth for perspective")
require("val projectedX = yawX * perspective" in renderer and "val projectedY = cameraY * perspective" in renderer, "solver must perspective-project every corner")
require("private fun perspectiveFactor(" in renderer and "1.0 - cameraZ / perspectiveDistance" in renderer, "solver/runtime perspective formula drift")
require("scene.members.forEach { member ->" in renderer, "projection must include every member")
require("doubleArrayOf(member.x, member.x + 1.0)" in renderer, "projection must include each member's X corners")
require("doubleArrayOf(member.y, member.y + 1.0)" in renderer, "projection must include each member's Y corners")
require("doubleArrayOf(member.z, member.z + 1.0)" in renderer, "projection must include each member's Z corners")

# The smaller fit rectangle creates real internal headroom for mounted module geometry. Merely
# widening the scissor would hide the symptom rather than reducing the oversized preview.
require("centerY.toFloat() + PREVIEW_VERTICAL_BIAS" in renderer, "preview needs a small downward bias for top module headroom")
require("graphics.enableScissor" in renderer and "graphics.disableScissor()" in renderer, "preview must remain clipped to its native UI region")

# Cache scene + solved camera together. Rendering state remains live through desk references.
require("private var cachedPreview: CachedPreview?" in renderer, "scene/layout cache missing")
require("private fun previewFor(snapshot: ConsoleMultiblockSnapshot): CachedPreview" in renderer, "revision-cached preview builder missing")
require("snapshot.revision" in renderer and "snapshot.anchor.asLong()" in renderer, "preview cache must be keyed by multiblock identity and revision")
require("val scene = buildScene(snapshot)" in renderer and "CachedPreview(key, scene, layoutFor(scene))" in renderer, "scene and layout must be built together on cache miss")
require("private data class CachedPreview(" in renderer and "val scene: Scene" in renderer and "val layout: Layout" in renderer, "cache must retain both scene and layout")

require("CachedBuffers.block(state)" in renderer, "desk baked model rendering missing")
require("ModulePartRender.flatten" in renderer and "ModulePartRender.apply" in renderer, "mounted Aeroworks module rendering missing")
require("ModulePartRender.displayValues(mounted, restValues)" in renderer, "module preview must retain native REST-value semantics")
require("import net.createmod.catnip.render.SuperByteBuffer" in renderer, "Kotlin renderer must import SuperByteBuffer for explicit Flywheel self types")
require(renderer.count(".light<SuperByteBuffer>(FULL_BRIGHT)") == 2, "both Flywheel light transforms must specify SuperByteBuffer so Kotlin can infer Self")
require(renderer.count("buffers.endBatch()") == 1, "multiblock preview must flush its shared buffer exactly once")
require("Lighting.setupFor3DItems()" in renderer and "Lighting.setupForFlatItems()" in renderer, "native GUI lighting transition must be preserved")

# Independent reference model for today's straight-row topology. This checks the properties the
# player actually sees: stronger side rotation as length grows, natural near/far perspective,
# and a genuine internal margin instead of a 92x72 edge-to-edge fit.
VIEWPORT_WIDTH = 92.0
VIEWPORT_HEIGHT = 72.0
FIT_WIDTH = 84.0
FIT_HEIGHT = 58.0
NATIVE_SCALE = 42.0
NATIVE_YAW = 225.0
MAX_SIDE_YAW = 262.0
NATIVE_PITCH = 30.0
MIN_SIDE_PITCH = 6.0
MAX_CAMERA_SPAN = 64.0
PERSPECTIVE_DEPTH_FRACTION = 0.30
MIN_PERSPECTIVE_W = 0.65

require(FIT_WIDTH <= VIEWPORT_WIDTH - 8.0, "preview lost horizontal safety margin")
require(FIT_HEIGHT <= VIEWPORT_HEIGHT - 12.0, "preview lost vertical/module headroom")


def camera_progress(length: int) -> float:
    if length <= 2:
        return 0.0
    normalized = math.log(length / 2.0) / math.log(MAX_CAMERA_SPAN / 2.0)
    return math.sqrt(max(0.0, min(1.0, normalized)))


def camera_angles(length: int) -> tuple[float, float]:
    progress = camera_progress(length)
    yaw = NATIVE_YAW + (MAX_SIDE_YAW - NATIVE_YAW) * progress
    pitch = NATIVE_PITCH + (MIN_SIDE_PITCH - NATIVE_PITCH) * progress
    return yaw, pitch


def perspective_distance(length: int) -> float:
    radius = 0.5 * math.sqrt(length * length + 1.0 + 1.0)
    return radius / PERSPECTIVE_DEPTH_FRACTION


def camera_vertex(length: int, x: float, y: float, z: float, yaw: float, pitch: float) -> tuple[float, float, float]:
    local_x = x - length * 0.5
    local_y = y - 0.5
    local_z = z - 0.5
    yaw_radians = math.radians(yaw)
    pitch_radians = math.radians(pitch)
    cos_yaw = math.cos(yaw_radians)
    sin_yaw = math.sin(yaw_radians)
    cos_pitch = math.cos(pitch_radians)
    sin_pitch = math.sin(pitch_radians)

    yaw_x = cos_yaw * local_x + sin_yaw * local_z
    yaw_z = -sin_yaw * local_x + cos_yaw * local_z
    camera_y = cos_pitch * local_y - sin_pitch * yaw_z
    camera_z = sin_pitch * local_y + cos_pitch * yaw_z
    return yaw_x, camera_y, camera_z


def project_vertex(length: int, x: float, y: float, z: float, yaw: float, pitch: float) -> tuple[float, float, float]:
    camera_x, camera_y, camera_z = camera_vertex(length, x, y, z, yaw, pitch)
    w = max(MIN_PERSPECTIVE_W, 1.0 - camera_z / perspective_distance(length))
    return camera_x / w, camera_y / w, camera_z / w


def projected_row(length: int, yaw: float, pitch: float) -> tuple[float, float]:
    xs: list[float] = []
    ys: list[float] = []
    for member_x in range(length):
        for x in (float(member_x), float(member_x + 1)):
            for y in (0.0, 1.0):
                for z in (0.0, 1.0):
                    px, py, _ = project_vertex(length, x, y, z, yaw, pitch)
                    xs.append(px)
                    ys.append(py)
    return max(xs) - min(xs), max(ys) - min(ys)


def fitted_screen_size(length: int, yaw: float, pitch: float) -> tuple[float, float, float]:
    width, height = projected_row(length, yaw, pitch)
    scale = min(NATIVE_SCALE, FIT_WIDTH / width, FIT_HEIGHT / height)
    return width * scale, height * scale, scale


def member_screen_width(length: int, member: int, yaw: float, pitch: float) -> float:
    xs: list[float] = []
    for x in (float(member), float(member + 1)):
        for y in (0.0, 1.0):
            for z in (0.0, 1.0):
                px, _, _ = project_vertex(length, x, y, z, yaw, pitch)
                xs.append(px)
    return max(xs) - min(xs)


def member_center_camera_z(length: int, member: int, yaw: float, pitch: float) -> float:
    return camera_vertex(length, member + 0.5, 0.5, 0.5, yaw, pitch)[2]


expected_angles = {
    2: (225.0, 30.0),
    3: (237.7, 21.8),
    4: (241.5, 19.3),
    8: (248.4, 14.8),
    16: (253.7, 11.4),
    32: (258.1, 8.5),
    64: (262.0, 6.0),
}
previous_yaw = -math.inf
previous_pitch = math.inf
for length, (expected_yaw, expected_pitch) in expected_angles.items():
    yaw, pitch = camera_angles(length)
    require(abs(yaw - expected_yaw) < 0.15, f"{length}-desk yaw {yaw:.2f} no longer follows the visible side-view curve")
    require(abs(pitch - expected_pitch) < 0.15, f"{length}-desk pitch {pitch:.2f} no longer follows the side-view curve")
    require(yaw > previous_yaw, f"{length}-desk yaw must increase with multiblock length")
    require(pitch < previous_pitch, f"{length}-desk pitch must decrease as the row turns into depth")
    previous_yaw = yaw
    previous_pitch = pitch

    screen_width, screen_height, _ = fitted_screen_size(length, yaw, pitch)
    require(screen_width <= FIT_WIDTH + 0.01, f"{length}-desk preview exceeds horizontal safe fit")
    require(screen_height <= FIT_HEIGHT + 0.01, f"{length}-desk preview exceeds vertical safe fit")
    require(screen_height >= 54.0, f"{length}-desk preview became unnecessarily tiny")

for length in (4, 8, 16, 32, 64):
    yaw, pitch = camera_angles(length)
    far_member = 0
    near_member = length - 1
    far_z = member_center_camera_z(length, far_member, yaw, pitch)
    near_z = member_center_camera_z(length, near_member, yaw, pitch)
    require(
        far_z < 0.0 < near_z,
        f"{length}-desk camera convention changed: farZ={far_z:.3f}, nearZ={near_z:.3f}",
    )
    near_width = member_screen_width(length, near_member, yaw, pitch)
    far_width = member_screen_width(length, far_member, yaw, pitch)
    require(
        far_width < near_width * 0.72,
        f"{length}-desk natural perspective too weak: near={near_width:.3f}, far={far_width:.3f}",
    )

require("python3 tools/verify-console-multiblock-preview.py" in workflow, "workflow must enforce multiblock preview source contract")
require("python3 tools/verify-aeroworks-console-preview-bytecode.py" in workflow, "workflow must pin the Aeroworks preview bytecode contract")

print("Validated natural-perspective ControlDesk preview, stronger length-driven side rotation, module headroom, revision cache, Kotlin-safe Flywheel lighting and native fallbacks.")
