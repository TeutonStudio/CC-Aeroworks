package de.teutonstudio.ccaeroworks.client.display

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.createradar.RadarTrace
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.nbt.CompoundTag
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.WeakHashMap

/**
 * Draws a compact vertical contact legend next to the native radar circle on
 * large RadarDisplay modules.
 *
 * The legend deliberately counts the same already-filtered native RadarTrack
 * payload that hydrates Create: Radars' MonitorBlockEntity. Category ownership
 * therefore stays with Create: Radars and the legend can never disagree with
 * the synchronized contact set shown by the radar circle.
 */
object RadarLegendRenderer {
    private const val RADAR_TRACK_UTIL_CLASS =
        "com.happysg.radar.block.radar.track.RadarTrackUtil"
    private const val RADAR_TRACK_CLASS =
        "com.happysg.radar.block.radar.track.RadarTrack"

    private const val MODULE_SURFACE_Y = 2.16 / 16.0
    private const val LEGEND_X = 0.708
    private const val LEGEND_Z = 0.306
    private const val TEXT_SCALE = 0.0022f
    private const val LINE_STEP = 10.0f
    private const val TEXT_COLOR = -0x1f1f20

    private var contract: Contract? = null
    private var contractResolutionAttempted = false
    private var lastFailureSignature: String? = null
    private val countCache = WeakHashMap<RadarDisplaySnapshot, RadarContactCounts>()

