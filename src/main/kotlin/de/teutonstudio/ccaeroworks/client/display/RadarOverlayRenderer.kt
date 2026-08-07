package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.createradar.RadarTrace
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
        val hasRadarDisplay = AeroworksDeskAccess.hasRadarDisplay(desk)
        if (hasRadarDisplay) {
            val newlyAdded = trackedDesks.add(desk)
            if (newlyAdded) {
                RadarTrace.event(
                    "R00_TRACK_ADD",
                    desk.level,
                    desk.blockPos,
                    "registered desk for native radar overlay; class=${desk.javaClass.name} trackedCount=${trackedDesks.size}"
                )
            }
            RadarTrace.periodic(
                "R00_TRACK_HEARTBEAT",
                desk.level,
                desk.blockPos,
                20L,
                "track() is being called; hasRadarDisplay=true tracked=${desk in trackedDesks} trackedCount=${trackedDesks.size}"
            )
        } else {
            val removed = trackedDesks.remove(desk)
            if (removed) {
                RadarTrace.event(
                    "R00_TRACK_REMOVE",
                    desk.level,
                    desk.blockPos,
                    "RadarDisplay no longer mounted; removed from overlay set trackedCount=${trackedDesks.size}"
                )
            }
        }
    }

    @JvmStatic
    fun renderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: run {
            if (trackedDesks.isNotEmpty()) {
                RadarTrace.event(
                    "R01_OVERLAY_NO_LEVEL",
                    null,
                    null,
                    "Minecraft.level is null; clearing ${trackedDesks.size} tracked desks"
                )
            }
            trackedDesks.clear()
            return
        }

        val camera = event.camera.position
        val partialTicks = event.partialTick.getGameTimeDeltaPartialTick(true)
        RadarTrace.periodic(
            "R01_OVERLAY_STAGE",
            level,
            null,
            10L,
            "AFTER_BLOCK_ENTITIES fired trackedCount=${trackedDesks.size} camera=$camera partialTicks=$partialTicks " +
                "buffers=${minecraft.renderBuffers().bufferSource().javaClass.name}"
        )
        if (trackedDesks.isEmpty()) {
            RadarTrace.periodic(
                "R02_OVERLAY_EMPTY",
                level,
                null,
                20L,
                "render stage is alive but no RadarDisplay desk is registered"
            )
            return
        }

        val poseStack = event.poseStack
        val buffers = minecraft.renderBuffers().bufferSource()
        var renderedAny = false

        val iterator = trackedDesks.iterator()
        while (iterator.hasNext()) {
            val desk = iterator.next()
            val removed = desk.isRemoved
            val wrongLevel = desk.level !== level
            val hasRadarDisplay = AeroworksDeskAccess.hasRadarDisplay(desk)
            if (removed || wrongLevel || !hasRadarDisplay) {
                RadarTrace.event(
                    "R03_OVERLAY_EVICT",
                    level,
                    desk.blockPos,
                    "isRemoved=$removed sameLevel=${!wrongLevel} hasRadarDisplay=$hasRadarDisplay"
                )
                iterator.remove()
                continue
            }

            RadarTrace.periodic(
                "R04_OVERLAY_DESK",
                level,
                desk.blockPos,
                10L,
                "attempting native render radarSurfaces=${AeroworksDeskAccess.radarSurfaces(desk).size} " +
                    "deskBlockState=${desk.blockState} socketCount=${desk.socketCount()}"
            )

            poseStack.pushPose()
            try {
                poseStack.translate(
                    desk.blockPos.x - camera.x,
                    desk.blockPos.y - camera.y,
                    desk.blockPos.z - camera.z
                )
                val rendered = CreateRadarNativeMonitorRenderer.render(
                    desk,
                    poseStack,
                    buffers,
                    partialTicks
                )
                renderedAny = rendered || renderedAny
                RadarTrace.periodic(
                    "R08_NATIVE_RETURN",
                    level,
                    desk.blockPos,
                    10L,
                    "CreateRadarNativeMonitorRenderer.render returned=$rendered aggregateRenderedAny=$renderedAny"
                )
            } catch (throwable: Throwable) {
                RadarTrace.event(
                    "R98_OVERLAY_EXCEPTION",
                    level,
                    desk.blockPos,
                    "${RadarTrace.throwable(throwable)}"
                )
                throw throwable
            } finally {
                poseStack.popPose()
            }
        }

        if (renderedAny) {
            RadarTrace.periodic(
                "R09_END_BATCH",
                level,
                null,
                10L,
                "at least one native monitor render invoked; flushing BufferSource.endBatch()"
            )
            buffers.endBatch()
        } else {
            RadarTrace.periodic(
                "R09_NO_DRAW",
                level,
                null,
                10L,
                "tracked desks exist but no native monitor surface reported a successful draw"
            )
        }
    }
}
