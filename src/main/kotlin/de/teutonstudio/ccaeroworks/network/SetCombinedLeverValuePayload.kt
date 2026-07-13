package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SetCombinedLeverValuePayload(val pos: BlockPos, val socket: Int, val channel: String, val value: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetCombinedLeverValuePayload> =
            CustomPacketPayload.Type(CCAeroworks.id("set_combined_lever_value"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetCombinedLeverValuePayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SetCombinedLeverValuePayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SetCombinedLeverValuePayload =
                    SetCombinedLeverValuePayload(
                        buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf(16), buffer.readByte().toInt()
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetCombinedLeverValuePayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeUtf(payload.channel, 16)
                    buffer.writeByte(payload.value)
                }
            }

        private val lastAcceptedTick = ConcurrentHashMap<UUID, Long>()

        @JvmStatic
        fun handle(payload: SetCombinedLeverValuePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (payload.value !in -15..15 || !level.hasChunkAt(payload.pos) || !level.mayInteract(player, payload.pos)) return
            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (payload.socket !in 0 until desk.socketCount()) return
            val module = desk.module(payload.socket) ?: return
            if (!CombinedInputSource.isCombined(module, payload.channel)) return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return
            val maximumDistance = player.blockInteractionRange() + 1.0
            if (player.distanceToSqr(payload.pos.center) > maximumDistance * maximumDistance) return
            val tick = level.gameTime
            if (lastAcceptedTick.put(player.uuid, tick) == tick) return
            desk.setChannelFromController(payload.socket, payload.channel, payload.value)
        }
    }
}
