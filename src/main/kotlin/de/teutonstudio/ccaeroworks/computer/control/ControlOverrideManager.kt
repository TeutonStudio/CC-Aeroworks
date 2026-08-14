package de.teutonstudio.ccaeroworks.computer.control

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSnapshot
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.server.level.ServerLevel
import java.util.UUID
import java.util.WeakHashMap

enum class ControlOverrideMode {
    HARD
}

data class ControlOverrideKey(
    val deskId: UUID,
    val socket: Int,
    val channel: String
)

data class ControlOverrideState(
    val key: ControlOverrideKey,
    val ownerDeskId: UUID,
    var commandedValue: Int,
    val mode: ControlOverrideMode,
    val engagedTick: Long,
    var updatedTick: Long
)

data class ControlOverrideCommand(
    val deskId: String,
    val socket: Any?,
    val channel: String,
    val value: Int
)

/**
 * Server-side control-authority layer for ComputerControlDesk scripts.
 *
 * HARD overrides own an Aeroworks control channel until explicitly released or until the owning
 * embedded computer/network becomes invalid. Normal controller writes are rejected by the
 * ConsoleBlockEntityControlOverrideMixin while the owner writes through the original Aeroworks
 * setter under ControlWriteContext, keeping the visible module state and vehicle control value in
 * one canonical place.
 */
object ControlOverrideManager {
    private data class ResolvedCommand(
        val member: ConsoleMember,
        val socket: Int,
        val channel: String,
        val value: Int,
        val key: ControlOverrideKey
    )

    private val states = WeakHashMap<ServerLevel, MutableMap<ControlOverrideKey, ControlOverrideState>>()

    @Synchronized
    @JvmStatic
    fun isHardOverridden(desk: ConsoleBlockEntity, socket: Int, channel: String): Boolean {
        val level = desk.level as? ServerLevel ?: return false
        val deskId = (desk as DeskIdentityAccess).ccaeroworks_getDeskId()
        return states[level]?.get(ControlOverrideKey(deskId, socket, channel))?.mode == ControlOverrideMode.HARD
    }

