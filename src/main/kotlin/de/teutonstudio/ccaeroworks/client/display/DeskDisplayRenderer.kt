package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture

object DeskDisplayRenderer {
    private const val SPACING = 0.18
    private const val PIXEL_SURFACE_Y = 2.251 / 16.0
    private const val PIXEL_COLOR_ARGB: Int = -230909

    @JvmStatic
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        DisplayRenderExtensions.track(desk)
        renderPixels(desk, poseStack, buffers, light)
        renderText(desk, poseStack, buffers, light)
    }

    /**
     * Pixel-only pass. Each programmable display is represented by one cached dynamic texture and
     * one quad instead of one model render for every enabled pixel.
     */
    @JvmStatic
    fun renderPixels(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val activeSockets = mutableSetOf<Int>()

        AeroworksDeskAccess.renderedDisplays(desk).forEach { display ->
            val pixels = display.pixels ?: return@forEach
            val socket = sockets.getOrNull(display.socket) ?: return@forEach
            activeSockets += display.socket

            val texture = DeskDisplayTextureCache.texture(desk, display.socket, pixels)
            val consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture))
            val halfWidth = display.type.surfaceWidthParts.toDouble() /
                (2.0 * DeskDisplayType.VANILLA_PARTS_PER_BLOCK)
            val halfHeight = display.type.surfaceHeightParts.toDouble() /
                (2.0 * DeskDisplayType.VANILLA_PARTS_PER_BLOCK)
            val minX = 0.5 - halfWidth
            val maxX = 0.5 + halfWidth
            val minZ = 0.5 - halfHeight
            val maxZ = 0.5 + halfHeight

            poseStack.pushPose()
            try {
                poseStack.translate(0.5, 0.5, 0.5)
                poseStack.mulPose(rotation)
                poseStack.translate(
                    socket.offset().x - 0.5,
                    socket.offset().y - 0.5,
                    socket.offset().z - 0.5
                )
                poseStack.mulPose(socket.orientation())
                poseStack.translate(-0.5, 0.0, -0.5)

                val pose = poseStack.last()
                // v=0 belongs to raster row 0, which historically sits on the +Z display edge.
                vertex(consumer, pose, minX, PIXEL_SURFACE_Y, maxZ, 0.0f, 0.0f, light)
                vertex(consumer, pose, maxX, PIXEL_SURFACE_Y, maxZ, 1.0f, 0.0f, light)
                vertex(consumer, pose, maxX, PIXEL_SURFACE_Y, minZ, 1.0f, 1.0f, light)
                vertex(consumer, pose, minX, PIXEL_SURFACE_Y, minZ, 0.0f, 1.0f, light)
            } finally {
                poseStack.popPose()
            }
        }

        DeskDisplayTextureCache.retain(desk, activeSockets)
    }

    private fun vertex(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        x: Double,
        y: Double,
        z: Double,
        u: Float,
        v: Float,
        light: Int
    ) {
        consumer.addVertex(pose, x.toFloat(), y.toFloat(), z.toFloat())
            .setColor(PIXEL_COLOR_ARGB)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0f, 1.0f, 0.0f)
    }

    @JvmStatic
    fun renderText(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val consumer = buffers.getBuffer(RenderType.cutout())
        AeroworksDeskAccess.renderedDisplays(desk).forEach { display ->
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
