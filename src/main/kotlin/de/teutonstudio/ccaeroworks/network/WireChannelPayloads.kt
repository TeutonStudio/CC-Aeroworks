package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.wire.ControlChannelSnapshotBuilder
import de.teutonstudio.ccaeroworks.computer.wire.ControlChannelView
import de.teutonstudio.ccaeroworks.computer.wire.ControlModuleGroupView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelBankView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelManagerSnapshot
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelSnapshotState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelView
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
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
    val snapshot: WireChannelManagerSnapshot
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val MAX_CONNECTIONS_PER_CHANNEL = 128

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
                    val groupCount = buffer.readVarInt()
                    require(groupCount in 0..192) { "Invalid control module group count: $groupCount" }
                    val groups = ArrayList<ControlModuleGroupView>(groupCount)
                    repeat(groupCount) {
                        val id = buffer.readUtf(256)
                        val label = buffer.readUtf(64)
                        val deskId = buffer.readUtf(64)
                        val deskIndex = buffer.readVarInt()
                        val socket = buffer.readVarInt()
                        val socketName = buffer.readUtf(32)
                        val moduleId = buffer.readUtf(128)
                        val channelCount = buffer.readVarInt()
                        require(channelCount in 0..32) { "Invalid control channel count: $channelCount" }
                        val channels = ArrayList<ControlChannelView>(channelCount)
                        repeat(channelCount) {
                            channels += ControlChannelView(
                                id = buffer.readUtf(320),
                                name = buffer.readUtf(64),
                                value = buffer.readVarInt().coerceIn(0, 15),
                                overridden = buffer.readBoolean(),
                                connections = readConnections(buffer)
                            )
                        }
                        groups += ControlModuleGroupView(
                            id = id,
                            label = label,
                            deskId = deskId,
                            deskIndex = deskIndex,
                            socket = socket,
                            socketName = socketName,
                            moduleId = moduleId,
                            channels = channels
                        )
                    }

                    val count = buffer.readVarInt()
                    require(count in 0..32) { "Invalid wire channel count: $count" }
                    val channels = ArrayList<WireChannelView>(count)
                    repeat(count) {
                        val id = buffer.readUUID()
                        val name = buffer.readUtf(32)
                        val value = buffer.readVarInt().coerceIn(0, 15)
                        val targets = readConnections(buffer)
                        channels += WireChannelView(
                            id = id,
                            name = name,
                            value = value,
                            connections = targets.size,
                            connected = targets.isNotEmpty(),
                            targets = targets
                        )
                    }
                    return WireChannelSnapshotPayload(
                        WireChannelManagerSnapshot(
                            wire = WireChannelBankView(backend, enabled, channels),
                            controlGroups = groups
                        )
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: WireChannelSnapshotPayload) {
                    val snapshot = payload.snapshot
                    buffer.writeUtf(snapshot.wire.backend, 32)
                    buffer.writeBoolean(snapshot.wire.enabled)
                    buffer.writeVarInt(snapshot.controlGroups.size.coerceAtMost(192))
                    snapshot.controlGroups.take(192).forEach { group ->
                        buffer.writeUtf(group.id, 256)
                        buffer.writeUtf(group.label, 64)
                        buffer.writeUtf(group.deskId, 64)
                        buffer.writeVarInt(group.deskIndex)
                        buffer.writeVarInt(group.socket)
                        buffer.writeUtf(group.socketName, 32)
                        buffer.writeUtf(group.moduleId, 128)
                        buffer.writeVarInt(group.channels.size.coerceAtMost(32))
                        group.channels.take(32).forEach { channel ->
                            buffer.writeUtf(channel.id, 320)
                            buffer.writeUtf(channel.name, 64)
                            buffer.writeVarInt(channel.value.coerceIn(0, 15))
                            buffer.writeBoolean(channel.overridden)
                            writeConnections(buffer, channel.connections)
                        }
                    }
                    buffer.writeVarInt(snapshot.wire.channels.size.coerceAtMost(32))
                    snapshot.wire.channels.take(32).forEach { channel ->
                        buffer.writeUUID(channel.id)
                        buffer.writeUtf(channel.name, 32)
                        buffer.writeVarInt(channel.value.coerceIn(0, 15))
                        writeConnections(buffer, channel.targets)
                    }
                }
            }

        private fun readConnections(buffer: RegistryFriendlyByteBuf): List<WireConnectionView> {
            val count = buffer.readVarInt()
            require(count in 0..MAX_CONNECTIONS_PER_CHANNEL) { "Invalid wire connection count: $count" }
            return List(count) {
                WireConnectionView(
                    x = buffer.readInt(),
                    y = buffer.readInt(),
                    z = buffer.readInt(),
                    side = buffer.readUtf(8)
                )
            }
        }

        private fun writeConnections(buffer: RegistryFriendlyByteBuf, connections: List<WireConnectionView>) {
            val limited = connections.take(MAX_CONNECTIONS_PER_CHANNEL)
            buffer.writeVarInt(limited.size)
            limited.forEach { connection ->
                buffer.writeInt(connection.x)
                buffer.writeInt(connection.y)
                buffer.writeInt(connection.z)
                buffer.writeUtf(connection.side, 8)
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
    val snapshot = WireChannelManagerSnapshot(
        wire = owner.wireBank.snapshot(),
        controlGroups = ControlChannelSnapshotBuilder.build(owner)
    )
    PacketDistributor.sendToPlayer(player, WireChannelSnapshotPayload(snapshot))
}