    @Synchronized
    fun listChannels(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> {
        val (level, snapshot) = activeNetwork(owner)
        val levelStates = states[level].orEmpty()
        val result = arrayListOf<Map<String, Any>>()

        snapshot.members.forEach { member ->
            val desk = member.desk
            for (socket in 0 until desk.socketCount()) {
                val module = desk.module(socket) ?: continue
                if (CombinedInputSource.isDisplayPointerModule(module)) continue
                val channels = CombinedInputSource.channels(module)
                if (channels.isEmpty()) continue
                val moduleId = AeroworksModuleAccess.id(module).toString()
                channels.forEach { channel ->
                    if (module.channels().none { it.id() == channel }) return@forEach
                    val key = ControlOverrideKey(UUID.fromString(member.id), socket, channel)
                    val state = levelStates[key]
                    result += linkedMapOf<String, Any>(
                        "desk" to member.id,
                        "deskIndex" to member.index,
                        "socket" to socket,
                        "socketName" to DeskSockets.name(socket),
                        "module" to moduleId,
                        "channel" to channel,
                        "value" to module.value(channel),
                        "overridden" to (state != null)
                    ).also { info ->
                        state?.let {
                            info["commanded"] = it.commandedValue
                            info["owner"] = it.ownerDeskId.toString()
                            info["mode"] = it.mode.name.lowercase()
                        }
                    }
                }
            }
        }
        return result
    }

    @Synchronized
    fun getState(
        owner: ComputerControlDeskBlockEntity,
        deskId: String,
        rawSocket: Any?,
        channel: String
    ): Map<String, Any> {
        val (level, snapshot) = activeNetwork(owner)
        val resolved = resolveCommand(snapshot, ControlOverrideCommand(deskId, rawSocket, channel, 0), validateValue = false)
        val module = resolved.member.desk.module(resolved.socket)
            ?: throw LuaException("Socket ${resolved.socket} is empty")
        val state = states[level]?.get(resolved.key)
        return linkedMapOf<String, Any>(
            "desk" to resolved.member.id,
            "deskIndex" to resolved.member.index,
            "socket" to resolved.socket,
            "socketName" to DeskSockets.name(resolved.socket),
            "module" to AeroworksModuleAccess.id(module).toString(),
            "channel" to resolved.channel,
            "value" to module.value(resolved.channel),
            "overridden" to (state != null)
        ).also { info ->
            state?.let {
                info["commanded"] = it.commandedValue
                info["owner"] = it.ownerDeskId.toString()
                info["mode"] = it.mode.name.lowercase()
                info["engagedTick"] = it.engagedTick
                info["updatedTick"] = it.updatedTick
            }
        }
    }

    @Synchronized
    fun override(
        owner: ComputerControlDeskBlockEntity,
        command: ControlOverrideCommand
    ): Map<String, Any> {
        overrideBatch(owner, listOf(command))
        return getState(owner, command.deskId, command.socket, command.channel)
    }

    @Synchronized
    fun overrideBatch(
        owner: ComputerControlDeskBlockEntity,
        commands: List<ControlOverrideCommand>
    ): Int {
        if (commands.isEmpty()) return 0
        val (level, snapshot) = activeNetwork(owner)
        val resolved = commands.map { resolveCommand(snapshot, it, validateValue = true) }
        val duplicate = resolved.groupingBy { it.key }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicate != null) {
            throw LuaException("Override batch contains duplicate channel ${describe(duplicate.key)}")
        }

        val levelStates = states.getOrPut(level) { linkedMapOf() }
        resolved.forEach { command ->
            val existing = levelStates[command.key]
            if (existing != null && existing.ownerDeskId != owner.deskId) {
                throw LuaException("Channel ${describe(command.key)} is already controlled by another ComputerControlDesk")
            }
        }

        var changed = 0
        val tick = level.gameTime
        resolved.forEach { command ->
            val existing = levelStates[command.key]
            val action = if (existing == null) "engaged" else "updated"
            val state = if (existing == null) {
                ControlOverrideState(
                    command.key,
                    owner.deskId,
                    command.value,
                    ControlOverrideMode.HARD,
                    tick,
                    tick
                ).also { levelStates[command.key] = it }
            } else {
                existing
            }

            val valueChanged = state.commandedValue != command.value
            if (valueChanged) {
                state.commandedValue = command.value
                state.updatedTick = tick
            }
            val module = command.member.desk.module(command.socket)
                ?: throw LuaException("Socket ${command.socket} became empty during override")
            val effectiveChanged = module.value(command.channel) != command.value
            if (effectiveChanged) {
                ControlWriteContext.computerOverride {
                    command.member.desk.setChannelFromController(command.socket, command.channel, command.value)
                }
            }
            if (existing == null || valueChanged || effectiveChanged) {
                changed++
                queueOverrideEvent(owner, action, command, state)
            }
        }
        if (levelStates.isEmpty()) states.remove(level)
        return changed
    }

    @Synchronized
    fun release(
        owner: ComputerControlDeskBlockEntity,
        deskId: String,
        rawSocket: Any?,
        channel: String,
        reason: String = "released"
    ): Boolean {
        val (level, snapshot) = activeNetwork(owner)
        val resolved = resolveCommand(snapshot, ControlOverrideCommand(deskId, rawSocket, channel, 0), validateValue = false)
        val levelStates = states[level] ?: return false
        val state = levelStates[resolved.key] ?: return false
        if (state.ownerDeskId != owner.deskId) {
            throw LuaException("Channel ${describe(resolved.key)} is controlled by another ComputerControlDesk")
        }
        levelStates.remove(resolved.key)
        if (levelStates.isEmpty()) states.remove(level)
        queueReleaseEvent(owner, resolved.key, reason)
        return true
    }

    @Synchronized
    fun releaseAll(owner: ComputerControlDeskBlockEntity, reason: String = "released"): Int {
        val ownerId = owner.deskId
        var removed = 0
        val targetLevel = owner.level as? ServerLevel
        val candidateLevels = if (targetLevel != null) listOf(targetLevel) else states.keys.toList()
        candidateLevels.forEach { level ->
            val levelStates = states[level] ?: return@forEach
            val keys = levelStates.values
                .filter { it.ownerDeskId == ownerId }
                .map { it.key }
            keys.forEach { key ->
                levelStates.remove(key)
                removed++
                queueReleaseEvent(owner, key, reason)
            }
            if (levelStates.isEmpty()) states.remove(level)
        }
        return removed
    }

