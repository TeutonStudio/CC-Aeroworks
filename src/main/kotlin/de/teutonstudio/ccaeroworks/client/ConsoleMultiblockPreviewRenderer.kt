package de.teutonstudio.ccaeroworks.client

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ModulePartRender
import com.mred231.aeroworks.content.controls.ModulePartials
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSnapshot
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Replaces Aeroworks' one-desk overview preview with a compact perspective view of the
 * complete connected ControlDesk multiblock. All members are first transformed into one
 * shared camera space. A vertex consumer then applies one perspective projection to every
 * desk and module vertex, so the far end genuinely becomes smaller instead of merely being
 * shifted by an orthographic rotation.
 */
object ConsoleMultiblockPreviewRenderer {
    private const val AEROWORKS_WINDOW_WIDTH = 198
    private const val PREVIEW_OFFSET_X = 34
    private const val PREVIEW_BASE_Y = 76
    private const val PREVIEW_OFFSET_Y = 41

    private const val VIEWPORT_WIDTH = 92
    private const val VIEWPORT_HEIGHT = 72
    private const val VIEWPORT_PADDING = 2

    // Do not fit the desk AABBs all the way to the scissor rectangle. Aeroworks modules can
    // protrude above the nominal one-block desk volume, so a real internal frame is required.
    private const val FIT_WIDTH = 84.0
    private const val FIT_HEIGHT = 58.0
    private const val PREVIEW_VERTICAL_BIAS = 2.0F

    private const val NATIVE_SCALE = 42.0F
    private const val NATIVE_PITCH = 30.0F
    private const val NATIVE_YAW = 225.0F
    private const val MAX_SIDE_YAW = 262.0F
    private const val MIN_SIDE_PITCH = 6.0F
    private const val MAX_CAMERA_SPAN = 64.0

    // The camera distance is derived from the scene bounding sphere. With 0.30 the closest
    // possible vertex still has homogeneous w >= 0.70 at every yaw/pitch, while the far end
    // is visibly smaller. This keeps perspective strength stable as the multiblock grows.
    private const val PERSPECTIVE_DEPTH_FRACTION = 0.30
    private const val MIN_PERSPECTIVE_W = 0.65

    private const val GUI_Z = 200.0F
    private const val FULL_BRIGHT = 0xF000F0

    private val canonicalFacing = Direction.NORTH
    private val restValues = ModulePartRender.ChannelValues { 0.0F }

    @Volatile
    private var cachedPreview: CachedPreview? = null

