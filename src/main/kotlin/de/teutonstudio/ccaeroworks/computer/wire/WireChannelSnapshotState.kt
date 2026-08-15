package de.teutonstudio.ccaeroworks.computer.wire

import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannel
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.ControlDirectionalSignals
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelGroupView
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager

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
    val userGroups: List<UserChannelGroupView>
)

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
            },
            userGroups = snapshot.userGroups.map { group -> group.copy(bindings = group.bindings.toList()) }
        )
    }

    fun get(): WireChannelManagerSnapshot = current

    fun clear() {
        current = emptySnapshot()
    }

    private fun emptySnapshot(): WireChannelManagerSnapshot = WireChannelManagerSnapshot(
        wire = WireChannelBankView("none", false, emptyList()),
        controlGroups = emptyList(),
        userGroups = emptyList()
    )
}

object ControlChannelSnapshotBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): List<ControlModuleGroupView> {
        val level = owner.level ?: return emptyList()
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val members = network.members.associateBy { it.id }
        val dbwChannelsByDesk = hashMapOf<String, List<NativeDriveByWireChannel>>()
        val discovered = runCatching { ControlOverrideManager.listChannels(owner) }.getOrElse { return emptyList() }
        val groups = linkedMapOf<String, MutableControlModuleGroup>()

        discovered.forEach { row ->
            val deskId = row["desk"] as? String ?: return@forEach
            val member = members[deskId] ?: return@forEach
            val deskIndex = (row["deskIndex"] as? Number)?.toInt() ?: -1
            val socket = (row["socket"] as? Number)?.toInt() ?: return@forEach
            val socketName = row["socketName"] as? String ?: socket.toString()
            val moduleId = row["module"] as? String ?: return@forEach
            val channel = row["channel"] as? String ?: return@forEach
            val nativeValue = (row["value"] as? Number)?.toInt() ?: 0
            val overridden = row["overridden"] as? Boolean ?: false
            val sourcePos = member.pos
            val groupId = "module:$deskId:$socket:$moduleId"
            val nativeDbwChannels = dbwChannelsByDesk.getOrPut(deskId) { NativeDriveByWireChannels.channels(member.desk) }

            val group = groups.getOrPut(groupId) {
                MutableControlModuleGroup(
                    groupId,
                    moduleId.substringAfter(':', moduleId).replace('_', ' '),
                    deskId,
                    deskIndex,
                    socket,
                    socketName,
                    moduleId
                )
            }

            ControlDirectionalSignals.split(moduleId, socket, channel, nativeValue, nativeDbwChannels).forEach { signal ->
                val connections = signal.wireChannel?.let { owner.wireBank.connectionTargets(sourcePos, it) }.orEmpty()
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
                group.id, group.label, group.deskId, group.deskIndex, group.socket,
                group.socketName, group.moduleId, group.channels.toList()
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
