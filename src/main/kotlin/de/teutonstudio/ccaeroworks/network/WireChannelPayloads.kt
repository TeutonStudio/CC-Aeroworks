package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.channel.ChannelKind
import de.teutonstudio.ccaeroworks.computer.channel.ChannelPath
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelBindingView
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelGroupView
import de.teutonstudio.ccaeroworks.computer.channel.channelGroups
import de.teutonstudio.ccaeroworks.computer.wire.ChannelPathMutationState
import de.teutonstudio.ccaeroworks.computer.wire.ControlChannelView
import de.teutonstudio.ccaeroworks.computer.wire.ControlModuleGroupView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelBankView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelManagerSnapshot
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelManagerSnapshotBuilder
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
        @JvmField val TYPE = CustomPacketPayload.Type<RequestWireChannelSnapshotPayload>(CCAeroworks.id("request_wire_channel_snapshot"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestWireChannelSnapshotPayload> = object : StreamCodec<RegistryFriendlyByteBuf, RequestWireChannelSnapshotPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf) = RequestWireChannelSnapshotPayload()
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestWireChannelSnapshotPayload) = Unit
        }
        @JvmStatic fun handle(payload: RequestWireChannelSnapshotPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            sendSnapshot(player)
        }
    }
}

enum class WireChannelMutation { ADD, RENAME, REMOVE }

data class MutateWireChannelPayload(val mutation: WireChannelMutation, val id: UUID?, val name: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<MutateWireChannelPayload>(CCAeroworks.id("mutate_wire_channel"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MutateWireChannelPayload> = object : StreamCodec<RegistryFriendlyByteBuf, MutateWireChannelPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): MutateWireChannelPayload {
                val mutation = WireChannelMutation.entries.getOrNull(buffer.readVarInt()) ?: throw IllegalArgumentException("Invalid wire mutation")
                val id = if (buffer.readBoolean()) buffer.readUUID() else null
                return MutateWireChannelPayload(mutation, id, buffer.readUtf(32))
            }
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: MutateWireChannelPayload) {
                buffer.writeVarInt(payload.mutation.ordinal); buffer.writeBoolean(payload.id != null); payload.id?.let(buffer::writeUUID); buffer.writeUtf(payload.name, 32)
            }
        }
        @JvmStatic fun handle(payload: MutateWireChannelPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
            runCatching {
                when (payload.mutation) {
                    WireChannelMutation.ADD -> owner.wireBank.addChannel(payload.name)
                    WireChannelMutation.RENAME -> owner.wireBank.renameChannel(payload.id ?: return, payload.name)
                    WireChannelMutation.REMOVE -> owner.wireBank.removeChannel(payload.id ?: return)
                }
            }.onFailure { CCAeroworks.LOGGER.debug("Rejected wire channel UI mutation for {}: {}", player.scoreboardName, it.message) }
            sendSnapshot(player)
        }
    }
}

data class MutateChannelPathPayload(val targetId: String, val path: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<MutateChannelPathPayload>(CCAeroworks.id("mutate_channel_path"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MutateChannelPathPayload> = object : StreamCodec<RegistryFriendlyByteBuf, MutateChannelPathPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf) = MutateChannelPathPayload(buffer.readUtf(512), buffer.readUtf(ChannelPath.MAX_PATH_LENGTH))
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: MutateChannelPathPayload) {
                buffer.writeUtf(payload.targetId, 512); buffer.writeUtf(payload.path, ChannelPath.MAX_PATH_LENGTH)
            }
        }
        @JvmStatic fun handle(payload: MutateChannelPathPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
            val result = runCatching { ChannelRegistry.setLogicalPath(owner, payload.targetId, payload.path) }
            result.onSuccess { descriptor ->
                PacketDistributor.sendToPlayer(player, ChannelPathMutationResultPayload(true, "Path: ${descriptor.logicalPath}"))
            }.onFailure { throwable ->
                CCAeroworks.LOGGER.debug("Rejected logical channel path mutation for {}: {}", player.scoreboardName, throwable.message)
                PacketDistributor.sendToPlayer(player, ChannelPathMutationResultPayload(false, throwable.message ?: "Channel rename failed"))
            }
            sendSnapshot(player)
        }
    }
}

data class ChannelPathMutationResultPayload(val success: Boolean, val message: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<ChannelPathMutationResultPayload>(CCAeroworks.id("channel_path_mutation_result"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ChannelPathMutationResultPayload> = object : StreamCodec<RegistryFriendlyByteBuf, ChannelPathMutationResultPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf) = ChannelPathMutationResultPayload(buffer.readBoolean(), buffer.readUtf(256))
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: ChannelPathMutationResultPayload) {
                buffer.writeBoolean(payload.success); buffer.writeUtf(payload.message, 256)
            }
        }
        @JvmStatic fun handle(payload: ChannelPathMutationResultPayload, context: IPayloadContext) {
            ChannelPathMutationState.accept(payload.success, payload.message)
        }
    }
}