    /**
     * @return true when the native one-desk preview should be cancelled.
     */
    fun render(
        graphics: GuiGraphics,
        console: ConsoleBlockEntity,
        windowLeft: Int,
        windowTop: Int
    ): Boolean {
        val level = console.level ?: return false
        val snapshot = ConsoleMultiblockManager.resolve(level, console.blockPos)
        if (snapshot.members.size <= 1) return false
        if (snapshot.state == ConsoleNetworkState.PARTIALLY_LOADED ||
            snapshot.state == ConsoleNetworkState.TOO_LARGE
        ) {
            return false
        }

        val preview = previewFor(snapshot)
        val scene = preview.scene
        val layout = preview.layout
        if (scene.members.size <= 1) return false

        val centerX = windowLeft + AEROWORKS_WINDOW_WIDTH + PREVIEW_OFFSET_X
        val centerY = windowTop + PREVIEW_BASE_Y + PREVIEW_OFFSET_Y
        val scissorLeft = centerX - VIEWPORT_WIDTH / 2 - VIEWPORT_PADDING
        val scissorTop = centerY - VIEWPORT_HEIGHT / 2 - VIEWPORT_PADDING
        val scissorRight = centerX + VIEWPORT_WIDTH / 2 + VIEWPORT_PADDING
        val scissorBottom = centerY + VIEWPORT_HEIGHT / 2 + VIEWPORT_PADDING

        val buffers = graphics.bufferSource()
        val cutout = buffers.getBuffer(RenderType.cutout())
        val perspectiveConsumer = PerspectiveVertexConsumer(
            cutout,
            centerX.toFloat(),
            centerY.toFloat() + PREVIEW_VERTICAL_BIAS,
            layout
        )

        // Keep the model transform in camera space. Catnip's SuperByteBuffer applies this pose
        // on the CPU before calling VertexConsumer.addVertex(), so the perspective divide has
        // to happen in PerspectiveVertexConsumer after the shared rotations have been applied.
        val cameraPose = PoseStack()
        cameraPose.pushPose()

        graphics.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom)
        try {
            // Both rotations are collective. The length-dependent camera below increasingly
            // looks along a long desk row instead of solving the problem by shrinking it.
            cameraPose.mulPose(Axis.XP.rotationDegrees(layout.pitch))
            cameraPose.mulPose(Axis.YP.rotationDegrees(layout.yaw))
            cameraPose.translate(-layout.centerX, -layout.centerY, -layout.centerZ)

            scene.members.forEach { member ->
                cameraPose.pushPose()
                try {
                    cameraPose.translate(member.x, member.y, member.z)
                    val state = normalizedPreviewState(member.desk.blockState)
                    val deskBuffer = CachedBuffers.block(state)
                    deskBuffer.light<SuperByteBuffer>(FULL_BRIGHT)
                    deskBuffer.renderInto(cameraPose, perspectiveConsumer)
                    renderModules(member.desk, state, cameraPose, perspectiveConsumer)
                } finally {
                    cameraPose.popPose()
                }
            }

            // Match Aeroworks' native GUI preview flush/lighting order and flush once for the
            // whole multiblock instead of once per desk.
            Lighting.setupFor3DItems()
            buffers.endBatch()
        } finally {
            Lighting.setupForFlatItems()
            cameraPose.popPose()
            graphics.disableScissor()
        }
        return true
    }

    private fun renderModules(
        desk: ConsoleBlockEntity,
        previewState: BlockState,
        poseStack: PoseStack,
        consumer: VertexConsumer
    ) {
        val sockets = desk.sockets()
        for (socketIndex in sockets.indices) {
            val mounted = desk.module(socketIndex) ?: continue
            val socket = sockets[socketIndex]
            val offset = socket.offset()

            ModulePartRender.flatten(mounted, socket.type()).forEach { part ->
                val partial = ModulePartials.get(part.model()) ?: return@forEach
                val partBuffer = CachedBuffers.partial(partial, previewState)
                partBuffer.translate(0.5, 0.0, 0.5)
                partBuffer.rotateYDegrees(-canonicalFacing.toYRot())
                partBuffer.translate(offset.x - 0.5, offset.y, offset.z - 0.5)
                partBuffer.rotate(socket.orientation())
                partBuffer.translate(-0.5, 0.0, -0.5)
                ModulePartRender.apply(
                    partBuffer,
                    part,
                    ModulePartRender.displayValues(mounted, restValues),
                    socket.orientation(),
                    false
                )
                partBuffer.light<SuperByteBuffer>(FULL_BRIGHT)
                partBuffer.renderInto(poseStack, consumer)
            }
        }
    }

    private fun normalizedPreviewState(source: BlockState): BlockState {
        var state = source
        val facing = state.properties
            .filterIsInstance<DirectionProperty>()
            .firstOrNull { it.name == "facing" }
        if (facing != null && canonicalFacing in facing.possibleValues) {
            state = state.setValue(facing, canonicalFacing)
        }
        val ceiling = state.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == "ceiling" }
        if (ceiling != null) state = state.setValue(ceiling, false)
        return state
    }

    /**
     * Scene geometry and its camera layout depend only on the multiblock revision. The desk
     * references remain live, so module/value rendering still observes current block entities.
     */
    private fun previewFor(snapshot: ConsoleMultiblockSnapshot): CachedPreview {
        val key = PreviewKey(
            snapshot.anchor.asLong(),
            snapshot.revision,
            VIEWPORT_WIDTH,
            VIEWPORT_HEIGHT
        )
        cachedPreview?.takeIf { it.key == key }?.let { return it }

        val scene = buildScene(snapshot)
        val preview = CachedPreview(key, scene, layoutFor(scene))
        cachedPreview = preview
        return preview
    }

    private fun buildScene(snapshot: ConsoleMultiblockSnapshot): Scene {
        val first = snapshot.members.first()
        val origin = first.pos
        val facing = first.facing
        val right = facing.clockWise
        val members = snapshot.members.map { member ->
            val dx = member.pos.x - origin.x
            val dy = member.pos.y - origin.y
            val dz = member.pos.z - origin.z
            LocalMember(
                desk = member.desk,
                x = (dx * right.stepX + dz * right.stepZ).toDouble(),
                y = dy.toDouble(),
                // World-forward maps to canonical NORTH, hence the sign inversion.
                z = -(dx * facing.stepX + dz * facing.stepZ).toDouble()
            )
        }

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        members.forEach { member ->
            minX = min(minX, member.x)
            minY = min(minY, member.y)
            minZ = min(minZ, member.z)
            maxX = max(maxX, member.x + 1.0)
            maxY = max(maxY, member.y + 1.0)
            maxZ = max(maxZ, member.z + 1.0)
        }
        return Scene(members, Bounds(minX, minY, minZ, maxX, maxY, maxZ))
    }

    private fun layoutFor(scene: Scene): Layout {
        val bounds = scene.bounds
        val cameraProgress = cameraProgress(scene)
        val yaw = lerp(NATIVE_YAW, MAX_SIDE_YAW, cameraProgress)
        val pitch = lerp(NATIVE_PITCH, MIN_SIDE_PITCH, cameraProgress)
        val perspectiveDistance = perspectiveDistance(scene)
        val projected = projectedBounds(scene, yaw, pitch, perspectiveDistance)
        val fittedScale = fittedScale(projected).toFloat()

        return Layout(
            yaw = yaw,
            pitch = pitch,
            scale = fittedScale,
            perspectiveDistance = perspectiveDistance.toFloat(),
            projectedCenterX = projected.centerX.toFloat(),
            projectedCenterY = projected.centerY.toFloat(),
            centerX = (bounds.minX + bounds.maxX) * 0.5,
            centerY = (bounds.minY + bounds.maxY) * 0.5,
            centerZ = (bounds.minZ + bounds.maxZ) * 0.5
        )
    }

    /**
     * Long rows should visibly turn into depth instead of being reduced mostly by uniform scale.
     * A logarithmic span makes the change noticeable already for 3-8 desks; sqrt then eases the
     * transition toward the 64-member limit without snapping the last few sizes edge-on.
     */
    private fun cameraProgress(scene: Scene): Float {
        val horizontalSpan = max(scene.bounds.width, scene.bounds.depth)
        if (horizontalSpan <= 2.0) return 0.0F

        val normalized = (
            ln(horizontalSpan / 2.0) /
                ln(MAX_CAMERA_SPAN / 2.0)
            ).coerceIn(0.0, 1.0)
        return sqrt(normalized).toFloat()
    }

    /**
     * Use the bounding-sphere radius so the perspective strength is independent of yaw/pitch.
     * Because rotations preserve radius, distance = radius / fraction guarantees the nearest
     * possible vertex stays in front of the perspective singularity for every camera angle.
     */
    private fun perspectiveDistance(scene: Scene): Double {
        val halfWidth = scene.bounds.width * 0.5
        val halfHeight = scene.bounds.height * 0.5
        val halfDepth = scene.bounds.depth * 0.5
        val radius = sqrt(
            halfWidth * halfWidth +
                halfHeight * halfHeight +
                halfDepth * halfDepth
        )
        return max(radius / PERSPECTIVE_DEPTH_FRACTION, 0.001)
    }

    private fun fittedScale(projected: ProjectedBounds): Double = minOf(
        NATIVE_SCALE.toDouble(),
        FIT_WIDTH / projected.width,
        FIT_HEIGHT / projected.height
    )

    /**
     * Mirrors the actual camera transform and perspective divide for every unit-block corner.
     * For each camera-space vertex the GUI position is x/w, y/w with
     * w = 1 - cameraZ / perspectiveDistance. In this camera convention positive Z points
     * toward the viewer, so nearby geometry grows while negative-Z distant geometry shrinks.
     */
    private fun projectedBounds(
        scene: Scene,
        yaw: Float,
        pitch: Float,
        perspectiveDistance: Double
    ): ProjectedBounds {
        val centerX = (scene.bounds.minX + scene.bounds.maxX) * 0.5
        val centerY = (scene.bounds.minY + scene.bounds.maxY) * 0.5
        val centerZ = (scene.bounds.minZ + scene.bounds.maxZ) * 0.5
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val cosYaw = cos(yawRadians)
        val sinYaw = sin(yawRadians)
        val cosPitch = cos(pitchRadians)
        val sinPitch = sin(pitchRadians)

        var minProjectedX = Double.POSITIVE_INFINITY
        var maxProjectedX = Double.NEGATIVE_INFINITY
        var minProjectedY = Double.POSITIVE_INFINITY
        var maxProjectedY = Double.NEGATIVE_INFINITY

        scene.members.forEach { member ->
            for (x in doubleArrayOf(member.x, member.x + 1.0)) {
                for (y in doubleArrayOf(member.y, member.y + 1.0)) {
                    for (z in doubleArrayOf(member.z, member.z + 1.0)) {
                        val localX = x - centerX
                        val localY = y - centerY
                        val localZ = z - centerZ

                        // PoseStack multiplication in render() yields Y first, then X.
                        val yawX = cosYaw * localX + sinYaw * localZ
                        val yawZ = -sinYaw * localX + cosYaw * localZ
                        val cameraY = cosPitch * localY - sinPitch * yawZ
                        val cameraZ = sinPitch * localY + cosPitch * yawZ
                        val perspective = perspectiveFactor(cameraZ, perspectiveDistance)
                        val projectedX = yawX * perspective
                        val projectedY = cameraY * perspective

                        minProjectedX = min(minProjectedX, projectedX)
                        maxProjectedX = max(maxProjectedX, projectedX)
                        minProjectedY = min(minProjectedY, projectedY)
                        maxProjectedY = max(maxProjectedY, projectedY)
                    }
                }
            }
        }

        return ProjectedBounds(
            minX = minProjectedX,
            maxX = maxProjectedX,
            minY = minProjectedY,
            maxY = maxProjectedY
        )
    }

    private fun perspectiveFactor(cameraZ: Double, perspectiveDistance: Double): Double =
        1.0 / max(MIN_PERSPECTIVE_W, 1.0 - cameraZ / perspectiveDistance)

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount

    /**
     * Catnip applies the supplied PoseStack to every SuperByteBuffer vertex on the CPU and then
     * calls addVertex(x, y, z), discarding homogeneous w. Applying perspective here therefore
     * preserves it for desk and module vertices without changing Minecraft's global GUI matrix.
     */
    private class PerspectiveVertexConsumer(
        private val delegate: VertexConsumer,
        private val centerX: Float,
        private val centerY: Float,
        private val layout: Layout
    ) : VertexConsumer {
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
            val denominator = max(
                MIN_PERSPECTIVE_W.toFloat(),
                1.0F - z / layout.perspectiveDistance
            )
            val projectedX = x / denominator
            val projectedY = y / denominator
            val projectedZ = z / denominator

            delegate.addVertex(
                centerX + layout.scale * (projectedX - layout.projectedCenterX),
                centerY - layout.scale * (projectedY - layout.projectedCenterY),
                GUI_Z + layout.scale * projectedZ
            )
            return this
        }

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
            delegate.setColor(red, green, blue, alpha)
            return this
        }

        override fun setUv(u: Float, v: Float): VertexConsumer {
            delegate.setUv(u, v)
            return this
        }

        override fun setUv1(u: Int, v: Int): VertexConsumer {
            delegate.setUv1(u, v)
            return this
        }

        override fun setUv2(u: Int, v: Int): VertexConsumer {
            delegate.setUv2(u, v)
            return this
        }

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
            delegate.setNormal(x, y, z)
            return this
        }
    }

    private data class LocalMember(
        val desk: ConsoleBlockEntity,
        val x: Double,
        val y: Double,
        val z: Double
    )

    private data class Scene(val members: List<LocalMember>, val bounds: Bounds)

    private data class Bounds(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    ) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
        val depth: Double get() = maxZ - minZ
    }

    private data class ProjectedBounds(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double
    ) {
        val width: Double get() = max(abs(maxX - minX), 0.001)
        val height: Double get() = max(abs(maxY - minY), 0.001)
        val centerX: Double get() = (minX + maxX) * 0.5
        val centerY: Double get() = (minY + maxY) * 0.5
    }

    private data class Layout(
        val yaw: Float,
        val pitch: Float,
        val scale: Float,
        val perspectiveDistance: Float,
        val projectedCenterX: Float,
        val projectedCenterY: Float,
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double
    )

    private data class PreviewKey(
        val anchor: Long,
        val revision: Long,
        val width: Int,
        val height: Int
    )

    private data class CachedPreview(
        val key: PreviewKey,
        val scene: Scene,
        val layout: Layout
    )
}
