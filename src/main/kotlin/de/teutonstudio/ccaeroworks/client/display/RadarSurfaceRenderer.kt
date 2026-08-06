package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.display.RadarSurfaceState
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction

object RadarSurfaceRenderer {
    private const val TRACK_POSITION_SCALE = 0.75
    private const val SMALL_HALF_WIDTH = 0.17
    private const val LARGE_HALF_WIDTH = 0.245
    private const val HALF_HEIGHT = 0.17
    private const val SWEEP_PERIOD_TICKS = 120L

    data class Element(
        val model: PartialModel,
        val key: String,
        val x: Double = 0.0,
        val z: Double = 0.0,
        val spinning: Boolean = false,
        val translucent: Boolean = false
    )

    @JvmStatic
    fun render(
        desk: ConsoleBlockEntity,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        light: Int
    ) {
        val sockets = desk.sockets()
        val rotation = ConsoleBlock.rotationFor(desk.blockState)
        val gameTime = desk.level?.gameTime ?: 0L

        AeroworksDeskAccess.radarSurfaces(desk).forEach { surface ->
            val socket = sockets.getOrNull(surface.socket) ?: return@forEach
            elements(surface, gameTime).forEach { element ->
                val rendered: SuperByteBuffer = CachedBuffers.partial(element.model, desk.blockState)
                    .translate(0.5, 0.5, 0.5)
                    .rotate(rotation)
                    .translate(socket.offset().x - 0.5, socket.offset().y - 0.5, socket.offset().z - 0.5)
                    .rotate(socket.orientation())
                    .translate(-0.5, 0.0, -0.5)

                if (element.spinning) {
                    rendered
                        .translate(0.5, 0.0, 0.5)
                        .rotate(Axis.YP.rotationDegrees(sweepAngle(gameTime)))
                        .translate(-0.5, 0.0, -0.5)
                }
                rendered.translate(element.x, 0.0, element.z)
                rendered.light<SuperByteBuffer>(light)
                rendered.renderInto(
                    poseStack,
                    buffers.getBuffer(if (element.translucent) RenderType.translucent() else RenderType.cutout())
                )
            }
        }
    }

    @JvmStatic
    fun key(surface: RadarSurfaceState): String =
        "${surface.socket}:${surface.type}:${surface.facing}:${surface.snapshot?.contentHash() ?: 0}"

    @JvmStatic
    fun sweepAngle(gameTime: Long): Float =
        (gameTime % SWEEP_PERIOD_TICKS).toFloat() * (360.0f / SWEEP_PERIOD_TICKS.toFloat())

    @JvmStatic
    fun elements(surface: RadarSurfaceState, gameTime: Long): List<Element> {
        val models = DeskDisplayModels.radar(surface.type)
        val elements = mutableListOf(
            Element(models.filler, key = "filler", translucent = true),
            Element(models.circle, key = "circle", translucent = true)
        )
        val snapshot = surface.snapshot
        if (!RadarDisplaySnapshot.isFresh(snapshot, gameTime)) {
            elements += Element(DeskDisplayModels.radarDisconnected(), key = "disconnected")
            return elements
        }

        val active = requireNotNull(snapshot)
        elements += Element(models.sweep, key = "sweep", spinning = true, translucent = true)
        for (track in active.tracks) {
            val projected = project(surface, active, track.position.x, track.position.z) ?: continue
            elements += Element(
                model = DeskDisplayModels.radarTrack(track.sprite),
                key = "track:${track.id}:${track.sprite}",
                x = projected.first,
                z = projected.second
            )
            if (track.id == active.selectedTrackId) {
                elements += Element(
                    model = DeskDisplayModels.radarSelected(),
                    key = "selected:${track.id}",
                    x = projected.first,
                    z = projected.second
                )
            }
        }
        return elements
    }

    private fun project(
        surface: RadarSurfaceState,
        snapshot: RadarDisplaySnapshot,
        worldX: Double,
        worldZ: Double
    ): Pair<Double, Double>? {
        if (snapshot.range <= 0.0) return null

        val relativeX = worldX - snapshot.center.x
        val relativeZ = worldZ - snapshot.center.z
        val axisZ = surface.facing.axis == Direction.Axis.Z

        var normalizedX = (if (axisZ) relativeX else relativeZ) / snapshot.range
        if (surface.facing == Direction.NORTH || surface.facing == Direction.EAST) {
            normalizedX = -normalizedX
        }

        var normalizedZ = (if (axisZ) relativeZ else relativeX) / snapshot.range
        if (surface.facing == Direction.NORTH || surface.facing == Direction.WEST) {
            normalizedZ = -normalizedZ
        }

        if (normalizedX * normalizedX + normalizedZ * normalizedZ > 1.0) return null

        val halfWidth = when (surface.type) {
            RadarDisplayType.SMALL -> SMALL_HALF_WIDTH
            RadarDisplayType.LARGE -> LARGE_HALF_WIDTH
        }
        return Pair(
            normalizedX * halfWidth * TRACK_POSITION_SCALE,
            normalizedZ * HALF_HEIGHT * TRACK_POSITION_SCALE
        )
    }
}
