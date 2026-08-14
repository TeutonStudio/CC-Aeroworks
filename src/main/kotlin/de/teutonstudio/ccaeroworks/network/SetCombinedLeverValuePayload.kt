package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SetCombinedLeverValuePayload(
    val pos: BlockPos,
    val socket: Int,
    val channel: String,
    val value: Int,
    val finalValue: Boolean = false
) : CustomPacketPayload {
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
                        buffer.readBlockPos(),
                        buffer.readVarInt(),
                        buffer.readUtf(16),
                        buffer.readByte().toInt(),
                        buffer.readBoolean()
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetCombinedLeverValuePayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeUtf(payload.channel, 16)
                    buffer.writeByte(payload.value)
                    buffer.writeBoolean(payload.finalValue)
                }
            }

        private val rateStates = ConcurrentHashMap<RateKey, RateState>()

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
            val rateKey = RateKey(player.uuid, payload.pos.asLong(), payload.socket, payload.channel)
            val previous = rateStates[rateKey]
            if (previous?.tick == tick) {
                if (!payload.finalValue || previous.finalAccepted) return
                rateStates[rateKey] = RateState(tick, finalAccepted = true)
            } else {
                rateStates[rateKey] = RateState(tick, finalAccepted = payload.finalValue)
            }

            desk.setChannelFromController(payload.socket, payload.channel, payload.value)
        }

        private data class RateKey(val player: UUID, val pos: Long, val socket: Int, val channel: String)
        private data class RateState(val tick: Long, val finalAccepted: Boolean)
    }
}
