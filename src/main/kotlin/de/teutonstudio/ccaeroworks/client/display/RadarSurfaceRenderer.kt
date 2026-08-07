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

/**
 * Renders the RadarDisplay entirely from CC-Aeroworks-owned display partials.
 *
 * Create: Radars' MonitorSprite textures are renderer resources, not guaranteed
 * block-atlas sprites. Baking them into PartialModels produced the black/magenta
 * missing-texture surface on real clients. Reusing the already-proven segment
 * and pixel partials keeps classic rendering and Flywheel on one safe path.
 */
object RadarSurfaceRenderer {
    private const val TRACK_POSITION_SCALE = 0.75
    private const val SMALL_HALF_WIDTH = 0.17
    private const val LARGE_HALF_WIDTH = 0.245
    private const val HALF_HEIGHT = 0.17
    private const val SWEEP_PERIOD_TICKS = 120L
    private const val GLYPH_OFFSET = 0.024
    private const val SELECTION_OFFSET = 0.045

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
     * Flywheel rebuilds only when renderable radar content changes. Transport
     * timestamps are deliberately excluded so heartbeat packets do not churn
     * instance pools every few ticks.
     */
    @JvmStatic
    fun key(surface: RadarSurfaceState): String {
        val snapshot = surface.snapshot
        return if (snapshot == null) {
            "${surface.socket}:${surface.type}:none"
        } else {
            buildString {
                append(surface.socket).append(':').append(surface.type).append(':')
                append(snapshot.connected).append(':').append(snapshot.operational).append(':')
                append(snapshot.status).append(':').append(snapshot.radarPos).append(':')
                append(snapshot.range).append(':').append(snapshot.selectedTrackId).append(':')
                append(snapshot.tracks.hashCode())
            }
        }
    }

    @JvmStatic
    fun sweepAngle(gameTime: Long): Float =
        (gameTime % SWEEP_PERIOD_TICKS).toFloat() * (360.0f / SWEEP_PERIOD_TICKS.toFloat())

    @JvmStatic
    fun elements(surface: RadarSurfaceState, gameTime: Long): List<Element> {
        val elements = frameElements(surface.type).toMutableList()
        val snapshot = surface.snapshot
        if (!RadarDisplaySnapshot.isFresh(snapshot, gameTime)) {
            elements += Element(DeskDisplayModels.radarDisconnected())
            return elements
        }

        val active = requireNotNull(snapshot)

        // The vertical segment is offset by half its own length. Rotating it
        // around the model centre therefore produces a radial sweep arm rather
        // than a diameter line.
        elements += Element(
            model = DeskDisplayModels.VERTICAL,
            z = -0.075,
            spinning = true
        )

        for (track in active.tracks) {
            val projected = project(surface.type, active, track.position.x, track.position.z) ?: continue
            elements += trackGlyph(track, projected.first, projected.second)
            if (track.id == active.selectedTrackId) {
                elements += selectionGlyph(projected.first, projected.second)
            }
        }
        return elements
    }

    private fun frameElements(type: RadarDisplayType): List<Element> {
        val xCenters = when (type) {
            RadarDisplayType.SMALL -> listOf(-0.105, 0.0, 0.105)
            RadarDisplayType.LARGE -> listOf(-0.18, -0.06, 0.06, 0.18)
        }
        val halfWidth = when (type) {
            RadarDisplayType.SMALL -> SMALL_HALF_WIDTH
            RadarDisplayType.LARGE -> LARGE_HALF_WIDTH
        }
        val sideX = halfWidth - 0.022

        return buildList {
            for (x in xCenters) {
                add(Element(DeskDisplayModels.HORIZONTAL, x = x, z = HALF_HEIGHT - 0.012))
                add(Element(DeskDisplayModels.HORIZONTAL, x = x, z = -HALF_HEIGHT + 0.012))
            }
            for (z in listOf(-0.08, 0.08)) {
                add(Element(DeskDisplayModels.VERTICAL, x = sideX, z = z))
                add(Element(DeskDisplayModels.VERTICAL, x = -sideX, z = z))
            }
        }
    }

    private fun trackGlyph(track: RadarDisplayTrack, x: Double, z: Double): List<Element> =
        when (track.sprite) {
            RadarDisplayTrackSprite.ENTITY -> listOf(
                Element(DeskDisplayModels.PIXEL, x, z)
            )

            RadarDisplayTrackSprite.PLAYER -> listOf(
                Element(DeskDisplayModels.PIXEL, x, z - GLYPH_OFFSET),
                Element(DeskDisplayModels.PIXEL, x, z + GLYPH_OFFSET)
            )

            RadarDisplayTrackSprite.PROJECTILE -> listOf(
                Element(DeskDisplayModels.PIXEL, x - GLYPH_OFFSET, z),
                Element(DeskDisplayModels.PIXEL, x + GLYPH_OFFSET, z)
            )

            RadarDisplayTrackSprite.CONTRAPTION -> listOf(
                Element(DeskDisplayModels.PIXEL, x - GLYPH_OFFSET, z - GLYPH_OFFSET),
                Element(DeskDisplayModels.PIXEL, x + GLYPH_OFFSET, z - GLYPH_OFFSET),
                Element(DeskDisplayModels.PIXEL, x - GLYPH_OFFSET, z + GLYPH_OFFSET),
                Element(DeskDisplayModels.PIXEL, x + GLYPH_OFFSET, z + GLYPH_OFFSET)
            )
        }

    private fun selectionGlyph(x: Double, z: Double): List<Element> = listOf(
        Element(DeskDisplayModels.PIXEL, x - SELECTION_OFFSET, z - SELECTION_OFFSET),
        Element(DeskDisplayModels.PIXEL, x + SELECTION_OFFSET, z - SELECTION_OFFSET),
        Element(DeskDisplayModels.PIXEL, x - SELECTION_OFFSET, z + SELECTION_OFFSET),
        Element(DeskDisplayModels.PIXEL, x + SELECTION_OFFSET, z + SELECTION_OFFSET)
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

        val halfWidth = when (type) {
            RadarDisplayType.SMALL -> SMALL_HALF_WIDTH
            RadarDisplayType.LARGE -> LARGE_HALF_WIDTH
        }
        return Pair(
            normalizedX * halfWidth * TRACK_POSITION_SCALE,
            -normalizedZ * HALF_HEIGHT * TRACK_POSITION_SCALE
        )
    }
}
