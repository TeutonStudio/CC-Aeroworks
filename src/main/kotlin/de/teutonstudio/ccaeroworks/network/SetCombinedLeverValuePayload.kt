package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralState
import de.teutonstudio.ccaeroworks.compat.sable.SableSpatial
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Legacy single-channel Combined packet kept for protocol compatibility.
 *
 * New clients send CombinedControlSamplePayload. If an older client still uses this packet, later
 * packets are intentionally allowed to overwrite earlier packets in the same server tick instead
 * of the former first-value-wins behaviour.
 */
data class SetCombinedLeverValuePayload(val pos: BlockPos, val socket: Int, val channel: String, val value: Int) :
    CustomPacketPayload {
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

        @JvmStatic
        fun handle(payload: SetCombinedLeverValuePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (payload.value !in -15..15 || !level.hasChunkAt(payload.pos)) return
            if (!level.mayInteract(player, SableSpatial.worldBlockPos(level, payload.pos))) return
            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (payload.socket !in 0 until desk.socketCount()) return
            val module = desk.module(payload.socket) ?: return
            if (!CombinedInputSource.isCombined(module, payload.channel)) return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return

            val network = ConsoleMultiblockManager.resolve(level, payload.pos)
            val maximumDistance = player.blockInteractionRange() + 1.0
            if (network.members.none {
                    SableSpatial.distanceSquared(level, player.position(), it.pos.center) <=
                        maximumDistance * maximumDistance
                }
            ) return

            val previousValue = module.value(payload.channel).coerceIn(-15, 15)
            desk.setChannelFromController(payload.socket, payload.channel, payload.value)
            val effectiveValue = module.value(payload.channel).coerceIn(-15, 15)
            if (effectiveValue != previousValue) {
                ControlDeskPeripheralState.queueImmediateInput(
                    desk,
                    payload.socket,
                    CombinedInputSource.moduleId(module),
                    payload.channel,
                    effectiveValue
                )
            }
        }
    }
}
