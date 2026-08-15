package de.teutonstudio.ccaeroworks.computer.channel

import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideCommand
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import java.util.UUID

enum class ChannelKind { CONTROL, WIRE }

data class ControlChannelTarget(
    val deskId: String,
    val socket: Int,
    val nativeChannel: String,
    val sign: Int
)

data class ChannelDescriptor(
    val id: String,
    val name: String,
    val kind: ChannelKind,
    val value: Int,
    val available: Boolean,
    val connections: List<WireConnectionView>,
    val controlTarget: ControlChannelTarget? = null,
    val wireId: UUID? = null,
    val wireName: String? = null,
    val overridden: Boolean = false
)

data class ChannelModuleDescriptor(
    val id: String,
    val label: String,
    val deskId: String,
    val deskIndex: Int,
    val socket: Int,
    val socketName: String,
    val moduleId: String,
    val channels: List<ChannelDescriptor>
)

data class UserChannelBindingView(
    val alias: String,
    val targetId: String,
    val targetLabel: String,
    val available: Boolean,
    val value: Int?,
    val kind: ChannelKind?
)

data class UserChannelGroupView(
    val id: UUID,
    val name: String,
    val bindings: List<UserChannelBindingView>
)

data class ChannelRegistrySnapshot(
    val modules: List<ChannelModuleDescriptor>,
    val wires: List<ChannelDescriptor>,
    val groups: List<UserChannelGroupView>,
    val wireBackend: String,
    val wireEnabled: Boolean
) {
    val channels: List<ChannelDescriptor> get() = modules.flatMap(ChannelModuleDescriptor::channels) + wires
}

/** One canonical logical view over physical controls, virtual wire channels and user aliases. */
object ChannelRegistry {
    fun snapshot(owner: ComputerControlDeskBlockEntity): ChannelRegistrySnapshot {
        val level = owner.level ?: throw LuaException("The ComputerControlDesk is no longer loaded")
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val members = network.members.associateBy { it.id }
        val dbwByDesk = hashMapOf<String, List<de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannel>>()
        val moduleMap = linkedMapOf<String, MutableModule>()

        ControlOverrideManager.listChannels(owner).forEach { row ->
            val deskId = row["desk"] as? String ?: return@forEach
            val member = members[deskId] ?: return@forEach
            val socket = (row["socket"] as? Number)?.toInt() ?: return@forEach
            val socketName = row["socketName"] as? String ?: socket.toString()
            val moduleId = row["module"] as? String ?: return@forEach
            val nativeChannel = row["channel"] as? String ?: return@forEach
            val nativeValue = (row["value"] as? Number)?.toInt() ?: 0
            val overridden = row["overridden"] == true
            val moduleKey = "module:$deskId:$socket:$moduleId"
            val nativeDbw = dbwByDesk.getOrPut(deskId) { NativeDriveByWireChannels.channels(member.desk) }
            val module = moduleMap.getOrPut(moduleKey) {
                MutableModule(
                    moduleKey,
                    moduleId.substringAfter(':', moduleId).replace('_', ' '),
                    deskId,
                    (row["deskIndex"] as? Number)?.toInt() ?: member.index,
                    socket,
                    socketName,
                    moduleId
                )
            }
            ControlDirectionalSignals.split(moduleId, socket, nativeChannel, nativeValue, nativeDbw).forEach { signal ->
                val id = "control:$deskId:$socket:$moduleId:$nativeChannel:${signal.direction}"
                module.channels += ChannelDescriptor(
                    id = id,
                    name = signal.label,
                    kind = ChannelKind.CONTROL,
                    value = signal.value,
                    available = true,
                    connections = signal.wireChannel?.let { owner.wireBank.connectionTargets(member.pos, it) }.orEmpty(),
                    controlTarget = ControlChannelTarget(deskId, socket, nativeChannel, signal.sign),
                    overridden = overridden
                )
            }
        }

        val wireSnapshot = owner.wireBank.snapshot()
        val wires = wireSnapshot.channels.map { channel ->
            ChannelDescriptor(
                id = "wire:${channel.id}",
                name = channel.name,
                kind = ChannelKind.WIRE,
                value = channel.value,
                available = true,
                connections = channel.targets,
                wireId = channel.id,
                wireName = channel.name
            )
        }
        val modules = moduleMap.values.map { it.freeze() }
        val byId = (modules.flatMap(ChannelModuleDescriptor::channels) + wires).associateBy(ChannelDescriptor::id)
        val groups = owner.channelGroups().definitions().map { group ->
            UserChannelGroupView(
                group.id,
                group.name,
                group.bindings.map { binding ->
                    val target = byId[binding.targetId]
                    UserChannelBindingView(
                        binding.alias,
                        binding.targetId,
                        target?.name ?: binding.targetId,
                        target != null,
                        target?.value,
                        target?.kind
                    )
                }
            )
        }
        return ChannelRegistrySnapshot(modules, wires, groups, wireSnapshot.backend, wireSnapshot.enabled)
    }