    @JvmStatic
    fun render(
        desk: ConsoleBlockEntity,
        poseStack: PoseStack,
        buffers: MultiBufferSource
    ): Boolean {
        val level = desk.level ?: return false
        val gameTime = level.gameTime
        val native = resolveContract(desk) ?: return false
        val font = Minecraft.getInstance().font
        val sockets = desk.sockets()
        var renderedAny = false

        for (surface in AeroworksDeskAccess.radarSurfaces(desk)) {
            if (surface.type != RadarDisplayType.LARGE) continue
            val snapshot = surface.snapshot ?: continue
            if (!RadarDisplaySnapshot.isFresh(snapshot, gameTime)) continue
            val socket = sockets.getOrNull(surface.socket) ?: continue
            val counts = countCache.getOrPut(snapshot) { countContacts(native, snapshot, desk) ?: return@getOrPut EMPTY_COUNTS }

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
                poseStack.translate(LEGEND_X, MODULE_SURFACE_Y, LEGEND_Z)
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
                poseStack.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE)

                legendLines(counts).forEachIndexed { index, line ->
                    font.drawInBatch(
                        line,
                        0.0f,
                        index * LINE_STEP,
                        TEXT_COLOR,
                        false,
                        poseStack.last().pose(),
                        buffers,
                        Font.DisplayMode.POLYGON_OFFSET,
                        0,
                        LightTexture.FULL_BRIGHT
                    )
                }
                renderedAny = true
                lastFailureSignature = null
                RadarTrace.periodic(
                    "L10_LEGEND_RENDER",
                    level,
                    desk.blockPos,
                    10L,
                    "socket=${surface.socket} counts=$counts syncedTracks=${snapshot.trackCount}"
                )
            } catch (throwable: Throwable) {
                reportFailure(unwrap(throwable), desk)
            } finally {
                poseStack.popPose()
            }
        }
        return renderedAny
    }

    private fun countContacts(
        native: Contract,
        snapshot: RadarDisplaySnapshot,
        desk: ConsoleBlockEntity
    ): RadarContactCounts? = try {
        val rawTracks = native.deserializeListNBT.invoke(null, snapshot.nativeTracks) as? Iterable<*>
            ?: throw IllegalStateException("RadarTrackUtil.deserializeListNBT returned no iterable")

        var players = 0
        var ships = 0
        var contraptions = 0
        var mobs = 0
        var projectiles = 0
        var animals = 0
        var items = 0

        for (track in rawTracks) {
            track ?: continue
            if (!native.radarTrackClass.isInstance(track)) continue
            val category = native.getTrackCategory.invoke(track)
                ?.toString()
                ?.uppercase(Locale.ROOT)
                ?: continue
            when (category) {
                "PLAYER" -> players++
                "VS2" -> ships++
                "CONTRAPTION" -> contraptions++
                "MOB", "HOSTILE" -> mobs++
                "PROJECTILE" -> projectiles++
                "ANIMAL" -> animals++
                "ITEM" -> items++
            }
        }

        RadarContactCounts(
            players = players,
            ships = ships,
            contraptions = contraptions,
            mobs = mobs,
            projectiles = projectiles,
            animals = animals,
            items = items
        )
    } catch (throwable: Throwable) {
        reportFailure(unwrap(throwable), desk)
        null
    }

    private fun legendLines(counts: RadarContactCounts): List<String> = listOf(
        "PLY ${formatCount(counts.players)}",
        "SHP ${formatCount(counts.ships)}",
        "CTR ${formatCount(counts.contraptions)}",
        "MOB ${formatCount(counts.mobs)}",
        "PRJ ${formatCount(counts.projectiles)}",
        "ANI ${formatCount(counts.animals)}",
        "ITM ${formatCount(counts.items)}"
    )

    private fun formatCount(value: Int): String = when {
        value >= 100 -> "99+"
        else -> value.coerceAtLeast(0).toString().padStart(2, '0')
    }

    private fun resolveContract(desk: ConsoleBlockEntity): Contract? {
        contract?.let { return it }
        if (contractResolutionAttempted) return null
        contractResolutionAttempted = true

        return try {
            val loader = RadarLegendRenderer::class.java.classLoader
            val radarTrackUtil = Class.forName(RADAR_TRACK_UTIL_CLASS, true, loader)
            val radarTrack = Class.forName(RADAR_TRACK_CLASS, true, loader)
            val deserialize = radarTrackUtil.getMethod("deserializeListNBT", CompoundTag::class.java)
            if (!Modifier.isStatic(deserialize.modifiers)) {
                throw IllegalStateException("RadarTrackUtil.deserializeListNBT is no longer static")
            }
            val category = radarTrack.getMethod("getTrackCategory")
            Contract(
                radarTrackClass = radarTrack,
                deserializeListNBT = deserialize,
                getTrackCategory = category
            ).also {
                contract = it
                RadarTrace.event(
                    "L00_LEGEND_CONTRACT_OK",
                    desk.level,
                    desk.blockPos,
                    "deserialize=${methodSignature(deserialize)} category=${methodSignature(category)}"
                )
            }
        } catch (throwable: Throwable) {
            reportFailure(unwrap(throwable), desk)
            null
        }
    }

    private fun reportFailure(cause: Throwable, desk: ConsoleBlockEntity) {
        val signature = "${cause.javaClass.name}:${cause.message.orEmpty()}"
        if (signature == lastFailureSignature) return
        lastFailureSignature = signature
        RadarTrace.event("L99_LEGEND_FAILURE", desk.level, desk.blockPos, RadarTrace.throwable(cause))
        CCAeroworks.LOGGER.warn(
            "[CC-Aeroworks] Create: Radars contact legend rendering failed for desk {}",
            desk.blockPos,
            cause
        )
    }

    private fun methodSignature(method: Method): String =
        "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString { it.name }}):${method.returnType.name}"

    private fun unwrap(throwable: Throwable): Throwable =
        if (throwable is InvocationTargetException) throwable.targetException ?: throwable else throwable

    private data class Contract(
        val radarTrackClass: Class<*>,
        val deserializeListNBT: Method,
        val getTrackCategory: Method
    )

    private data class RadarContactCounts(
        val players: Int,
        val ships: Int,
        val contraptions: Int,
        val mobs: Int,
        val projectiles: Int,
        val animals: Int,
        val items: Int
    )

    private val EMPTY_COUNTS = RadarContactCounts(0, 0, 0, 0, 0, 0, 0)
}
