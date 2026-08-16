package de.teutonstudio.ccaeroworks.client

import com.mojang.blaze3d.platform.Lighting
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Replaces Aeroworks' one-desk overview preview with a compact view of the complete
 * connected ControlDesk row. The renderer intentionally mirrors Aeroworks 1.3.0's
 * native preview path: desk baked models and mounted module parts are rendered at
 * REST values, while world facing and ceiling placement are normalized for UI use.
 */
object ConsoleMultiblockPreviewRenderer {
    private const val AEROWORKS_WINDOW_WIDTH = 198
    private const val PREVIEW_OFFSET_X = 34
    private const val PREVIEW_BASE_Y = 76
    private const val PREVIEW_OFFSET_Y = 41

    private const val VIEWPORT_WIDTH = 92
    private const val VIEWPORT_HEIGHT = 72
    private const val VIEWPORT_PADDING = 2
    private const val NATIVE_SCALE = 42.0F
    private const val NATIVE_PITCH = 30.0F
    private const val NATIVE_YAW = 225.0F
    private const val MAX_YAW = 250.0F
    private const val MIN_PITCH = 18.0F
    private const val CAMERA_TRANSITION_SPAN = 7.0
    private const val GUI_Z = 200.0F
    private const val FULL_BRIGHT = 0xF000F0

    private val canonicalFacing = Direction.NORTH
    private val restValues = ModulePartRender.ChannelValues { 0.0F }

    @Volatile
    private var cachedLayout: CachedLayout? = null

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

        val scene = buildScene(snapshot)
        if (scene.members.size <= 1) return false
        val layout = layoutFor(snapshot, scene.bounds)

        val centerX = windowLeft + AEROWORKS_WINDOW_WIDTH + PREVIEW_OFFSET_X
        val centerY = windowTop + PREVIEW_BASE_Y + PREVIEW_OFFSET_Y
        val scissorLeft = centerX - VIEWPORT_WIDTH / 2 - VIEWPORT_PADDING
        val scissorTop = centerY - VIEWPORT_HEIGHT / 2 - VIEWPORT_PADDING
        val scissorRight = centerX + VIEWPORT_WIDTH / 2 + VIEWPORT_PADDING
        val scissorBottom = centerY + VIEWPORT_HEIGHT / 2 + VIEWPORT_PADDING

        val poseStack = graphics.pose()
        val buffers = graphics.bufferSource()
        val cutout = buffers.getBuffer(RenderType.cutout())

        graphics.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom)
        poseStack.pushPose()
        try {
            poseStack.translate(centerX.toFloat(), centerY.toFloat(), GUI_Z)
            poseStack.scale(layout.scale, -layout.scale, layout.scale)
            poseStack.mulPose(Axis.XP.rotationDegrees(layout.pitch))
            poseStack.mulPose(Axis.YP.rotationDegrees(layout.yaw))
            poseStack.translate(-layout.centerX, -layout.centerY, -layout.centerZ)

            scene.members.forEach { member ->
                poseStack.pushPose()
                try {
                    poseStack.translate(member.x, member.y, member.z)
                    val state = normalizedPreviewState(member.desk.blockState)
                    val deskBuffer = CachedBuffers.block(state)
                    deskBuffer.light<SuperByteBuffer>(FULL_BRIGHT)
                    deskBuffer.renderInto(poseStack, cutout)
                    renderModules(member.desk, state, poseStack, cutout)
                } finally {
                    poseStack.popPose()
                }
            }

            // Match Aeroworks' native GUI preview flush/lighting order and flush once for the
            // whole multiblock instead of once per desk.
            Lighting.setupFor3DItems()
            buffers.endBatch()
        } finally {
            Lighting.setupForFlatItems()
            poseStack.popPose()
            graphics.disableScissor()
        }
        return true
    }

    private fun renderModules(
        desk: ConsoleBlockEntity,
        previewState: BlockState,
        poseStack: com.mojang.blaze3d.vertex.PoseStack,
        consumer: com.mojang.blaze3d.vertex.VertexConsumer
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

    private fun layoutFor(snapshot: ConsoleMultiblockSnapshot, bounds: Bounds): Layout {
        val key = LayoutKey(snapshot.anchor.asLong(), snapshot.revision, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
        cachedLayout?.takeIf { it.key == key }?.let { return it.layout }

        val longestHorizontalSpan = max(bounds.width, bounds.depth)
        val transition = ((longestHorizontalSpan - 1.0) / CAMERA_TRANSITION_SPAN)
            .coerceIn(0.0, 1.0)
        val yaw = lerp(NATIVE_YAW, MAX_YAW, transition.toFloat())
        val pitch = lerp(NATIVE_PITCH, MIN_PITCH, transition.toFloat())
        val projected = projectedSize(bounds, yaw, pitch)
        val fittedScale = minOf(
            NATIVE_SCALE.toDouble(),
            VIEWPORT_WIDTH / projected.first,
            VIEWPORT_HEIGHT / projected.second
        ).toFloat()
        val layout = Layout(
            yaw = yaw,
            pitch = pitch,
            scale = fittedScale,
            centerX = (bounds.minX + bounds.maxX) * 0.5,
            centerY = (bounds.minY + bounds.maxY) * 0.5,
            centerZ = (bounds.minZ + bounds.maxZ) * 0.5
        )
        cachedLayout = CachedLayout(key, layout)
        return layout
    }

    private fun projectedSize(bounds: Bounds, yaw: Float, pitch: Float): Pair<Double, Double> {
        val centerX = (bounds.minX + bounds.maxX) * 0.5
        val centerY = (bounds.minY + bounds.maxY) * 0.5
        val centerZ = (bounds.minZ + bounds.maxZ) * 0.5
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

        for (x in doubleArrayOf(bounds.minX, bounds.maxX)) {
            for (y in doubleArrayOf(bounds.minY, bounds.maxY)) {
                for (z in doubleArrayOf(bounds.minZ, bounds.maxZ)) {
                    val localX = x - centerX
                    val localY = y - centerY
                    val localZ = z - centerZ
                    val yawX = cosYaw * localX + sinYaw * localZ
                    val yawZ = -sinYaw * localX + cosYaw * localZ
                    val pitchY = cosPitch * localY - sinPitch * yawZ
                    minProjectedX = min(minProjectedX, yawX)
                    maxProjectedX = max(maxProjectedX, yawX)
                    minProjectedY = min(minProjectedY, pitchY)
                    maxProjectedY = max(maxProjectedY, pitchY)
                }
            }
        }
        return Pair(
            max(abs(maxProjectedX - minProjectedX), 0.001),
            max(abs(maxProjectedY - minProjectedY), 0.001)
        )
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount

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
        val depth: Double get() = maxZ - minZ
    }

    private data class Layout(
        val yaw: Float,
        val pitch: Float,
        val scale: Float,
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double
    )

    private data class LayoutKey(
        val anchor: Long,
        val revision: Long,
        val width: Int,
        val height: Int
    )

    private data class CachedLayout(val key: LayoutKey, val layout: Layout)
}
