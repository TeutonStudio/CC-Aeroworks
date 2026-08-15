package de.teutonstudio.ccaeroworks.computer.wire

import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.ControlDirectionalSignals
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager

data class ControlChannelView(
    val id: String,
    val name: String,
    /** Physical redstone-facing direction signal, always 0..15. */
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
    val controlGroups: List<ControlModuleGroupView>
)

/** Latest server snapshot used by the ComputerControlDesk channel manager. */
object WireChannelSnapshotState {
    @Volatile
    private var current: WireChannelManagerSnapshot = emptySnapshot()

    fun accept(snapshot: WireChannelManagerSnapshot) {
        current = snapshot.copy(
            wire = snapshot.wire.copy(channels = snapshot.wire.channels.map { channel ->
                channel.copy(targets = channel.targets.toList())
            }),
            controlGroups = snapshot.controlGroups.map { group ->
                group.copy(channels = group.channels.map { channel ->
                    channel.copy(connections = channel.connections.toList())
                })
            }
        )
    }

    fun get(): WireChannelManagerSnapshot = current

    fun clear() {
        current = emptySnapshot()
    }

    private fun emptySnapshot(): WireChannelManagerSnapshot = WireChannelManagerSnapshot(
        wire = WireChannelBankView("none", false, emptyList()),
        controlGroups = emptyList()
    )
}

/**
 * Projects native signed Aeroworks axes into the same two-direction 0..15 channels that their
 * physical Redstone Link/Drive By Wire output exposes. Runtime control authority still belongs to
 * ControlOverrideManager; this is presentation/discovery only.
 */
object ControlChannelSnapshotBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): List<ControlModuleGroupView> {
        val level = owner.level ?: return emptyList()
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val positions = network.members.associate { it.id to it.pos }
        val dbwChannelsByPos = hashMapOf<Long, List<String>>()
        val discovered = runCatching { ControlOverrideManager.listChannels(owner) }.getOrElse { return emptyList() }
        val groups = linkedMapOf<String, MutableControlModuleGroup>()

        discovered.forEach { row ->
            val deskId = row["desk"] as? String ?: return@forEach
            val deskIndex = (row["deskIndex"] as? Number)?.toInt() ?: -1
            val socket = (row["socket"] as? Number)?.toInt() ?: return@forEach
            val socketName = row["socketName"] as? String ?: socket.toString()
            val moduleId = row["module"] as? String ?: return@forEach
            val channel = row["channel"] as? String ?: return@forEach
            val nativeValue = (row["value"] as? Number)?.toInt() ?: 0
            val overridden = row["overridden"] as? Boolean ?: false
            val sourcePos = positions[deskId] ?: return@forEach
            val groupId = "module:$deskId:$socket:$moduleId"
            val nativeDbwChannels = dbwChannelsByPos.getOrPut(sourcePos.asLong()) {
                NativeDriveByWireChannels.channels(level, sourcePos)
            }

            val group = groups.getOrPut(groupId) {
                MutableControlModuleGroup(
                    id = groupId,
                    label = moduleId.substringAfter(':', moduleId).replace('_', ' '),
                    deskId = deskId,
                    deskIndex = deskIndex,
                    socket = socket,
                    socketName = socketName,
                    moduleId = moduleId
                )
            }

            ControlDirectionalSignals.split(moduleId, channel, nativeValue, nativeDbwChannels).forEach { signal ->
                val connections = signal.wireChannel
                    ?.let { owner.wireBank.connectionTargets(sourcePos, it) }
                    .orEmpty()
                group.channels += ControlChannelView(
                    id = "control:$deskId:$socket:$moduleId:$channel:${signal.direction}",
                    name = signal.label,
                    value = signal.value,
                    overridden = overridden,
                    connections = connections
                )
            }
        }

        return groups.values.map { group ->
            ControlModuleGroupView(
                id = group.id,
                label = group.label,
                deskId = group.deskId,
                deskIndex = group.deskIndex,
                socket = group.socket,
                socketName = group.socketName,
                moduleId = group.moduleId,
                channels = group.channels.toList()
            )
        }
    }

    private data class MutableControlModuleGroup(
        val id: String,
        val label: String,
        val deskId: String,
        val deskIndex: Int,
        val socket: Int,
        val socketName: String,
        val moduleId: String,
        val channels: MutableList<ControlChannelView> = arrayListOf()
    )
}