    fun findById(owner: ComputerControlDeskBlockEntity, id: String): ChannelDescriptor? =
        snapshot(owner).channels.firstOrNull { it.id == id }

    fun resolve(owner: ComputerControlDeskBlockEntity, raw: String): ChannelDescriptor {
        val snapshot = snapshot(owner)
        return resolve(snapshot, owner, raw)
            ?: throw LuaException("Unknown or unavailable channel '$raw'")
    }

    fun read(owner: ComputerControlDeskBlockEntity, raw: String): Int = resolve(owner, raw).value

    fun stat(owner: ComputerControlDeskBlockEntity, raw: String): Map<String, Any> {
        val snapshot = snapshot(owner)
        groupBinding(snapshot, owner, raw)?.let { (group, binding) ->
            val target = snapshot.channels.firstOrNull { it.id == binding.targetId }
            return linkedMapOf<String, Any>(
                "name" to binding.alias,
                "path" to "/groups/${group.name}/${binding.alias}",
                "nodeType" to "channel",
                "id" to binding.targetId,
                "available" to (target != null)
            ).also { info -> target?.let { info.putAll(describe(it)) } }
        }
        return describe(resolve(snapshot, owner, raw) ?: throw LuaException("Unknown channel '$raw'"))
    }

    fun ls(owner: ComputerControlDeskBlockEntity, rawPath: String): List<Map<String, Any>> {
        val snapshot = snapshot(owner)
        val path = normalizePath(rawPath)
        return when {
            path == "/" -> listOf(
                groupEntry("modules", "/modules", "system", false),
                groupEntry("wires", "/wires", "system", true),
                groupEntry("groups", "/groups", "user-root", true)
            )
            path == "/modules" -> snapshot.modules.map { module ->
                linkedMapOf(
                    "name" to module.label,
                    "path" to "/modules/${module.deskId}/${module.socket}",
                    "nodeType" to "group",
                    "groupType" to "module",
                    "mutable" to false,
                    "desk" to module.deskId,
                    "deskIndex" to module.deskIndex,
                    "socket" to module.socket,
                    "socketName" to module.socketName,
                    "module" to module.moduleId
                )
            }
            path.startsWith("/modules/") -> {
                val parts = path.split('/').filter(String::isNotBlank)
                if (parts.size != 3) throw LuaException("Module path must be /modules/<desk>/<socket>")
                val socket = parts[2].toIntOrNull() ?: throw LuaException("Invalid module socket '${parts[2]}'")
                val module = snapshot.modules.firstOrNull { it.deskId == parts[1] && it.socket == socket }
                    ?: throw LuaException("Unknown module path '$path'")
                module.channels.map { channelEntry(it, "$path/${it.name}") }
            }
            path == "/wires" -> snapshot.wires.map { channelEntry(it, "/wires/${it.name}") }
            path == "/groups" -> snapshot.groups.map { group ->
                linkedMapOf(
                    "name" to group.name,
                    "path" to "/groups/${group.name}",
                    "nodeType" to "group",
                    "groupType" to "user",
                    "id" to group.id.toString(),
                    "mutable" to true
                )
            }
            path.startsWith("/groups/") -> {
                val parts = path.split('/').filter(String::isNotBlank)
                if (parts.size != 2) throw LuaException("Group path must be /groups/<name>")
                val group = snapshot.groups.firstOrNull { it.name == parts[1] }
                    ?: throw LuaException("Unknown channel group '${parts[1]}'")
                group.bindings.map { binding ->
                    linkedMapOf<String, Any>(
                        "name" to binding.alias,
                        "path" to "$path/${binding.alias}",
                        "nodeType" to "channel",
                        "id" to binding.targetId,
                        "available" to binding.available,
                        "mutable" to true
                    ).also { entry ->
                        binding.kind?.let { entry["kind"] = it.name.lowercase() }
                        binding.value?.let { entry["value"] = it }
                    }
                }
            }
            else -> throw LuaException("Cannot list '$path'")
        }
    }