    @Synchronized
    fun tick(owner: ComputerControlDeskBlockEntity, powered: Boolean) {
        val level = owner.level as? ServerLevel ?: return
        val levelStates = states[level] ?: return
        val ownerStates = levelStates.values.filter { it.ownerDeskId == owner.deskId }
        if (ownerStates.isEmpty()) return
        if (!powered) {
            releaseAll(owner, "computer_off")
            return
        }

        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== owner) {
            releaseAll(owner, "network_invalid")
            return
        }

        val members = snapshot.members.associateBy { UUID.fromString(it.id) }
        val invalid = arrayListOf<ControlOverrideKey>()
        ownerStates.forEach { state ->
            val member = members[state.key.deskId]
            if (member == null || !validControl(member.desk, state.key.socket, state.key.channel)) {
                invalid += state.key
                return@forEach
            }
            val module = member.desk.module(state.key.socket) ?: run {
                invalid += state.key
                return@forEach
            }
            if (module.value(state.key.channel) != state.commandedValue) {
                ControlWriteContext.computerOverride {
                    member.desk.setChannelFromController(
                        state.key.socket,
                        state.key.channel,
                        state.commandedValue
                    )
                }
            }
        }
        invalid.forEach { key ->
            levelStates.remove(key)
            queueReleaseEvent(owner, key, "target_invalid")
        }
        if (levelStates.isEmpty()) states.remove(level)
    }

    private fun activeNetwork(owner: ComputerControlDeskBlockEntity): Pair<ServerLevel, ConsoleMultiblockSnapshot> {
        val level = owner.level as? ServerLevel
            ?: throw LuaException("The ComputerControlDesk is not in a server level")
        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== owner) {
            throw LuaException("The ComputerControlDesk must own an active control network")
        }
        return level to snapshot
    }

    private fun resolveCommand(
        snapshot: ConsoleMultiblockSnapshot,
        command: ControlOverrideCommand,
        validateValue: Boolean
    ): ResolvedCommand {
        if (validateValue && command.value !in -15..15) {
            throw LuaException("Control value must be between -15 and 15")
        }
        val member = snapshot.members.firstOrNull { it.id == command.deskId }
            ?: throw LuaException("Unknown desk '${command.deskId}' in this control network")
        val socket = AeroworksDeskService.parseSocket(member.desk, command.socket)
        validateControl(member.desk, socket, command.channel)
        return ResolvedCommand(
            member,
            socket,
            command.channel,
            command.value,
            ControlOverrideKey(UUID.fromString(member.id), socket, command.channel)
        )
    }

    private fun validateControl(desk: ConsoleBlockEntity, socket: Int, channel: String) {
        val module = desk.module(socket) ?: throw LuaException("Socket $socket is empty")
        if (CombinedInputSource.isDisplayPointerModule(module)) {
            throw LuaException("Display pointer channels cannot be overridden as vehicle controls")
        }
        val supported = CombinedInputSource.channels(module)
        if (channel !in supported || module.channels().none { it.id() == channel }) {
            throw LuaException("Channel '$channel' is not a supported continuous control channel")
        }
    }

    private fun validControl(desk: ConsoleBlockEntity, socket: Int, channel: String): Boolean {
        if (socket !in 0 until desk.socketCount()) return false
        val module = desk.module(socket) ?: return false
        if (CombinedInputSource.isDisplayPointerModule(module)) return false
        return channel in CombinedInputSource.channels(module) && module.channels().any { it.id() == channel }
    }

    private fun queueOverrideEvent(
        owner: ComputerControlDeskBlockEntity,
        action: String,
        command: ResolvedCommand,
        state: ControlOverrideState
    ) {
        owner.getServerComputer()?.queueEvent(
            CCAeroworks.CONTROL_OVERRIDE_EVENT,
            arrayOf(
                action,
                command.member.id,
                command.member.index,
                command.socket,
                DeskSockets.name(command.socket),
                command.channel,
                state.commandedValue,
                state.mode.name.lowercase()
            )
        )
    }

    private fun queueReleaseEvent(
        owner: ComputerControlDeskBlockEntity,
        key: ControlOverrideKey,
        reason: String
    ) {
        owner.getServerComputer()?.queueEvent(
            CCAeroworks.CONTROL_RELEASE_EVENT,
            arrayOf(key.deskId.toString(), key.socket, DeskSockets.name(key.socket), key.channel, reason)
        )
    }

    private fun describe(key: ControlOverrideKey): String =
        "${key.deskId}/${key.socket}/${key.channel}"
}