enum class ChannelGroupMutation { ADD, RENAME, REMOVE, BIND, RENAME_BINDING, UNBIND }

data class MutateChannelGroupPayload(
    val mutation: ChannelGroupMutation,
    val groupId: UUID?,
    val name: String,
    val alias: String,
    val targetId: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<MutateChannelGroupPayload>(CCAeroworks.id("mutate_channel_group"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MutateChannelGroupPayload> = object : StreamCodec<RegistryFriendlyByteBuf, MutateChannelGroupPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): MutateChannelGroupPayload {
                val mutation = ChannelGroupMutation.entries.getOrNull(buffer.readVarInt()) ?: throw IllegalArgumentException("Invalid channel-group mutation")
                val id = if (buffer.readBoolean()) buffer.readUUID() else null
                return MutateChannelGroupPayload(mutation, id, buffer.readUtf(32), buffer.readUtf(32), buffer.readUtf(512))
            }
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: MutateChannelGroupPayload) {
                buffer.writeVarInt(payload.mutation.ordinal); buffer.writeBoolean(payload.groupId != null); payload.groupId?.let(buffer::writeUUID)
                buffer.writeUtf(payload.name, 32); buffer.writeUtf(payload.alias, 32); buffer.writeUtf(payload.targetId, 512)
            }
        }
        @JvmStatic fun handle(payload: MutateChannelGroupPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
            runCatching {
                val bank = owner.channelGroups()
                when (payload.mutation) {
                    ChannelGroupMutation.ADD -> bank.addGroup(payload.name)
                    ChannelGroupMutation.RENAME -> bank.renameGroup(payload.groupId ?: return, payload.name)
                    ChannelGroupMutation.REMOVE -> bank.removeGroup(payload.groupId ?: return)
                    ChannelGroupMutation.BIND -> {
                        val id = payload.groupId ?: return
                        require(ChannelRegistry.findById(owner, payload.targetId) != null) { "Unknown channel target '${payload.targetId}'" }
                        bank.bind(id, payload.alias, payload.targetId)
                    }
                    ChannelGroupMutation.RENAME_BINDING -> bank.renameBinding(payload.groupId ?: return, payload.alias, payload.name)
                    ChannelGroupMutation.UNBIND -> bank.unbind(payload.groupId ?: return, payload.alias)
                }
            }.onFailure { CCAeroworks.LOGGER.debug("Rejected channel-group UI mutation for {}: {}", player.scoreboardName, it.message) }
            sendSnapshot(player)
        }
    }
}

