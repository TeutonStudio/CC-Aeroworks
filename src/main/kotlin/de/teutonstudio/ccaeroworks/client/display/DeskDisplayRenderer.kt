package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplayFrames
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplaySnapshot
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import kotlin.math.abs
import kotlin.math.min

object DeskDisplayRenderer {
    private const val SPACING = 0.18
    private const val BASE_PIXEL_X_SPACING = 0.045
    private const val BASE_PIXEL_Z_SPACING = 0.05
    private const val SMALL_MAX_PIXEL_X_SPAN = 0.30
    private const val LARGE_MAX_PIXEL_X_SPAN = 0.48
    private const val MAX_PIXEL_Z_SPAN = 0.30

    // display_pixel.json is 0.56 model units wide/deep in a 16-unit block model.
    private const val MODEL_PIXEL_SPAN = 0.56 / 16.0
    private const val MERGE_EPSILON = 1.0e-6

    @JvmStatic
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        // RadarDisplay is rendered later in one shared world pass so classic and
        // Flywheel desks both use the exact same native Create: Radars renderer.
        RadarOverlayRenderer.track(desk)

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
                    socket,
                    rotation,
                    poseStack,
                    consumer,
                    light
                )
                return@forEach
            }

            val elements = if (display.pixels != null) {
                buildList {
                    for (y in 0 until display.pixels.height) for (x in 0 until display.pixels.width) {
                        if (display.pixels.get(x, y)) {
                            add(
                                Triple(
                                    DeskDisplayModels.PIXEL,
                                    pixelOffsetX(display.type, display.pixels.width, x),
                                    pixelOffsetZ(display.pixels.height, y)
                                )
                            )
                        }
                    }
                }
            } else {
                buildList {
                    display.text.padEnd(display.type.width, ' ').forEachIndexed { digit, character ->
                        DeskDisplayModels.segments(character).forEach { segment ->
                            add(Triple(segment.model, digitOffset(display.type.width, digit) + segment.x, segment.z))
                        }
                    }
                }
            }
            elements.forEach { (model, x, z) ->
                val rendered: SuperByteBuffer = CachedBuffers.partial(model, desk.blockState)
                    .translate(0.5, 0.5, 0.5)
                    .rotate(rotation)
                    .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                    .rotate(socket.orientation())
                    .translate(-0.5, 0.0, -0.5)
                    .translate(x, 0.0, z)
                rendered.light<SuperByteBuffer>(light)
                rendered.renderInto(poseStack, consumer)
            }
        }
    }

    private fun renderRuntimeFrame(
        desk: ConsoleBlockEntity,
        socketIndex: Int,
        type: DeskDisplayType,
        frame: ReactiveDisplaySnapshot,
        socket: com.mred231.aeroworks.content.controls.ConsoleSocket,
        rotation: org.joml.Quaternionf,
        poseStack: PoseStack,
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        light: Int
    ) {
        val spacingX = pixelSpacingX(type, frame.width)
        val spacingZ = pixelSpacingZ(frame.height)
        val mergeHorizontal = spacingX <= MODEL_PIXEL_SPAN + MERGE_EPSILON
        val mergeVertical = spacingZ <= MODEL_PIXEL_SPAN + MERGE_EPSILON
        val rectangles = ReactiveDisplayRenderCache.rectangles(
            desk,
            socketIndex,
            frame,
            mergeHorizontal,
            mergeVertical
        )

        rectangles.forEach { rectangle ->
            val firstX = pixelOffsetX(type, frame.width, rectangle.x)
            val lastX = pixelOffsetX(type, frame.width, rectangle.x + rectangle.width - 1)
            val firstZ = pixelOffsetZ(frame.height, rectangle.y)
            val lastZ = pixelOffsetZ(frame.height, rectangle.y + rectangle.height - 1)
            val centerX = (firstX + lastX) * 0.5
            val centerZ = (firstZ + lastZ) * 0.5
            val scaleX = (MODEL_PIXEL_SPAN + abs(lastX - firstX)) / MODEL_PIXEL_SPAN
            val scaleZ = (MODEL_PIXEL_SPAN + abs(lastZ - firstZ)) / MODEL_PIXEL_SPAN

            // A dense run is geometrically the same union as overlapping neighbouring pixel
            // cuboids, so render it as one centered, scaled pixel model. At low resolutions the
            // spacing remains larger than the model and every pixel keeps its visible gap.
            val rendered: SuperByteBuffer = CachedBuffers.partial(DeskDisplayModels.PIXEL, desk.blockState)
                .translate(0.5, 0.5, 0.5)
                .rotate(rotation)
                .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                .rotate(socket.orientation())
                .translate(-0.5, 0.0, -0.5)
                .translate(centerX + 0.5, 0.0, centerZ + 0.5)
                .scale(scaleX.toFloat(), 1.0f, scaleZ.toFloat())
                .translate(-0.5, 0.0, -0.5)
            rendered.light<SuperByteBuffer>(light)
            rendered.renderInto(poseStack, consumer)
        }
    }

    @JvmStatic
    fun digitOffset(width: Int, digit: Int): Double = -(width - 1) * SPACING / 2.0 + digit * SPACING

    @JvmStatic
    fun pixelOffsetX(type: DeskDisplayType, width: Int, x: Int): Double {
        if (width <= 1) return 0.0
        val spacing = pixelSpacingX(type, width)
        return -(width - 1) * spacing / 2.0 + x * spacing
    }

    @JvmStatic
    fun pixelOffsetX(width: Int, x: Int): Double =
        centeredOffset(width, x, BASE_PIXEL_X_SPACING, Double.POSITIVE_INFINITY)

    @JvmStatic
    fun pixelOffsetZ(height: Int, y: Int): Double {
        if (height <= 1) return 0.0
        val spacing = pixelSpacingZ(height)
        return (height - 1) * spacing / 2.0 - y * spacing
    }

    @JvmStatic
    fun pixelSpacingX(type: DeskDisplayType, width: Int): Double {
        if (width <= 1) return Double.POSITIVE_INFINITY
        val maxSpan = when (type) {
            DeskDisplayType.TWO_DIGIT -> SMALL_MAX_PIXEL_X_SPAN
            DeskDisplayType.THREE_DIGIT -> LARGE_MAX_PIXEL_X_SPAN
        }
        return min(BASE_PIXEL_X_SPACING, maxSpan / (width - 1))
    }

    @JvmStatic
    fun pixelSpacingZ(height: Int): Double {
        if (height <= 1) return Double.POSITIVE_INFINITY
        return min(BASE_PIXEL_Z_SPACING, MAX_PIXEL_Z_SPAN / (height - 1))
    }

    private fun centeredOffset(count: Int, index: Int, baseSpacing: Double, maxSpan: Double): Double {
        if (count <= 1) return 0.0
        val spacing = min(baseSpacing, maxSpan / (count - 1))
        return -(count - 1) * spacing / 2.0 + index * spacing
    }
}
