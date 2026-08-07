package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.Collections
import java.util.WeakHashMap

/**
 * Renders RadarDisplay surfaces once per frame after block entities.
 *
 * Both Aeroworks' Flywheel ConsoleVisual and the classic ConsoleRenderer only
 * register desks here. Keeping the native Create: Radars draw in this shared
 * stage prevents duplicate geometry and avoids maintaining a second Flywheel
 * approximation of MonitorRenderer.
 */
object RadarOverlayRenderer {
    private val trackedDesks = Collections.newSetFromMap(
        WeakHashMap<ConsoleBlockEntity, Boolean>()
    )

    @JvmStatic
    fun track(desk: ConsoleBlockEntity) {
        if (AeroworksDeskAccess.hasRadarDisplay(desk)) {
            trackedDesks.add(desk)
        } else {
            trackedDesks.remove(desk)
        }
    }

    @JvmStatic
    fun renderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: run {
            trackedDesks.clear()
            return
        }
        if (trackedDesks.isEmpty()) return

        val camera = event.camera.position
        val poseStack = event.poseStack
        val buffers = minecraft.renderBuffers().bufferSource()
        val partialTicks = event.partialTick.getGameTimeDeltaPartialTick(true)
        var renderedAny = false

        val iterator = trackedDesks.iterator()
        while (iterator.hasNext()) {
            val desk = iterator.next()
            if (
                desk.isRemoved ||
                desk.level !== level ||
                !AeroworksDeskAccess.hasRadarDisplay(desk)
            ) {
                iterator.remove()
                continue
            }

            poseStack.pushPose()
            try {
                poseStack.translate(
                    desk.blockPos.x - camera.x,
                    desk.blockPos.y - camera.y,
                    desk.blockPos.z - camera.z
                )
                renderedAny = CreateRadarNativeMonitorRenderer.render(
                    desk,
                    poseStack,
                    buffers,
                    partialTicks
                ) || renderedAny
            } finally {
                poseStack.popPose()
            }
        }

        if (renderedAny) {
            buffers.endBatch()
        }
    }
}
