package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import kotlin.math.min

object DeskDisplayRenderer {
    private const val SPACING = 0.18
    private const val BASE_PIXEL_X_SPACING = 0.045
    private const val BASE_PIXEL_Z_SPACING = 0.05
    private const val SMALL_MAX_PIXEL_X_SPAN = 0.30
    private const val LARGE_MAX_PIXEL_X_SPAN = 0.48
    private const val MAX_PIXEL_Z_SPAN = 0.30

    @JvmStatic
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        AeroworksDeskAccess.renderedDisplays(desk).forEach { display ->
            val socket = sockets.getOrNull(display.socket) ?: return@forEach
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

        RadarSurfaceRenderer.render(desk, poseStack, buffers, light)
    }

    @JvmStatic
    fun digitOffset(width: Int, digit: Int): Double = -(width - 1) * SPACING / 2.0 + digit * SPACING

    @JvmStatic
    fun pixelOffsetX(type: DeskDisplayType, width: Int, x: Int): Double {
        val maxSpan = when (type) {
            DeskDisplayType.TWO_DIGIT -> SMALL_MAX_PIXEL_X_SPAN
            DeskDisplayType.THREE_DIGIT -> LARGE_MAX_PIXEL_X_SPAN
        }
        return centeredOffset(width, x, BASE_PIXEL_X_SPACING, maxSpan)
    }

    @JvmStatic
    fun pixelOffsetX(width: Int, x: Int): Double =
        centeredOffset(width, x, BASE_PIXEL_X_SPACING, Double.POSITIVE_INFINITY)

    @JvmStatic
    fun pixelOffsetZ(height: Int, y: Int): Double {
        if (height <= 1) return 0.0
        val spacing = min(BASE_PIXEL_Z_SPACING, MAX_PIXEL_Z_SPAN / (height - 1))
        return (height - 1) * spacing / 2.0 - y * spacing
    }

    private fun centeredOffset(count: Int, index: Int, baseSpacing: Double, maxSpan: Double): Double {
        if (count <= 1) return 0.0
        val spacing = min(baseSpacing, maxSpan / (count - 1))
        return -(count - 1) * spacing / 2.0 + index * spacing
    }
}