    fun setWire(owner: ComputerControlDeskBlockEntity, raw: String, value: Int) {
        val channel = resolve(owner, raw)
        if (channel.kind != ChannelKind.WIRE) throw LuaException("Channel '$raw' is not a wire channel")
        owner.wireBank.setValue(channel.wireName ?: throw LuaException("Wire channel is unavailable"), value)
    }

    fun pulseWire(owner: ComputerControlDeskBlockEntity, raw: String, ticks: Int, value: Int) {
        val channel = resolve(owner, raw)
        if (channel.kind != ChannelKind.WIRE) throw LuaException("Channel '$raw' is not a wire channel")
        owner.wireBank.pulse(channel.wireName ?: throw LuaException("Wire channel is unavailable"), ticks, value)
    }

    fun resetWire(owner: ComputerControlDeskBlockEntity, raw: String) {
        val channel = resolve(owner, raw)
        if (channel.kind != ChannelKind.WIRE) throw LuaException("Channel '$raw' is not a wire channel")
        owner.wireBank.reset(channel.wireName ?: throw LuaException("Wire channel is unavailable"))
    }

    fun override(owner: ComputerControlDeskBlockEntity, raw: String, value: Int): Map<String, Any> {
        requireSignal(value)
        val descriptor = resolve(owner, raw)
        val target = descriptor.controlTarget ?: throw LuaException("Channel '$raw' is not a control channel")
        return ControlOverrideManager.override(
            owner,
            ControlOverrideCommand(target.deskId, target.socket, target.nativeChannel, target.sign * value)
        )
    }

