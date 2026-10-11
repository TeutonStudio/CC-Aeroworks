package de.teutonstudio.ccaeroworks.radarcompat.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks.RadarDeskAccess
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarLinkStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import java.util.Locale

/** Explain why an inactive surface has no native radar picture, without inventing radar data. */
object RadarStatusRenderer {
    fun render(desk: ConsoleBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource): Boolean {
        val level = desk.level ?: return false
        val font = Minecraft.getInstance().font
        val sockets = desk.sockets()
        var rendered = false
        for (surface in RadarDeskAccess.radarSurfaces(desk)) {
            val radarAvailable = surface.snapshot?.radarPos?.let { level.getBlockEntity(it) != null } ?: false
            val key = radarDisplayMessageKey(surface.snapshot, level.gameTime, radarAvailable) ?: continue
            val socket = sockets.getOrNull(surface.socket) ?: continue
            poseStack.pushPose()
            try {
                poseStack.translate(0.5, 0.5, 0.5)
                poseStack.mulPose(ConsoleBlock.rotationFor(desk.blockState))
                poseStack.translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                poseStack.mulPose(socket.orientation())
                poseStack.translate(0.0, 2.16 / 16.0, -0.06)
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
                val text = Component.translatable(key).visualOrderText
                val availableWidth = (surface.type.displayType.surfaceWidthParts - 1) / 16.0f
                val scale = minOf(0.003f, availableWidth / font.width(text).coerceAtLeast(1))
                poseStack.scale(scale, scale, scale)
                font.drawInBatch(
                    text, -font.width(text) / 2.0f, 0.0f, 0xFFFFC66D.toInt(), false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT
                )
                rendered = true
            } finally {
                poseStack.popPose()
            }
        }
        return rendered
    }
}

internal fun radarDisplayMessageKey(
    snapshot: RadarDisplaySnapshot?,
    gameTime: Long,
    radarAvailable: Boolean
): String? = when {
    snapshot == null -> "radar_status.cc_aeroworks.waiting"
    snapshot.status != RadarLinkStatus.ACTIVE -> "radar_status.cc_aeroworks.${snapshot.status.name.lowercase(Locale.ROOT)}"
    !RadarDisplaySnapshot.isFresh(snapshot, gameTime) -> "radar_status.cc_aeroworks.stale"
    !radarAvailable -> "radar_status.cc_aeroworks.radar_not_loaded"
    else -> null
}
