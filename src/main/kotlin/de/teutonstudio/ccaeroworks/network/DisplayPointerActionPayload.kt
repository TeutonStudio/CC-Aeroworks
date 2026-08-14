package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.sable.SableSpatial
import de.teutonstudio.ccaeroworks.computer.DeskDisplayInputDispatcher
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
    DOUBLE_TAP("double_tap")
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
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!payload.u.isFinite() || !payload.v.isFinite() || payload.u !in 0.0..1.0 || payload.v !in 0.0..1.0) return
            if (!level.hasChunkAt(payload.pos) || !level.mayInteract(player, payload.pos)) return

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return

            val network = ConsoleMultiblockManager.resolve(level, payload.pos)
            val maximumDistance = player.blockInteractionRange() + 1.0
            if (network.members.none {
                    SableSpatial.distanceSquared(level, player.position(), it.pos.center) <=
                        maximumDistance * maximumDistance
                }
            ) return

            val touch = DeskDisplayGeometry.touch(desk, payload.socket, payload.u, payload.v) ?: return
            val tick = level.gameTime
            val key = RateKey(player.uuid, payload.pos.asLong(), payload.socket, payload.action)
            if (lastAcceptedTick.put(key, tick) == tick) return

            DeskDisplayInputDispatcher.dispatch(desk, touch, payload.action)
        }

        private data class RateKey(
            val player: UUID,
            val pos: Long,
            val socket: Int,
            val action: DisplayPointerAction
        )
    }
}
