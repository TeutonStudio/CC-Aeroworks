package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupDefinition
import de.teutonstudio.ccaeroworks.computer.channel.ComputerChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.channelGroups
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideCommand
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager

/**
 * High-level logical I/O API. Existing `controls` and `wires` APIs remain the low-level contracts;
 * this API adds stable discovery paths and user-defined aliases across both channel kinds.
 */
class ComputerChannelsLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("channels")

    override fun getModuleName(): String = "cc_aeroworks.channels"

    @LuaFunction(mainThread = true)
    fun ls(arguments: IArguments): List<Map<String, Any>> = lua {
        val path = if (arguments.count() > 0) arguments.getString(0) else "/"
        ComputerChannelRegistry.ls(owner(), path)
    }

    @LuaFunction(mainThread = true)
    fun stat(reference: String): Map<String, Any>? = lua {
        ComputerChannelRegistry.stat(owner(), reference)
    }

    @LuaFunction(mainThread = true)
    fun read(reference: String): Int = lua {
        ComputerChannelRegistry.read(owner(), reference)
    }

    @LuaFunction(mainThread = true)
    fun setWire(reference: String, value: Int): Int = lua {
        val owner = owner()
        val channel = requireKind(owner, reference, "wire")
        val name = channel["wireName"]?.toString()
            ?: throw LuaException("Wire channel '$reference' has no physical wire name")
        owner.wireBank.setValue(name, value)
        owner.wireBank.value(name)
    }

    @LuaFunction(mainThread = true)
    fun pulseWire(arguments: IArguments): Int = lua {
        val reference = arguments.getString(0)
        val ticks = if (arguments.count() > 1) arguments.getInt(1) else 2
        val value = if (arguments.count() > 2) arguments.getInt(2) else 15
        val owner = owner()
        val channel = requireKind(owner, reference, "wire")
        val name = channel["wireName"]?.toString()
            ?: throw LuaException("Wire channel '$reference' has no physical wire name")
        owner.wireBank.pulse(name, ticks, value)
        owner.wireBank.value(name)
    }

    @LuaFunction(mainThread = true)
    fun resetWire(reference: String): Int = lua {
        val owner = owner()
        val channel = requireKind(owner, reference, "wire")
        val name = channel["wireName"]?.toString()
            ?: throw LuaException("Wire channel '$reference' has no physical wire name")
        owner.wireBank.reset(name)
        owner.wireBank.value(name)
    }

    @LuaFunction(mainThread = true)
    fun `override`(reference: String, value: Int): Map<String, Any> = lua {
        val owner = owner()
        val channel = requireKind(owner, reference, "control")
        ControlOverrideManager.override(owner, controlCommand(channel, value))
    }

    @LuaFunction(mainThread = true)
    fun overrideBatch(arguments: IArguments): Int = lua {
        val owner = owner()
        val table = arguments.getTable(0)
        val commands = indexedArray(table, "overrideBatch").map { (index, entry) ->
            val reference = entry["channel"] as? String
                ?: throw LuaException("overrideBatch entry $index is missing string field 'channel'")
            val value = integerField(entry, "value", "overrideBatch entry $index")
            val channel = requireKind(owner, reference, "control")
            controlCommand(channel, value)
        }
        ControlOverrideManager.overrideBatch(owner, commands)
    }

    @LuaFunction(mainThread = true)
    fun release(reference: String): Boolean = lua {
        val owner = owner()
        val channel = requireKind(owner, reference, "control")
        ControlOverrideManager.release(
            owner,
            channel["desk"].toString(),
            channel["socket"] ?: throw LuaException("Control channel '$reference' has no socket"),
            channel["channel"].toString()
        )
    }

    @LuaFunction(mainThread = true)
    fun releaseAll(): Int = lua { ControlOverrideManager.releaseAll(owner()) }

    @LuaFunction(mainThread = true)
    fun createGroup(name: String): Map<String, Any> = lua {
        describe(owner().channelGroups.create(name))
    }

    @LuaFunction(mainThread = true)
    fun renameGroup(group: String, newName: String): Map<String, Any> = lua {
        describe(owner().channelGroups.rename(group, newName))
    }

    @LuaFunction(mainThread = true)
    fun removeGroup(group: String): Map<String, Any> = lua {
        describe(owner().channelGroups.remove(group))
    }

    @LuaFunction(mainThread = true)
    fun bind(group: String, name: String, target: String): Map<String, Any> = lua {
        val owner = owner()
        val targetInfo = ComputerChannelRegistry.resolveChannel(owner, target)
        val targetId = targetInfo["id"]?.toString()
            ?: throw LuaException("Channel '$target' has no stable id")
        describe(owner.channelGroups.bind(group, name, targetId))
    }

    @LuaFunction(mainThread = true)
    fun unbind(group: String, name: String): Boolean = lua {
        owner().channelGroups.unbind(group, name)
    }

    private fun requireKind(
        owner: ComputerControlDeskBlockEntity,
        reference: String,
        expected: String
    ): Map<String, Any> {
        val channel = ComputerChannelRegistry.resolveChannel(owner, reference)
        val actual = channel["channelKind"]?.toString()
        if (actual != expected) {
            throw LuaException("Channel '$reference' is '$actual', expected '$expected'")
        }
        return channel
    }

    private fun controlCommand(channel: Map<String, Any>, value: Int): ControlOverrideCommand =
        ControlOverrideCommand(
            channel["desk"]?.toString() ?: throw LuaException("Control channel has no desk id"),
            channel["socket"] ?: throw LuaException("Control channel has no socket"),
            channel["channel"]?.toString() ?: throw LuaException("Control channel has no channel name"),
            value
        )

    private fun describe(group: ChannelGroupDefinition): Map<String, Any> = linkedMapOf(
        "id" to group.id.toString(),
        "name" to group.name,
        "path" to "/groups/${group.name}",
        "size" to group.bindings.size,
        "bindings" to group.bindings.map { binding ->
            linkedMapOf(
                "name" to binding.name,
                "path" to "/groups/${group.name}/${binding.name}",
                "targetId" to binding.targetId
            )
        }
    )

    private fun indexedArray(table: Map<*, *>, operation: String): List<Pair<Int, Map<*, *>>> {
        if (table.isEmpty()) return emptyList()
        val indexed = table.entries.map { (rawIndex, rawValue) ->
            val number = rawIndex as? Number
                ?: throw LuaException("$operation must be an array of tables")
            val numeric = number.toDouble()
            if (!numeric.isFinite() || numeric % 1.0 != 0.0 || numeric < 1.0) {
                throw LuaException("$operation indexes must be positive integers")
            }
            val entry = rawValue as? Map<*, *>
                ?: throw LuaException("$operation entry ${numeric.toInt()} must be a table")
            numeric.toInt() to entry
        }.sortedBy { it.first }
        indexed.forEachIndexed { offset, (index, _) ->
            if (index != offset + 1) throw LuaException("$operation must use consecutive indexes starting at 1")
        }
        return indexed
    }

    private fun integerField(table: Map<*, *>, name: String, context: String): Int {
        val number = table[name] as? Number
            ?: throw LuaException("$context is missing numeric field '$name'")
        val value = number.toDouble()
        if (!value.isFinite() || value % 1.0 != 0.0 || value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
            throw LuaException("$context field '$name' must be an integer")
        }
        return value.toInt()
    }

    private fun owner(): ComputerControlDeskBlockEntity = access.owner()
        ?: throw LuaException("The ComputerControlDesk is no longer loaded")

    private inline fun <T> lua(block: () -> T): T = try {
        block()
    } catch (exception: LuaException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw LuaException(exception.message ?: "Channel operation failed")
    }
}
