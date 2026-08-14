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

data class CombinedChannelValue(val channel: String, val value: Int)

data class CombinedControlSamplePayload(
    val pos: BlockPos,
    val socket: Int,
    val sequence: Int,
    val finalSample: Boolean,
    val values: List<CombinedChannelValue>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val MAX_CHANNELS = 8

        @JvmField
        val TYPE: CustomPacketPayload.Type<CombinedControlSamplePayload> =
            CustomPacketPayload.Type(CCAeroworks.id("combined_control_sample"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CombinedControlSamplePayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, CombinedControlSamplePayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): CombinedControlSamplePayload {
                    val pos = buffer.readBlockPos()
                    val socket = buffer.readVarInt()
                    val sequence = buffer.readVarInt()
                    val finalSample = buffer.readBoolean()
                    val count = buffer.readVarInt()
                    require(count in 1..MAX_CHANNELS) { "Invalid Combined channel count $count" }
                    val values = ArrayList<CombinedChannelValue>(count)
                    repeat(count) {
                        values += CombinedChannelValue(buffer.readUtf(16), buffer.readByte().toInt())
                    }
                    return CombinedControlSamplePayload(pos, socket, sequence, finalSample, values)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: CombinedControlSamplePayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeVarInt(payload.sequence)
                    buffer.writeBoolean(payload.finalSample)
                    buffer.writeVarInt(payload.values.size)
                    payload.values.forEach {
                        buffer.writeUtf(it.channel, 16)
                        buffer.writeByte(it.value)
                    }
                }
            }

        @JvmStatic
        fun handle(payload: CombinedControlSamplePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (payload.sequence < 0 || payload.values.isEmpty() || payload.values.size > MAX_CHANNELS) return
            if (payload.values.map { it.channel }.toSet().size != payload.values.size) return
            if (payload.values.any { it.value !in -15..15 || it.channel.isBlank() || it.channel.length > 16 }) return
            if (!level.hasChunkAt(payload.pos) || !level.mayInteract(player, payload.pos)) return

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (payload.socket !in 0 until desk.socketCount()) return
            val module = desk.module(payload.socket) ?: return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return
            if (payload.values.any { !CombinedInputSource.isCombined(module, it.channel) }) return

            val network = ConsoleMultiblockManager.resolve(level, payload.pos)
            val maximumDistance = player.blockInteractionRange() + 1.0
            if (network.members.none {
                    SableSpatial.distanceSquared(level, player.position(), it.pos.center) <=
                        maximumDistance * maximumDistance
                }
            ) return

            val moduleId = CombinedInputSource.moduleId(module)
            payload.values.forEach { entry ->
                val previousValue = module.value(entry.channel).coerceIn(-15, 15)
                desk.setChannelFromController(payload.socket, entry.channel, entry.value)
                val effectiveValue = module.value(entry.channel).coerceIn(-15, 15)
                if (effectiveValue != previousValue) {
                    ControlDeskPeripheralState.queueImmediateInput(
                        desk,
                        payload.socket,
                        moduleId,
                        entry.channel,
                        effectiveValue
                    )
                }
            }
        }
    }
}