data class WireChannelSnapshotPayload(val snapshot: WireChannelManagerSnapshot) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        private const val MAX_CONNECTIONS_PER_CHANNEL = 128
        private const val MAX_LOGICAL_PATHS = 256
        @JvmField val TYPE = CustomPacketPayload.Type<WireChannelSnapshotPayload>(CCAeroworks.id("wire_channel_snapshot"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, WireChannelSnapshotPayload> = object : StreamCodec<RegistryFriendlyByteBuf, WireChannelSnapshotPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): WireChannelSnapshotPayload {
                val backend = buffer.readUtf(32); val enabled = buffer.readBoolean()
                val groupCount = buffer.readVarInt(); require(groupCount in 0..192)
                val groups = ArrayList<ControlModuleGroupView>(groupCount)
                repeat(groupCount) {
                    val id = buffer.readUtf(256); val label = buffer.readUtf(64); val deskId = buffer.readUtf(64)
                    val deskIndex = buffer.readVarInt(); val socket = buffer.readVarInt(); val socketName = buffer.readUtf(32); val moduleId = buffer.readUtf(128)
                    val channelCount = buffer.readVarInt(); require(channelCount in 0..32)
                    val channels = ArrayList<ControlChannelView>(channelCount)
                    repeat(channelCount) {
                        channels += ControlChannelView(buffer.readUtf(320), buffer.readUtf(64), buffer.readVarInt().coerceIn(0, 15), buffer.readBoolean(), readConnections(buffer))
                    }
                    groups += ControlModuleGroupView(id, label, deskId, deskIndex, socket, socketName, moduleId, channels)
                }
                val wireCount = buffer.readVarInt(); require(wireCount in 0..32)
                val wires = ArrayList<WireChannelView>(wireCount)
                repeat(wireCount) {
                    val id = buffer.readUUID(); val name = buffer.readUtf(32); val value = buffer.readVarInt().coerceIn(0, 15); val targets = readConnections(buffer)
                    wires += WireChannelView(id, name, value, targets.size, targets.isNotEmpty(), targets)
                }
                val userCount = buffer.readVarInt(); require(userCount in 0..32)
                val userGroups = ArrayList<UserChannelGroupView>(userCount)
                repeat(userCount) {
                    val id = buffer.readUUID(); val name = buffer.readUtf(32); val bindingCount = buffer.readVarInt(); require(bindingCount in 0..64)
                    val bindings = ArrayList<UserChannelBindingView>(bindingCount)
                    repeat(bindingCount) {
                        val alias = buffer.readUtf(32); val targetId = buffer.readUtf(512); val targetLabel = buffer.readUtf(192); val available = buffer.readBoolean()
                        val value = if (buffer.readBoolean()) buffer.readVarInt().coerceIn(0, 15) else null
                        val kindOrdinal = buffer.readVarInt(); val kind = if (kindOrdinal == 0) null else ChannelKind.entries.getOrNull(kindOrdinal - 1)
                        bindings += UserChannelBindingView(alias, targetId, targetLabel, available, value, kind)
                    }
                    userGroups += UserChannelGroupView(id, name, bindings)
                }
                val pathCount = buffer.readVarInt(); require(pathCount in 0..MAX_LOGICAL_PATHS)
                val logicalPaths = linkedMapOf<String, String>()
                repeat(pathCount) { logicalPaths[buffer.readUtf(512)] = buffer.readUtf(ChannelPath.MAX_PATH_LENGTH) }
                return WireChannelSnapshotPayload(WireChannelManagerSnapshot(WireChannelBankView(backend, enabled, wires), groups, userGroups, logicalPaths))
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, payload: WireChannelSnapshotPayload) {
                val snapshot = payload.snapshot
                buffer.writeUtf(snapshot.wire.backend, 32); buffer.writeBoolean(snapshot.wire.enabled)
                buffer.writeVarInt(snapshot.controlGroups.size.coerceAtMost(192))
                snapshot.controlGroups.take(192).forEach { group ->
                    buffer.writeUtf(group.id, 256); buffer.writeUtf(group.label, 64); buffer.writeUtf(group.deskId, 64); buffer.writeVarInt(group.deskIndex)
                    buffer.writeVarInt(group.socket); buffer.writeUtf(group.socketName, 32); buffer.writeUtf(group.moduleId, 128); buffer.writeVarInt(group.channels.size.coerceAtMost(32))
                    group.channels.take(32).forEach { channel ->
                        buffer.writeUtf(channel.id, 320); buffer.writeUtf(channel.name, 64); buffer.writeVarInt(channel.value.coerceIn(0, 15)); buffer.writeBoolean(channel.overridden); writeConnections(buffer, channel.connections)
                    }
                }
                buffer.writeVarInt(snapshot.wire.channels.size.coerceAtMost(32))
                snapshot.wire.channels.take(32).forEach { channel ->
                    buffer.writeUUID(channel.id); buffer.writeUtf(channel.name, 32); buffer.writeVarInt(channel.value.coerceIn(0, 15)); writeConnections(buffer, channel.targets)
                }
                buffer.writeVarInt(snapshot.userGroups.size.coerceAtMost(32))
                snapshot.userGroups.take(32).forEach { group ->
                    buffer.writeUUID(group.id); buffer.writeUtf(group.name, 32); buffer.writeVarInt(group.bindings.size.coerceAtMost(64))
                    group.bindings.take(64).forEach { binding ->
                        buffer.writeUtf(binding.alias, 32); buffer.writeUtf(binding.targetId, 512); buffer.writeUtf(binding.targetLabel, 192); buffer.writeBoolean(binding.available)
                        buffer.writeBoolean(binding.value != null); binding.value?.let { buffer.writeVarInt(it.coerceIn(0, 15)) }
                        buffer.writeVarInt(binding.kind?.ordinal?.plus(1) ?: 0)
                    }
                }
                val paths = snapshot.logicalPaths.entries.take(MAX_LOGICAL_PATHS)
                buffer.writeVarInt(paths.size)
                paths.forEach { (id, path) -> buffer.writeUtf(id, 512); buffer.writeUtf(path, ChannelPath.MAX_PATH_LENGTH) }
            }
        }

        private fun readConnections(buffer: RegistryFriendlyByteBuf): List<WireConnectionView> {
            val count = buffer.readVarInt(); require(count in 0..MAX_CONNECTIONS_PER_CHANNEL)
            return List(count) { WireConnectionView(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readUtf(8)) }
        }
        private fun writeConnections(buffer: RegistryFriendlyByteBuf, connections: List<WireConnectionView>) {
            val limited = connections.take(MAX_CONNECTIONS_PER_CHANNEL); buffer.writeVarInt(limited.size)
            limited.forEach { buffer.writeInt(it.x); buffer.writeInt(it.y); buffer.writeInt(it.z); buffer.writeUtf(it.side, 8) }
        }
        @JvmStatic fun handle(payload: WireChannelSnapshotPayload, context: IPayloadContext) { WireChannelSnapshotState.accept(payload.snapshot) }
    }
}

private fun sendSnapshot(player: ServerPlayer) {
    val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: return
    val registry = runCatching { ChannelRegistry.snapshot(owner) }.getOrNull() ?: return
    PacketDistributor.sendToPlayer(player, WireChannelSnapshotPayload(WireChannelManagerSnapshotBuilder.build(registry)))
}
