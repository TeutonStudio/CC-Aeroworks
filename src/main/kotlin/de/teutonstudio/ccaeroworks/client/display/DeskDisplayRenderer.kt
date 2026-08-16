package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplayFrames
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplaySnapshot
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import kotlin.math.abs

object DeskDisplayRenderer {
    private const val SPACING = 0.18
    private const val MODEL_PIXEL_SPAN_BLOCKS = 0.56 / 16.0
    private const val MERGE_EPSILON = 1.0e-6

    @JvmStatic
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        // RadarDisplay is rendered later in one shared world pass so classic and
        // Flywheel desks both use the exact same native Create: Radars renderer.
        RadarOverlayRenderer.track(desk)
        renderPixels(desk, poseStack, buffers, light)
        renderText(desk, poseStack, buffers, light)
    }

    /**
     * Pixel-only pass. Flywheel consoles call this from [DeskPixelOverlayRenderer] instead of
     * allocating one persistent TransformedInstance for every enabled pixel.
     */
    @JvmStatic
    fun renderPixels(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        AeroworksDeskAccess.renderedDisplays(desk).forEach { display ->
            val socket = sockets.getOrNull(display.socket) ?: return@forEach
            val runtimeFrame = ReactiveDisplayFrames.snapshot(desk, display.socket)
            if (runtimeFrame != null) {
                renderRuntimeFrame(
                    desk,
                    display.socket,
                    display.type,
                    runtimeFrame,
                    poseStack,
                    buffers,
                    light
                )
                return@forEach
            }

            val pixels = display.pixels ?: return@forEach
            val scale = display.type.pixelModelScale
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (!pixels.get(x, y)) continue
                val rendered: SuperByteBuffer = CachedBuffers.partial(DeskDisplayModels.PIXEL, desk.blockState)
                    .translate(0.5, 0.5, 0.5)
                    .rotate(rotation)
                    .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                    .rotate(socket.orientation())
                    .translate(-0.5, 0.0, -0.5)
                    .translate(
                        pixelOffsetX(display.type, pixels.width, x),
                        0.0,
                        pixelOffsetZ(display.type, pixels.height, y)
                    )
                    // Scale only the pixel partial itself. Doing this before desk/socket placement
                    // also scales all later translations and collapses a high-PPB raster into a line.
                    .translate(0.5, 0.0, 0.5)
                    .scale(scale, 1.0f, scale)
                    .translate(-0.5, 0.0, -0.5)
                rendered.light<SuperByteBuffer>(light)
                rendered.renderInto(poseStack, consumer)
            }
        }
    }

    @JvmStatic
    fun renderText(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        AeroworksDeskAccess.renderedDisplays(desk).forEach { display ->
            if (ReactiveDisplayFrames.snapshot(desk, display.socket) != null) return@forEach
            if (display.pixels != null) return@forEach
            val socket = sockets.getOrNull(display.socket) ?: return@forEach
            display.text.padEnd(display.type.width, ' ').forEachIndexed { digit, character ->
                DeskDisplayModels.segments(character).forEach { segment ->
                    val rendered: SuperByteBuffer = CachedBuffers.partial(segment.model, desk.blockState)
                        .translate(0.5, 0.5, 0.5)
                        .rotate(rotation)
                        .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                        .rotate(socket.orientation())
                        .translate(-0.5, 0.0, -0.5)
                        .translate(digitOffset(display.type.width, digit) + segment.x, 0.0, segment.z)
                    rendered.light<SuperByteBuffer>(light)
                    rendered.renderInto(poseStack, consumer)
                }
            }
        }
    }

    private fun renderRuntimeFrame(
        desk: ConsoleBlockEntity,
        socketIndex: Int,
        type: DeskDisplayType,
        frame: ReactiveDisplaySnapshot,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        light: Int
    ) {
        val socket = desk.sockets().getOrNull(socketIndex) ?: return
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        val singleSpan = MODEL_PIXEL_SPAN_BLOCKS * type.pixelModelScale.toDouble()
        val pitch = type.pixelPitchBlocks
        val canMerge = pitch <= singleSpan + MERGE_EPSILON
        val rectangles = ReactiveDisplayRenderCache.rectangles(
            desk,
            socketIndex,
            frame,
            canMerge,
            canMerge
        )

        rectangles.forEach { rectangle ->
            val firstX = pixelOffsetX(type, frame.width, rectangle.x)
            val lastX = pixelOffsetX(type, frame.width, rectangle.x + rectangle.width - 1)
            val firstZ = pixelOffsetZ(type, frame.height, rectangle.y)
            val lastZ = pixelOffsetZ(type, frame.height, rectangle.y + rectangle.height - 1)
            val centerX = (firstX + lastX) * 0.5
            val centerZ = (firstZ + lastZ) * 0.5
            val scaleX = (singleSpan + abs(lastX - firstX)) / MODEL_PIXEL_SPAN_BLOCKS
            val scaleZ = (singleSpan + abs(lastZ - firstZ)) / MODEL_PIXEL_SPAN_BLOCKS

            val rendered = CachedBuffers.partial(DeskDisplayModels.PIXEL, desk.blockState)
                .translate(0.5, 0.5, 0.5)
                .rotate(rotation)
                .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                .rotate(socket.orientation())
                .translate(-0.5, 0.0, -0.5)
                .translate(centerX, 0.0, centerZ)
                .translate(0.5, 0.0, 0.5)
                .scale(scaleX.toFloat(), 1.0f, scaleZ.toFloat())
                .translate(-0.5, 0.0, -0.5)
            rendered.light<SuperByteBuffer>(light)
            rendered.renderInto(poseStack, consumer)
        }
    }

    @JvmStatic
    fun digitOffset(width: Int, digit: Int): Double = -(width - 1) * SPACING / 2.0 + digit * SPACING

    @JvmStatic
    fun pixelOffsetX(type: DeskDisplayType, width: Int, x: Int): Double =
        centeredOffset(width, x, type.pixelPitchBlocks)

    @JvmStatic
    fun pixelOffsetZ(type: DeskDisplayType, height: Int, y: Int): Double =
        -centeredOffset(height, y, type.pixelPitchBlocks)

    /** Compatibility overload for code which only knows the current global raster density. */
    @JvmStatic
    fun pixelOffsetX(width: Int, x: Int): Double =
        centeredOffset(width, x, 1.0 / CCServerConfig.displayPartsPerBlockValue().toDouble())

    /** Compatibility overload for code which only knows the current global raster density. */
    @JvmStatic
    fun pixelOffsetZ(height: Int, y: Int): Double =
        -centeredOffset(height, y, 1.0 / CCServerConfig.displayPartsPerBlockValue().toDouble())

    private fun centeredOffset(count: Int, index: Int, spacing: Double): Double {
        if (count <= 1) return 0.0
        return -(count - 1) * spacing / 2.0 + index * spacing
    }
}
