package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType

object DeskDisplayRenderer {
    private const val SPACING = 0.18
    private const val PIXEL_X_SPACING = 0.045
    private const val PIXEL_Z_SPACING = 0.05

    @JvmStatic
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        AeroworksDeskAccess.displays(desk).forEach { display ->
            val socket = sockets.getOrNull(display.socket) ?: return@forEach
            val elements = if (display.pixels != null) {
                buildList {
                    for (y in 0 until display.pixels.height) for (x in 0 until display.pixels.width) {
                        if (display.pixels.get(x, y)) add(Triple(DeskDisplayModels.PIXEL, pixelOffsetX(display.pixels.width, x), pixelOffsetZ(y)))
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

    @JvmStatic
    fun digitOffset(width: Int, digit: Int): Double = -(width - 1) * SPACING / 2.0 + digit * SPACING

    @JvmStatic
    fun pixelOffsetX(width: Int, x: Int): Double = -(width - 1) * PIXEL_X_SPACING / 2.0 + x * PIXEL_X_SPACING

    @JvmStatic
    fun pixelOffsetZ(y: Int): Double = (de.teutonstudio.ccaeroworks.display.DeskDisplayPixels.HEIGHT - 1) * PIXEL_Z_SPACING / 2.0 - y * PIXEL_Z_SPACING
}
