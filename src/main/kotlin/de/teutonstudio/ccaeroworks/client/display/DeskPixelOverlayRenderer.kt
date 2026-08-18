package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.Collections
import java.util.WeakHashMap

/**
 * Shared programmable-display pass for Flywheel-backed Aeroworks consoles.
 *
 * Flywheel continues to own cheap persistent text-segment instances. Pixel rasters are rendered
 * here as one dynamic-texture quad per display so high PPB values no longer produce thousands of
 * model renders or persistent instances.
 */
object DeskPixelOverlayRenderer {
    private val trackedDesks = Collections.newSetFromMap(
        WeakHashMap<ConsoleBlockEntity, Boolean>()
    )

    @JvmStatic
    fun track(desk: ConsoleBlockEntity) {
        if (hasPixelDisplay(desk)) {
            trackedDesks.add(desk)
        } else {
            trackedDesks.remove(desk)
            DeskDisplayTextureCache.release(desk)
        }
    }

    @JvmStatic
    fun renderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: run {
            trackedDesks.forEach(DeskDisplayTextureCache::release)
            trackedDesks.clear()
            return
        }
        if (trackedDesks.isEmpty()) return

        val camera = event.camera.position
        val partialTicks = event.partialTick.getGameTimeDeltaPartialTick(true)
        val poseStack = event.poseStack
        val buffers = minecraft.renderBuffers().bufferSource()
        var renderedAny = false

        val iterator = trackedDesks.iterator()
        while (iterator.hasNext()) {
            val desk = iterator.next()
            if (desk.isRemoved || desk.level !== level || !hasPixelDisplay(desk)) {
                DeskDisplayTextureCache.release(desk)
                iterator.remove()
                continue
            }

            poseStack.pushPose()
            try {
                SableClientRenderPose.apply(
                    poseStack,
                    desk,
                    desk.blockPos.x.toDouble(),
                    desk.blockPos.y.toDouble(),
                    desk.blockPos.z.toDouble(),
                    camera,
                    partialTicks
                )
                DeskDisplayRenderer.renderPixels(
                    desk,
                    poseStack,
                    buffers,
                    LevelRenderer.getLightColor(level, desk.blockPos)
                )
                renderedAny = true
            } finally {
                poseStack.popPose()
            }
        }

        if (renderedAny) buffers.endBatch()
    }

    private fun hasPixelDisplay(desk: ConsoleBlockEntity): Boolean =
        AeroworksDeskAccess.renderedDisplays(desk).any { it.pixels != null }
}
