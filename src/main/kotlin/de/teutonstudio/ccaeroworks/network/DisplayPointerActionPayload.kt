package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.sable.SableInteractionGeometry
import de.teutonstudio.ccaeroworks.computer.DeskDisplayInputDispatcher
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DisplayPointerAction(val eventName: String) {
    TAP("tap"),
    DOUBLE_TAP("double_tap"),
    HOLD("hold")
}

data class DisplayPointerActionPayload(
    val pos: BlockPos,
    val socket: Int,
    val u: Double,
    val v: Double,
    val action: DisplayPointerAction
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<DisplayPointerActionPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("display_pointer_action"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DisplayPointerActionPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, DisplayPointerActionPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): DisplayPointerActionPayload {
                    val pos = buffer.readBlockPos()
                    val socket = buffer.readVarInt()
                    val u = buffer.readDouble()
                    val v = buffer.readDouble()
                    val actionOrdinal = buffer.readUnsignedByte().toInt()
                    val action = DisplayPointerAction.entries.getOrNull(actionOrdinal)
                        ?: throw IllegalArgumentException("Unknown display pointer action $actionOrdinal")
                    return DisplayPointerActionPayload(pos, socket, u, v, action)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: DisplayPointerActionPayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeDouble(payload.u)
                    buffer.writeDouble(payload.v)
                    buffer.writeByte(payload.action.ordinal)
                }
            }

        private val lastAcceptedTick = ConcurrentHashMap<RateKey, Long>()

        @JvmStatic
        fun handle(payload: DisplayPointerActionPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer
            if (player == null) {
                TouchInputDiagnostics.warn("server", "rejected packet: sender is not a ServerPlayer")
                return
            }

            val descriptor = "player=${player.gameProfile.name} pos=${payload.pos.toShortString()} socket=${payload.socket} action=${payload.action.eventName} u=${format(payload.u)} v=${format(payload.v)}"
            TouchInputDiagnostics.info("server", "received $descriptor")

            val level = player.serverLevel()
            if (!payload.u.isFinite() || !payload.v.isFinite() || payload.u !in 0.0..1.0 || payload.v !in 0.0..1.0) {
                TouchInputDiagnostics.warn("server", "rejected $descriptor: normalized coordinates are outside 0..1 or non-finite")
                return
            }
            if (!level.hasChunkAt(payload.pos)) {
                TouchInputDiagnostics.warn("server", "rejected $descriptor: target chunk is not loaded")
                return
            }
            if (!SableInteractionGeometry.mayInteract(player, level, payload.pos)) {
                TouchInputDiagnostics.warn("server", "rejected $descriptor: Sable/world interaction check failed")
                return
            }

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity
            if (desk == null) {
                TouchInputDiagnostics.warn("server", "rejected $descriptor: target block entity is not a ConsoleBlockEntity")
                return
            }
            if (desk.hasController() && !desk.checkUser(player.uuid)) {
                TouchInputDiagnostics.warn("server", "rejected $descriptor: ControlDesk controller ownership check denied player")
                return
            }

            val network = ConsoleMultiblockManager.resolve(level, payload.pos)
            val reachableMembers = network.members.count {
                SableInteractionGeometry.withinReach(player, level, it.pos)
            }
            if (reachableMembers == 0) {
                TouchInputDiagnostics.warn(
                    "server",
                    "rejected $descriptor: no multiblock member is within interaction reach; state=${network.state} members=${network.members.size}"
                )
                return
            }

            val touch = DeskDisplayGeometry.touch(desk, payload.socket, payload.u, payload.v)
            if (touch == null) {
                val module = if (payload.socket in 0 until desk.socketCount()) desk.module(payload.socket) else null
                TouchInputDiagnostics.warn(
                    "server",
                    "rejected $descriptor: display geometry could not resolve touch; socketCount=${desk.socketCount()} module=${module?.type()}"
                )
                return
            }

            val tick = level.gameTime
            val key = RateKey(player.uuid, payload.pos.asLong(), payload.socket, payload.action)
            if (lastAcceptedTick.put(key, tick) == tick) {
                TouchInputDiagnostics.warn("server", "rejected $descriptor: duplicate action in server tick $tick")
                return
            }

            TouchInputDiagnostics.info(
                "server",
                "accepted $descriptor -> pixel=${touch.x},${touch.y}/${touch.width}x${touch.height} reachableMembers=$reachableMembers"
            )
            DeskDisplayInputDispatcher.dispatch(desk, touch, payload.action)
        }

        private fun format(value: Double): String = "%.5f".format(java.util.Locale.ROOT, value)

        private data class RateKey(
            val player: UUID,
            val pos: Long,
            val socket: Int,
            val action: DisplayPointerAction
        )
    }
}
