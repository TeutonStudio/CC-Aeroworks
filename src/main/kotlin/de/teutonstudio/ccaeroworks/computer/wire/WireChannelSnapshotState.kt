package de.teutonstudio.ccaeroworks.computer.wire

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager

data class ControlChannelView(
    val id: String,
    val name: String,
    val value: Int,
    val overridden: Boolean
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
            wire = snapshot.wire.copy(channels = snapshot.wire.channels.toList()),
            controlGroups = snapshot.controlGroups.map { group ->
                group.copy(channels = group.channels.toList())
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
 * Projects the existing ControlOverrideManager discovery data into immutable UI groups.
 * Control channels remain owned by Aeroworks and the override manager; this object stores nothing.
 */
object ControlChannelSnapshotBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): List<ControlModuleGroupView> {
        val discovered = runCatching { ControlOverrideManager.listChannels(owner) }.getOrElse { return emptyList() }
        val groups = linkedMapOf<String, MutableControlModuleGroup>()

        discovered.forEach { row ->
            val deskId = row["desk"] as? String ?: return@forEach
            val deskIndex = (row["deskIndex"] as? Number)?.toInt() ?: -1
            val socket = (row["socket"] as? Number)?.toInt() ?: return@forEach
            val socketName = row["socketName"] as? String ?: socket.toString()
            val moduleId = row["module"] as? String ?: return@forEach
            val channel = row["channel"] as? String ?: return@forEach
            val value = (row["value"] as? Number)?.toInt() ?: 0
            val overridden = row["overridden"] as? Boolean ?: false
            val groupId = "module:$deskId:$socket:$moduleId"

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
            group.channels += ControlChannelView(
                id = "control:$deskId:$socket:$moduleId:$channel",
                name = channel,
                value = value,
                overridden = overridden
            )
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
