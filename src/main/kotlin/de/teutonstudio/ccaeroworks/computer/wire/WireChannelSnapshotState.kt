package de.teutonstudio.ccaeroworks.computer.wire

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistrySnapshot
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelGroupView

data class ControlChannelView(
    val id: String,
    val name: String,
    val value: Int,
    val overridden: Boolean,
    val connections: List<WireConnectionView>
)

data class ControlModuleGroupView(
    val id: String,
    val label: String,
    val deskId: String,
    val deskIndex: Int,
    val socket: Int,
    val socketName: String,
    val moduleId: String,
    val channels: List<ControlChannelView>
)

data class WireChannelManagerSnapshot(
    val wire: WireChannelBankView,
    val controlGroups: List<ControlModuleGroupView>,
    val userGroups: List<UserChannelGroupView>,
    /** Canonical channel id -> effective logical path. Synthetic path groups are never serialized. */
    val logicalPaths: Map<String, String> = emptyMap()
)

data class ChannelPathMutationFeedback(val success: Boolean, val message: String)

object ChannelPathMutationState {
    @Volatile private var current: ChannelPathMutationFeedback? = null
    fun accept(success: Boolean, message: String) { current = ChannelPathMutationFeedback(success, message) }
    fun get(): ChannelPathMutationFeedback? = current
    fun clear() { current = null }
}

object WireChannelSnapshotState {
    @Volatile
    private var current: WireChannelManagerSnapshot = emptySnapshot()

    fun accept(snapshot: WireChannelManagerSnapshot) {
        current = snapshot.copy(
            wire = snapshot.wire.copy(channels = snapshot.wire.channels.map { channel -> channel.copy(targets = channel.targets.toList()) }),
            controlGroups = snapshot.controlGroups.map { group ->
                group.copy(channels = group.channels.map { channel -> channel.copy(connections = channel.connections.toList()) })
            },
            userGroups = snapshot.userGroups.map { group -> group.copy(bindings = group.bindings.toList()) },
            logicalPaths = snapshot.logicalPaths.toMap()
        )
    }

    fun get(): WireChannelManagerSnapshot = current

    fun clear() {
        current = emptySnapshot()
        ChannelPathMutationState.clear()
    }

    private fun emptySnapshot(): WireChannelManagerSnapshot = WireChannelManagerSnapshot(
        wire = WireChannelBankView("none", false, emptyList()),
        controlGroups = emptyList(),
        userGroups = emptyList(),
        logicalPaths = emptyMap()
    )
}

/** GUI control rows are adapted from the canonical registry instead of rediscovering hardware. */
object ControlChannelSnapshotBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): List<ControlModuleGroupView> =
        runCatching { build(ChannelRegistry.snapshot(owner)) }.getOrElse { emptyList() }

    fun build(snapshot: ChannelRegistrySnapshot): List<ControlModuleGroupView> = snapshot.modules.map { module ->
        ControlModuleGroupView(
            id = module.id,
            label = module.label,
            deskId = module.deskId,
            deskIndex = module.deskIndex,
            socket = module.socket,
            socketName = module.socketName,
            moduleId = module.moduleId,
            channels = module.channels.map { channel ->
                ControlChannelView(channel.id, channel.name, channel.value, channel.overridden, channel.connections)
            }
        )
    }
}

object WireChannelManagerSnapshotBuilder {
    fun build(snapshot: ChannelRegistrySnapshot): WireChannelManagerSnapshot = WireChannelManagerSnapshot(
        wire = WireChannelBankView(
            backend = snapshot.wireBackend,
            enabled = snapshot.wireEnabled,
            channels = snapshot.wires.mapNotNull { channel ->
                val wireId = channel.wireId ?: return@mapNotNull null
                WireChannelView(
                    id = wireId,
                    name = channel.wireName ?: channel.name,
                    value = channel.value,
                    connections = channel.connections.size,
                    connected = channel.connections.isNotEmpty(),
                    targets = channel.connections
                )
            }
        ),
        controlGroups = ControlChannelSnapshotBuilder.build(snapshot),
        userGroups = snapshot.groups,
        logicalPaths = snapshot.channels.associate { it.id to it.logicalPath }
    )
}
