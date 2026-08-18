package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.sable.SableInteractionGeometry
import de.teutonstudio.ccaeroworks.computer.DeskDisplayInputDispatcher
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.display.DeskDisplayInput
import de.teutonstudio.ccaeroworks.display.DeskDisplayStrokeSample
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

data class DisplayDrawSamplePayload(
    val u: Double,
    val v: Double,
    val directionU: Double,
    val directionV: Double,
    val speed: Double
)

/**
 * One tick-bounded packet of a right-button draw gesture.
 *
 * The client keeps the high-frequency virtual-finger path locally and sends at most one packet per
 * client tick. Each packet contains up to [MAX_BATCH_SAMPLES] normalized surface samples. The server
 * validates every sample, resolves it against the display's current raster and prepends the previous
 * accepted sample before dispatching to Lua, making each stroke event self-contained.
 */
data class DisplayDrawPayload(
    val pos: BlockPos,
    val socket: Int,
    val gestureId: Long,
    val sequence: Int,
    val u: Double,
    val v: Double,
    val directionU: Double,
    val directionV: Double,
    val speed: Double,
    val samples: List<DisplayDrawSamplePayload>,
    val isEnd: Boolean
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val MAX_BATCH_SAMPLES: Int = 16
        private const val STALE_GESTURE_TICKS = 40L
        private const val DIRECTION_EPSILON = 1.0e-12
        private const val DIRECTION_COMPONENT_TOLERANCE = 1.000001
        private const val MATCH_EPSILON = 1.0e-9

        @JvmField
        val TYPE: CustomPacketPayload.Type<DisplayDrawPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("display_draw"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DisplayDrawPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, DisplayDrawPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): DisplayDrawPayload {
                    val pos = buffer.readBlockPos()
                    val socket = buffer.readVarInt()
                    val gestureId = buffer.readVarLong()
                    val sequence = buffer.readVarInt()
                    val u = buffer.readDouble()
                    val v = buffer.readDouble()
                    val directionU = buffer.readDouble()
                    val directionV = buffer.readDouble()
                    val speed = buffer.readDouble()
                    val sampleCount = buffer.readVarInt()
                    require(sampleCount in 1..MAX_BATCH_SAMPLES) {
                        "display draw sample count $sampleCount is outside 1..$MAX_BATCH_SAMPLES"
                    }
                    val samples = List(sampleCount) {
                        DisplayDrawSamplePayload(
                            u = buffer.readDouble(),
                            v = buffer.readDouble(),
                            directionU = buffer.readDouble(),
                            directionV = buffer.readDouble(),
                            speed = buffer.readDouble()
                        )
                    }
                    return DisplayDrawPayload(
                        pos = pos,
                        socket = socket,
                        gestureId = gestureId,
                        sequence = sequence,
                        u = u,
                        v = v,
                        directionU = directionU,
                        directionV = directionV,
                        speed = speed,
                        samples = samples,
                        isEnd = buffer.readBoolean()
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: DisplayDrawPayload) {
                    require(payload.samples.size in 1..MAX_BATCH_SAMPLES) {
                        "display draw sample count ${payload.samples.size} is outside 1..$MAX_BATCH_SAMPLES"
                    }
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeVarLong(payload.gestureId)
                    buffer.writeVarInt(payload.sequence)
                    buffer.writeDouble(payload.u)
                    buffer.writeDouble(payload.v)
                    buffer.writeDouble(payload.directionU)
                    buffer.writeDouble(payload.directionV)
                    buffer.writeDouble(payload.speed)
                    buffer.writeVarInt(payload.samples.size)
                    payload.samples.forEach { sample ->
                        buffer.writeDouble(sample.u)
                        buffer.writeDouble(sample.v)
                        buffer.writeDouble(sample.directionU)
                        buffer.writeDouble(sample.directionV)
                        buffer.writeDouble(sample.speed)
                    }
                    buffer.writeBoolean(payload.isEnd)
                }
            }

        private val gestures = ConcurrentHashMap<GestureKey, GestureState>()

        @JvmStatic
        fun handle(payload: DisplayDrawPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer
            if (player == null) {
                TouchInputDiagnostics.warn("server", "rejected draw packet: sender is not a ServerPlayer")
                return
            }

            val descriptor = "player=${player.gameProfile.name} pos=${payload.pos.toShortString()} socket=${payload.socket} gesture=${payload.gestureId} seq=${payload.sequence} end=${payload.isEnd} u=${format(payload.u)} v=${format(payload.v)} direction=${format(payload.directionU)},${format(payload.directionV)} speed=${format(payload.speed)} samples=${payload.samples.size}"
            TouchInputDiagnostics.info("server", "received draw $descriptor")

            if (payload.gestureId <= 0L || payload.sequence < 0) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: invalid gesture id or sequence")
                return
            }
            if (payload.samples.size !in 1..MAX_BATCH_SAMPLES) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: invalid batch size")
                return
            }

            val normalizedSamples = arrayListOf<DisplayDrawSamplePayload>()
            payload.samples.forEachIndexed { index, sample ->
                val normalized = normalizeSample(sample)
                if (normalized == null) {
                    TouchInputDiagnostics.warn("server", "rejected draw $descriptor: invalid sample at index=$index")
                    return
                }
                normalizedSamples += normalized
            }

            val lastRaw = payload.samples.last()
            if (!matchesTopLevel(payload, lastRaw)) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: top-level point does not match final batch sample")
                return
            }

            val level = player.serverLevel()
            val tick = level.gameTime
            gestures.entries.removeIf { tick - it.value.lastTick > STALE_GESTURE_TICKS }

            if (!level.hasChunkAt(payload.pos)) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: target chunk is not loaded")
                return
            }
            if (!SableInteractionGeometry.mayInteract(player, level, payload.pos)) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: Sable/world interaction check failed")
                return
            }

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity
            if (desk == null) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: target block entity is not a ConsoleBlockEntity")
                return
            }
            if (desk.hasController() && !desk.checkUser(player.uuid)) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: ControlDesk controller ownership check denied player")
                return
            }

            val network = ConsoleMultiblockManager.resolve(level, payload.pos)
            val reachableMembers = network.members.count {
                SableInteractionGeometry.withinReach(player, level, it.pos)
            }
            if (reachableMembers == 0) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: no multiblock member is within interaction reach")
                return
            }

            val resolved = arrayListOf<ResolvedDrawSample>()
            normalizedSamples.forEachIndexed { index, sample ->
                val touch = DeskDisplayGeometry.touch(desk, payload.socket, sample.u, sample.v)
                if (touch == null) {
                    TouchInputDiagnostics.warn(
                        "server",
                        "rejected draw $descriptor: display geometry could not resolve batch sample index=$index"
                    )
                    return
                }
                resolved += ResolvedDrawSample(touch, sample.directionU, sample.directionV, sample.speed)
            }
            val current = resolved.last()

            val key = GestureKey(player.uuid, payload.pos.asLong(), payload.socket, payload.gestureId)
            if (payload.sequence == 0) {
                if (gestures.containsKey(key)) {
                    TouchInputDiagnostics.warn("server", "rejected draw $descriptor: duplicate gesture start")
                    return
                }
                val state = GestureState(current.touch, current, 0, tick)
                gestures[key] = state
                val input = DeskDisplayInput(
                    action = "draw",
                    touch = current.touch,
                    gestureId = payload.gestureId,
                    sequence = 0,
                    startX = current.touch.x,
                    startY = current.touch.y,
                    deltaX = 0,
                    deltaY = 0,
                    directionU = current.directionU,
                    directionV = current.directionV,
                    speed = current.speed,
                    samples = resolved.map(ResolvedDrawSample::toDeskSample),
                    isEnd = payload.isEnd
                )
                TouchInputDiagnostics.info(
                    "server",
                    "accepted draw START $descriptor -> pixel=${current.touch.x},${current.touch.y}/${current.touch.width}x${current.touch.height} delta=0,0 samples=${input.samples.size}"
                )
                DeskDisplayInputDispatcher.dispatch(desk, input)
                if (payload.isEnd) gestures.remove(key)
                return
            }

            val state = gestures[key]
            if (state == null) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: gesture has no accepted start")
                return
            }
            if (payload.sequence != state.lastSequence + 1) {
                TouchInputDiagnostics.warn(
                    "server",
                    "rejected draw $descriptor: expected sequence ${state.lastSequence + 1}"
                )
                return
            }

            val deltaX = current.touch.x - state.lastSample.touch.x
            val deltaY = current.touch.y - state.lastSample.touch.y
            val strokeSamples = buildList {
                add(state.lastSample.toDeskSample())
                resolved.forEach { add(it.toDeskSample()) }
            }
            gestures[key] = GestureState(state.startTouch, current, payload.sequence, tick)

            val input = DeskDisplayInput(
                action = "draw",
                touch = current.touch,
                gestureId = payload.gestureId,
                sequence = payload.sequence,
                startX = state.startTouch.x,
                startY = state.startTouch.y,
                deltaX = deltaX,
                deltaY = deltaY,
                directionU = current.directionU,
                directionV = current.directionV,
                speed = current.speed,
                samples = strokeSamples,
                isEnd = payload.isEnd
            )
            TouchInputDiagnostics.info(
                "server",
                "accepted draw ${if (payload.isEnd) "END" else "SAMPLE"} $descriptor -> start=${state.startTouch.x},${state.startTouch.y} pixel=${current.touch.x},${current.touch.y}/${current.touch.width}x${current.touch.height} delta=$deltaX,$deltaY samples=${strokeSamples.size}"
            )
            DeskDisplayInputDispatcher.dispatch(desk, input)
            if (payload.isEnd) gestures.remove(key)
        }

        private fun normalizeSample(sample: DisplayDrawSamplePayload): DisplayDrawSamplePayload? {
            if (!sample.u.isFinite() || !sample.v.isFinite() || sample.u !in 0.0..1.0 || sample.v !in 0.0..1.0 ||
                !sample.directionU.isFinite() || !sample.directionV.isFinite() || !sample.speed.isFinite() || sample.speed < 0.0 ||
                abs(sample.directionU) > DIRECTION_COMPONENT_TOLERANCE || abs(sample.directionV) > DIRECTION_COMPONENT_TOLERANCE
            ) return null

            val directionLength = hypot(sample.directionU, sample.directionV)
            if (directionLength <= DIRECTION_EPSILON && sample.speed > DIRECTION_EPSILON) return null
            return sample.copy(
                directionU = if (directionLength > DIRECTION_EPSILON) sample.directionU / directionLength else 0.0,
                directionV = if (directionLength > DIRECTION_EPSILON) sample.directionV / directionLength else 0.0
            )
        }

        private fun matchesTopLevel(payload: DisplayDrawPayload, last: DisplayDrawSamplePayload): Boolean =
            abs(payload.u - last.u) <= MATCH_EPSILON &&
                abs(payload.v - last.v) <= MATCH_EPSILON &&
                abs(payload.directionU - last.directionU) <= MATCH_EPSILON &&
                abs(payload.directionV - last.directionV) <= MATCH_EPSILON &&
                abs(payload.speed - last.speed) <= MATCH_EPSILON

        private fun format(value: Double): String = "%.5f".format(java.util.Locale.ROOT, value)

        private data class GestureKey(
            val player: UUID,
            val pos: Long,
            val socket: Int,
            val gestureId: Long
        )

        private data class GestureState(
            val startTouch: DeskDisplayTouch,
            val lastSample: ResolvedDrawSample,
            val lastSequence: Int,
            val lastTick: Long
        )

        private data class ResolvedDrawSample(
            val touch: DeskDisplayTouch,
            val directionU: Double,
            val directionV: Double,
            val speed: Double
        ) {
            fun toDeskSample(): DeskDisplayStrokeSample = DeskDisplayStrokeSample(
                x = touch.x,
                y = touch.y,
                u = touch.u,
                v = touch.v,
                directionU = directionU,
                directionV = directionV,
                speed = speed
            )
        }
    }
}
