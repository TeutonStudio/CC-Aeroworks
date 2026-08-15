package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelBankView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelSnapshotState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelView
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

class RequestWireChannelSnapshotPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<RequestWireChannelSnapshotPayload>(
            CCAeroworks.id("request_wire_channel_snapshot")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestWireChannelSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, RequestWireChannelSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = RequestWireChannelSnapshotPayload()
                override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestWireChannelSnapshotPayload) = Unit
            }

        @JvmStatic
        fun handle(payload: RequestWireChannelSnapshotPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            sendSnapshot(player)
        }
    }
}

enum class WireChannelMutation {
    ADD,
    RENAME,
    REMOVE
}

data class MutateWireChannelPayload(
    val mutation: WireChannelMutation,
    val id: UUID?,
    val name: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<MutateWireChannelPayload>(
            CCAeroworks.id("mutate_wire_channel")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MutateWireChannelPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, MutateWireChannelPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): MutateWireChannelPayload {
                    val ordinal = buffer.readVarInt()
                    val mutation = WireChannelMutation.entries.getOrNull(ordinal)
                        ?: throw IllegalArgumentException("Invalid wire mutation: $ordinal")
                    val id = if (buffer.readBoolean()) buffer.readUUID() else null
                    return MutateWireChannelPayload(mutation, id, buffer.readUtf(32))
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: MutateWireChannelPayload) {
                    buffer.writeVarInt(payload.mutation.ordinal)
                    buffer.writeBoolean(payload.id != null)
                    payload.id?.let(buffer::writeUUID)
                    buffer.writeUtf(payload.name, 32)
                }
            }

        @JvmStatic
        fun handle(payload: MutateWireChannelPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
            runCatching {
                when (payload.mutation) {
                    WireChannelMutation.ADD -> owner.wireBank.addChannel(payload.name)
                    WireChannelMutation.RENAME -> owner.wireBank.renameChannel(payload.id ?: return, payload.name)
                    WireChannelMutation.REMOVE -> owner.wireBank.removeChannel(payload.id ?: return)
                }
            }.onFailure { throwable ->
                CCAeroworks.LOGGER.debug("Rejected wire channel UI mutation for {}: {}", player.scoreboardName, throwable.message)
            }
            sendSnapshot(player)
        }
    }
}

data class WireChannelSnapshotPayload(
    val snapshot: WireChannelBankView
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<WireChannelSnapshotPayload>(
            CCAeroworks.id("wire_channel_snapshot")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, WireChannelSnapshotPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, WireChannelSnapshotPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): WireChannelSnapshotPayload {
                    val backend = buffer.readUtf(32)
                    val enabled = buffer.readBoolean()
                    val count = buffer.readVarInt()
                    require(count in 0..32) { "Invalid wire channel count: $count" }
                    val channels = ArrayList<WireChannelView>(count)
                    repeat(count) {
                        val id = buffer.readUUID()
                        val name = buffer.readUtf(32)
                        val value = buffer.readVarInt()
                        val connections = buffer.readVarInt()
                        channels += WireChannelView(id, name, value, connections, connections > 0)
                    }
                    return WireChannelSnapshotPayload(WireChannelBankView(backend, enabled, channels))
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: WireChannelSnapshotPayload) {
                    buffer.writeUtf(payload.snapshot.backend, 32)
                    buffer.writeBoolean(payload.snapshot.enabled)
                    buffer.writeVarInt(payload.snapshot.channels.size.coerceAtMost(32))
                    payload.snapshot.channels.take(32).forEach { channel ->
                        buffer.writeUUID(channel.id)
                        buffer.writeUtf(channel.name, 32)
                        buffer.writeVarInt(channel.value)
                        buffer.writeVarInt(channel.connections)
                    }
                }
            }

        @JvmStatic
        fun handle(payload: WireChannelSnapshotPayload, context: IPayloadContext) {
            WireChannelSnapshotState.accept(payload.snapshot)
        }
    }
}

private fun sendSnapshot(player: ServerPlayer) {
    val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
    PacketDistributor.sendToPlayer(player, WireChannelSnapshotPayload(owner.wireBank.snapshot()))
}
