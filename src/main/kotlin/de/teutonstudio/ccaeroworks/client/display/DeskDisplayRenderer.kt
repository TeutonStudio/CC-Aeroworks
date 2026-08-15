package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType

object DeskDisplayRenderer {
    private const val SPACING = 0.18

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
            val elements = if (display.pixels != null) {
                buildList {
                    for (y in 0 until display.pixels.height) for (x in 0 until display.pixels.width) {
                        if (display.pixels.get(x, y)) {
                            add(
                                Triple(
                                    DeskDisplayModels.PIXEL,
                                    pixelOffsetX(display.type, display.pixels.width, x),
                                    pixelOffsetZ(display.type, display.pixels.height, y)
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
                if (model == DeskDisplayModels.PIXEL) {
                    val scale = display.type.pixelModelScale
                    rendered.center()
                        .scale(scale, 1.0f, scale)
                        .uncenter()
                }
                rendered
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

    @JvmStatic
    fun digitOffset(width: Int, digit: Int): Double = -(width - 1) * SPACING / 2.0 + digit * SPACING

    @JvmStatic
    fun pixelOffsetX(type: DeskDisplayType, width: Int, x: Int): Double =
        centeredOffset(width, x, type.pixelPitchBlocks)

    /** Compatibility overload for callers that only know the grid width. */
    @JvmStatic
    fun pixelOffsetX(width: Int, x: Int): Double =
        centeredOffset(width, x, currentPixelPitch())

    @JvmStatic
    fun pixelOffsetZ(type: DeskDisplayType, height: Int, y: Int): Double =
        -centeredOffset(height, y, type.pixelPitchBlocks)

    /** Compatibility overload for callers that only know the grid height. */
    @JvmStatic
    fun pixelOffsetZ(height: Int, y: Int): Double =
        -centeredOffset(height, y, currentPixelPitch())

    private fun currentPixelPitch(): Double = 1.0 / CCServerConfig.displayPartsPerBlockValue().toDouble()

    private fun centeredOffset(count: Int, index: Int, spacing: Double): Double {
        if (count <= 1) return 0.0
        return -(count - 1) * spacing / 2.0 + index * spacing
    }
}
