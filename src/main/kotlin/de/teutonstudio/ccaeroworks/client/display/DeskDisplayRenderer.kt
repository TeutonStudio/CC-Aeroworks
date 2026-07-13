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

    @JvmStatic
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        AeroworksDeskAccess.displays(desk).forEach { display ->
            val socket = sockets.getOrNull(display.socket) ?: return@forEach
            val start = -(display.type.width - 1) * SPACING / 2.0
            display.text.padEnd(display.type.width, ' ').forEachIndexed { digit, character ->
                DeskDisplayModels.segments(character).forEach { segment ->
                    val rendered: SuperByteBuffer = CachedBuffers.partial(segment.model, desk.blockState)
                        .translate(0.5, 0.5, 0.5)
                        .rotate(rotation)
                        .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                        .rotate(socket.orientation())
                        .translate(-0.5, 0.0, -0.5)
                        .translate(start + digit * SPACING + segment.x, 0.0, segment.z)
                    rendered.light<SuperByteBuffer>(light)
                    rendered.renderInto(poseStack, consumer)
                }
            }
        }
    }

    @JvmStatic
    fun digitOffset(width: Int, digit: Int): Double = -(width - 1) * SPACING / 2.0 + digit * SPACING
}