    fun overrideBatch(owner: ComputerControlDeskBlockEntity, commands: List<Pair<String, Int>>): Int {
        val resolved = commands.map { (raw, value) ->
            requireSignal(value)
            val descriptor = resolve(owner, raw)
            val target = descriptor.controlTarget ?: throw LuaException("Channel '$raw' is not a control channel")
            Triple(raw, target, value)
        }
        val duplicate = resolved.groupBy { (_, target, _) -> Triple(target.deskId, target.socket, target.nativeChannel) }
            .entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            throw LuaException("Override batch addresses both directions of native channel ${duplicate.key}")
        }
        return ControlOverrideManager.overrideBatch(
            owner,
            resolved.map { (_, target, value) ->
                ControlOverrideCommand(target.deskId, target.socket, target.nativeChannel, target.sign * value)
            }
        )
    }

    fun release(owner: ComputerControlDeskBlockEntity, raw: String): Boolean {
        val descriptor = resolve(owner, raw)
        val target = descriptor.controlTarget ?: throw LuaException("Channel '$raw' is not a control channel")
        return ControlOverrideManager.release(owner, target.deskId, target.socket, target.nativeChannel)
    }

    private fun resolve(snapshot: ChannelRegistrySnapshot, owner: ComputerControlDeskBlockEntity, raw: String): ChannelDescriptor? {
        val value = raw.trim()
        snapshot.channels.firstOrNull { it.id == value }?.let { return it }
        val path = normalizePath(value)
        if (path.startsWith("/wires/")) {
            val name = path.removePrefix("/wires/")
            return snapshot.wires.firstOrNull { it.name == name }
        }
        if (path.startsWith("/groups/")) {
            val binding = groupBinding(snapshot, owner, path)?.second ?: return null
            return snapshot.channels.firstOrNull { it.id == binding.targetId }
        }
        if (path.startsWith("/modules/")) {
            val parts = path.split('/').filter(String::isNotBlank)
            if (parts.size != 4) return null
            val socket = parts[2].toIntOrNull() ?: return null
            return snapshot.modules.firstOrNull { it.deskId == parts[1] && it.socket == socket }
                ?.channels?.firstOrNull { it.name == parts[3] }
        }
        return null
    }

    private fun groupBinding(
        snapshot: ChannelRegistrySnapshot,
        owner: ComputerControlDeskBlockEntity,
        raw: String
    ): Pair<ChannelGroupDefinition, ChannelGroupBinding>? {
        val path = normalizePath(raw)
        val parts = path.split('/').filter(String::isNotBlank)
        if (parts.size != 3 || parts[0] != "groups") return null
        val group = owner.channelGroups().definitions().firstOrNull { it.name == parts[1] } ?: return null
        val binding = group.bindings.firstOrNull { it.alias == parts[2] } ?: return null
        return group to binding
    }

    private fun describe(channel: ChannelDescriptor): Map<String, Any> = linkedMapOf<String, Any>(
        "id" to channel.id,
        "name" to channel.name,
        "kind" to channel.kind.name.lowercase(),
        "value" to channel.value,
        "available" to channel.available,
        "overridden" to channel.overridden,
        "connections" to channel.connections.size,
        "targets" to channel.connections.map { target ->
            linkedMapOf("x" to target.x, "y" to target.y, "z" to target.z, "side" to target.side)
        }
    ).also { info ->
        channel.controlTarget?.let { target ->
            info["desk"] = target.deskId
            info["socket"] = target.socket
            info["nativeChannel"] = target.nativeChannel
            info["sign"] = target.sign
        }
        channel.wireId?.let { info["wireId"] = it.toString() }
    }

    private fun channelEntry(channel: ChannelDescriptor, path: String): Map<String, Any> =
        linkedMapOf<String, Any>(
            "name" to channel.name,
            "path" to path,
            "nodeType" to "channel",
            "id" to channel.id,
            "kind" to channel.kind.name.lowercase(),
            "value" to channel.value,
            "available" to channel.available,
            "writable" to true,
            "overridden" to channel.overridden
        )

    private fun groupEntry(name: String, path: String, type: String, mutable: Boolean): Map<String, Any> = linkedMapOf(
        "name" to name,
        "path" to path,
        "nodeType" to "group",
        "groupType" to type,
        "mutable" to mutable
    )

    private fun normalizePath(raw: String): String {
        val trimmed = raw.trim().ifEmpty { "/" }
        val prefixed = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return if (prefixed.length > 1) prefixed.trimEnd('/') else prefixed
    }

    private fun requireSignal(value: Int) {
        if (value !in 0..15) throw LuaException("Channel signal must be between 0 and 15")
    }

    private data class MutableModule(
        val id: String,
        val label: String,
        val deskId: String,
        val deskIndex: Int,
        val socket: Int,
        val socketName: String,
        val moduleId: String,
        val channels: MutableList<ChannelDescriptor> = arrayListOf()
    ) {
        fun freeze() = ChannelModuleDescriptor(id, label, deskId, deskIndex, socket, socketName, moduleId, channels.toList())
    }
}
