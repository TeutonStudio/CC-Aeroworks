package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.display.RadarSurfaceState
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared RadarDisplay composition for the classic BER and Flywheel.
 *
 * The radar uses atlas-safe CC-Aeroworks models backed by vanilla textures.
 * Create: Radars' MonitorSprite PNGs remain renderer resources and are therefore
 * deliberately not baked into PartialModels. This keeps both rendering paths
 * identical without falling back to the previous orange display-segment frame.
 */
object RadarSurfaceRenderer {
    private const val TRACK_POSITION_SCALE = 0.75
    private const val SMALL_HALF_WIDTH = 0.17
    private const val LARGE_HALF_WIDTH = 0.245
    private const val HALF_HEIGHT = 0.17
    private const val SWEEP_PERIOD_TICKS = 120L
    private const val GLYPH_OFFSET = 0.024
    private const val SELECTION_OFFSET = 0.045
    private const val OUTER_RING_SCALE = 0.88
    private const val INNER_RING_SCALE = 0.48
    private const val OUTER_RING_POINTS = 32
    private const val INNER_RING_POINTS = 20

    data class Element(
        val model: PartialModel,
        val x: Double = 0.0,
        val z: Double = 0.0,
        val spinning: Boolean = false
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
        val consumer = buffers.getBuffer(RenderType.cutout())
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
                rendered.renderInto(poseStack, consumer)
            }
        }
    }

    /**
     * Flywheel rebuilds only when renderable radar content or the fresh/stale
     * state changes. Heartbeat timestamps themselves remain excluded.
     */
    @JvmStatic
    fun key(surface: RadarSurfaceState, gameTime: Long): String {
        val snapshot = surface.snapshot
        return if (snapshot == null) {
            "${surface.socket}:${surface.type}:none"
        } else {
            buildString {
                append(surface.socket).append(':').append(surface.type).append(':')
                append(snapshot.connected).append(':').append(snapshot.operational).append(':')
                append(snapshot.status).append(':').append(snapshot.radarPos).append(':')
                append(snapshot.range).append(':').append(snapshot.selectedTrackId).append(':')
                append(snapshot.tracks.hashCode()).append(':')
                append(RadarDisplaySnapshot.isFresh(snapshot, gameTime))
            }
        }
    }

    @JvmStatic
    fun sweepAngle(gameTime: Long): Float =
        (gameTime % SWEEP_PERIOD_TICKS).toFloat() * (360.0f / SWEEP_PERIOD_TICKS.toFloat())

    @JvmStatic
    fun elements(surface: RadarSurfaceState, gameTime: Long): List<Element> {
        val elements = radarBaseElements(surface.type).toMutableList()
        val snapshot = surface.snapshot
        if (!RadarDisplaySnapshot.isFresh(snapshot, gameTime)) {
            elements += Element(DeskDisplayModels.radarDisconnected())
            return elements
        }

        val active = requireNotNull(snapshot)
        elements += Element(DeskDisplayModels.RADAR_SWEEP, spinning = true)

        for (track in active.tracks) {
            val projected = project(surface.type, active, track.position.x, track.position.z) ?: continue
            elements += trackGlyph(track, projected.first, projected.second)
            if (track.id == active.selectedTrackId) {
                elements += selectionGlyph(projected.first, projected.second)
            }
        }
        return elements
    }

    private fun radarBaseElements(type: RadarDisplayType): List<Element> {
        val halfWidth = halfWidth(type)
        return buildList {
            add(
                Element(
                    when (type) {
                        RadarDisplayType.SMALL -> DeskDisplayModels.RADAR_SMALL_BACKGROUND
                        RadarDisplayType.LARGE -> DeskDisplayModels.RADAR_LARGE_BACKGROUND
                    }
                )
            )
            addAll(ringElements(halfWidth * OUTER_RING_SCALE, HALF_HEIGHT * OUTER_RING_SCALE, OUTER_RING_POINTS))
            addAll(ringElements(halfWidth * INNER_RING_SCALE, HALF_HEIGHT * INNER_RING_SCALE, INNER_RING_POINTS))
            add(Element(DeskDisplayModels.RADAR_PIXEL))
        }
    }

    private fun ringElements(radiusX: Double, radiusZ: Double, points: Int): List<Element> =
        List(points) { index ->
            val angle = 2.0 * PI * index.toDouble() / points.toDouble()
            Element(
                model = DeskDisplayModels.RADAR_PIXEL,
                x = cos(angle) * radiusX,
                z = sin(angle) * radiusZ
            )
        }

    private fun trackGlyph(track: RadarDisplayTrack, x: Double, z: Double): List<Element> =
        when (track.sprite) {
            RadarDisplayTrackSprite.ENTITY -> listOf(
                Element(DeskDisplayModels.RADAR_PIXEL, x, z)
            )

            RadarDisplayTrackSprite.PLAYER -> listOf(
                Element(DeskDisplayModels.RADAR_PIXEL, x, z - GLYPH_OFFSET),
                Element(DeskDisplayModels.RADAR_PIXEL, x, z + GLYPH_OFFSET)
            )

            RadarDisplayTrackSprite.PROJECTILE -> listOf(
                Element(DeskDisplayModels.RADAR_PIXEL, x - GLYPH_OFFSET, z),
                Element(DeskDisplayModels.RADAR_PIXEL, x + GLYPH_OFFSET, z)
            )

            RadarDisplayTrackSprite.CONTRAPTION -> listOf(
                Element(DeskDisplayModels.RADAR_PIXEL, x - GLYPH_OFFSET, z - GLYPH_OFFSET),
                Element(DeskDisplayModels.RADAR_PIXEL, x + GLYPH_OFFSET, z - GLYPH_OFFSET),
                Element(DeskDisplayModels.RADAR_PIXEL, x - GLYPH_OFFSET, z + GLYPH_OFFSET),
                Element(DeskDisplayModels.RADAR_PIXEL, x + GLYPH_OFFSET, z + GLYPH_OFFSET)
            )
        }

    private fun selectionGlyph(x: Double, z: Double): List<Element> = listOf(
        Element(DeskDisplayModels.RADAR_SELECTED_PIXEL, x - SELECTION_OFFSET, z - SELECTION_OFFSET),
        Element(DeskDisplayModels.RADAR_SELECTED_PIXEL, x + SELECTION_OFFSET, z - SELECTION_OFFSET),
        Element(DeskDisplayModels.RADAR_SELECTED_PIXEL, x - SELECTION_OFFSET, z + SELECTION_OFFSET),
        Element(DeskDisplayModels.RADAR_SELECTED_PIXEL, x + SELECTION_OFFSET, z + SELECTION_OFFSET)
    )

    private fun project(
        type: RadarDisplayType,
        snapshot: RadarDisplaySnapshot,
        worldX: Double,
        worldZ: Double
    ): Pair<Double, Double>? {
        if (snapshot.range <= 0.0) return null
        val normalizedX = (worldX - snapshot.center.x) / snapshot.range
        val normalizedZ = (worldZ - snapshot.center.z) / snapshot.range
        if (normalizedX * normalizedX + normalizedZ * normalizedZ > 1.0) return null

        return Pair(
            normalizedX * halfWidth(type) * TRACK_POSITION_SCALE,
            -normalizedZ * HALF_HEIGHT * TRACK_POSITION_SCALE
        )
    }

    private fun halfWidth(type: RadarDisplayType): Double = when (type) {
        RadarDisplayType.SMALL -> SMALL_HALF_WIDTH
        RadarDisplayType.LARGE -> LARGE_HALF_WIDTH
    }
}
