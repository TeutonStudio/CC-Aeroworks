package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.createradar.RadarTrace
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource

/**
 * Draws fixed north-up cardinal labels on RadarDisplay surfaces.
 *
 * Create: Radars' monitor presentation is treated as north-up. The labels therefore
 * live entirely in the local display surface coordinate system. Desk rotation, socket
 * rotation and Sable transforms move the complete surface in the world, but no world
 * yaw or vehicle heading is applied to N/E/S/W themselves.
 */
object RadarCompassRenderer {
    private const val MODULE_SURFACE_Y = 2.17 / 16.0
    private const val COMPASS_RADIUS = 0.165
    private const val TEXT_SCALE = 0.0019f
    private const val TEXT_COLOR = -0x1f1f20

    private val CARDINALS = listOf(
        CardinalLabel("N", 0.0, -COMPASS_RADIUS),
        CardinalLabel("E", COMPASS_RADIUS, 0.0),
        CardinalLabel("S", 0.0, COMPASS_RADIUS),
        CardinalLabel("W", -COMPASS_RADIUS, 0.0)
    )

    @JvmStatic
    fun render(
        desk: ConsoleBlockEntity,
        poseStack: PoseStack,
        buffers: MultiBufferSource
    ): Boolean {
        val level = desk.level ?: return false
        val gameTime = level.gameTime
        val sockets = desk.sockets()
        val font = Minecraft.getInstance().font
        var renderedAny = false

        for (surface in AeroworksDeskAccess.radarSurfaces(desk)) {
            val snapshot = surface.snapshot ?: continue
            if (!RadarDisplaySnapshot.isFresh(snapshot, gameTime)) continue
            val socket = sockets.getOrNull(surface.socket) ?: continue

            poseStack.pushPose()
            try {
                poseStack.translate(0.5, 0.5, 0.5)
                poseStack.mulPose(ConsoleBlock.rotationFor(desk.blockState))
                poseStack.translate(
                    socket.offset().x - 0.5,
                    socket.offset().y - 0.5,
                    socket.offset().z - 0.5
                )
                poseStack.mulPose(socket.orientation())
                poseStack.translate(-0.5, 0.0, -0.5)

                CARDINALS.forEach { cardinal ->
                    drawCardinal(font, cardinal, poseStack, buffers)
                }

                renderedAny = true
                RadarTrace.periodic(
                    "H10_COMPASS_RENDER",
                    level,
                    desk.blockPos,
                    10L,
                    "socket=${surface.socket} type=${surface.type} northUp=true labels=N/E/S/W"
                )
            } finally {
                poseStack.popPose()
            }
        }

        return renderedAny
    }

    private fun drawCardinal(
        font: Font,
        cardinal: CardinalLabel,
        poseStack: PoseStack,
        buffers: MultiBufferSource
    ) {
        poseStack.pushPose()
        try {
            poseStack.translate(
                0.5 + cardinal.x,
                MODULE_SURFACE_Y,
                0.5 + cardinal.z
            )
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE)

            font.drawInBatch(
                cardinal.text,
                -font.width(cardinal.text) / 2.0f,
                -(font.lineHeight / 2.0f),
                TEXT_COLOR,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.POLYGON_OFFSET,
                0,
                LightTexture.FULL_BRIGHT
            )
        } finally {
            poseStack.popPose()
        }
    }

    private data class CardinalLabel(
        val text: String,
        val x: Double,
        val z: Double
    )
}
