package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.sable.SableInteractionGeometry
import de.teutonstudio.ccaeroworks.computer.DeskDisplayInputDispatcher
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.display.DeskDisplayInput
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

/**
 * One sample of a right-button draw gesture.
 *
 * The client sends only normalized current coordinates plus gesture ordering. Pixel start/current
 * coordinates and the delta from the previously accepted sample are resolved authoritatively on
 * the server against the display's current resolution.
 */
data class DisplayDrawPayload(
    val pos: BlockPos,
    val socket: Int,
    val gestureId: Long,
    val sequence: Int,
    val u: Double,
    val v: Double,
    val isEnd: Boolean
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val STALE_GESTURE_TICKS = 40L

        @JvmField
        val TYPE: CustomPacketPayload.Type<DisplayDrawPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("display_draw"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DisplayDrawPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, DisplayDrawPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): DisplayDrawPayload = DisplayDrawPayload(
                    pos = buffer.readBlockPos(),
                    socket = buffer.readVarInt(),
                    gestureId = buffer.readVarLong(),
                    sequence = buffer.readVarInt(),
                    u = buffer.readDouble(),
                    v = buffer.readDouble(),
                    isEnd = buffer.readBoolean()
                )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: DisplayDrawPayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeVarLong(payload.gestureId)
                    buffer.writeVarInt(payload.sequence)
                    buffer.writeDouble(payload.u)
                    buffer.writeDouble(payload.v)
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

            val descriptor = "player=${player.gameProfile.name} pos=${payload.pos.toShortString()} socket=${payload.socket} gesture=${payload.gestureId} seq=${payload.sequence} end=${payload.isEnd} u=${format(payload.u)} v=${format(payload.v)}"
            TouchInputDiagnostics.info("server", "received draw $descriptor")

            if (payload.gestureId <= 0L || payload.sequence < 0) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: invalid gesture id or sequence")
                return
            }
            if (!payload.u.isFinite() || !payload.v.isFinite() || payload.u !in 0.0..1.0 || payload.v !in 0.0..1.0) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: normalized coordinates are outside 0..1 or non-finite")
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

            val current = DeskDisplayGeometry.touch(desk, payload.socket, payload.u, payload.v)
            if (current == null) {
                TouchInputDiagnostics.warn("server", "rejected draw $descriptor: display geometry could not resolve current point")
                return
            }

            val key = GestureKey(player.uuid, payload.pos.asLong(), payload.socket, payload.gestureId)
            if (payload.sequence == 0) {
                if (gestures.containsKey(key)) {
                    TouchInputDiagnostics.warn("server", "rejected draw $descriptor: duplicate gesture start")
                    return
                }
                val state = GestureState(current, current, 0, tick)
                gestures[key] = state
                val input = DeskDisplayInput(
                    action = "draw",
                    touch = current,
                    gestureId = payload.gestureId,
                    sequence = 0,
                    startX = current.x,
                    startY = current.y,
                    deltaX = 0,
                    deltaY = 0,
                    isEnd = payload.isEnd
                )
                TouchInputDiagnostics.info(
                    "server",
                    "accepted draw START $descriptor -> pixel=${current.x},${current.y}/${current.width}x${current.height} delta=0,0"
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

            val deltaX = current.x - state.lastTouch.x
            val deltaY = current.y - state.lastTouch.y
            val next = state.copy(lastTouch = current, lastSequence = payload.sequence, lastTick = tick)
            gestures[key] = next

            val input = DeskDisplayInput(
                action = "draw",
                touch = current,
                gestureId = payload.gestureId,
                sequence = payload.sequence,
                startX = state.startTouch.x,
                startY = state.startTouch.y,
                deltaX = deltaX,
                deltaY = deltaY,
                isEnd = payload.isEnd
            )
            TouchInputDiagnostics.info(
                "server",
                "accepted draw ${if (payload.isEnd) "END" else "SAMPLE"} $descriptor -> start=${state.startTouch.x},${state.startTouch.y} pixel=${current.x},${current.y}/${current.width}x${current.height} delta=$deltaX,$deltaY"
            )
            DeskDisplayInputDispatcher.dispatch(desk, input)
            if (payload.isEnd) gestures.remove(key)
        }

        private fun format(value: Double): String = "%.5f".format(java.util.Locale.ROOT, value)

        private data class GestureKey(
            val player: UUID,
            val pos: Long,
            val socket: Int,
            val gestureId: Long
        )

        private data class GestureState(
            val startTouch: DeskDisplayTouch,
            val lastTouch: DeskDisplayTouch,
            val lastSequence: Int,
            val lastTick: Long
        )
    }
}
