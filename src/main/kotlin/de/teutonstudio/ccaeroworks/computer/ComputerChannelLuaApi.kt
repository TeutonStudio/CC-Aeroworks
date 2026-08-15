package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.channelGroups
import java.util.UUID

/** High-level logical channel API layered over the existing controls and wires runtimes. */
class ComputerChannelLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("channels")
    override fun getModuleName(): String = "cc_aeroworks.channels"

    @LuaFunction(mainThread = true)
    fun ls(arguments: IArguments): List<Map<String, Any>> = lua {
        ChannelRegistry.ls(owner(), if (arguments.count() > 0) arguments.getString(0) else "/")
    }

    @LuaFunction(mainThread = true)
    fun stat(pathOrId: String): Map<String, Any> = lua { ChannelRegistry.stat(owner(), pathOrId) }

    @LuaFunction(mainThread = true)
    fun read(pathOrId: String): Int = lua { ChannelRegistry.read(owner(), pathOrId) }

    @LuaFunction(mainThread = true)
    fun setWire(pathOrId: String, value: Int) = lua { ChannelRegistry.setWire(owner(), pathOrId, value) }

    @LuaFunction(mainThread = true)
    fun pulseWire(arguments: IArguments) = lua {
        val channel = arguments.getString(0)
        val ticks = if (arguments.count() > 1) arguments.getInt(1) else 2
        val value = if (arguments.count() > 2) arguments.getInt(2) else 15
        ChannelRegistry.pulseWire(owner(), channel, ticks, value)
    }

    @LuaFunction(mainThread = true)
    fun resetWire(pathOrId: String) = lua { ChannelRegistry.resetWire(owner(), pathOrId) }

    @LuaFunction(mainThread = true)
    fun `override`(pathOrId: String, value: Int): Map<String, Any> = lua {
        ChannelRegistry.override(owner(), pathOrId, value)
    }

    @LuaFunction(mainThread = true)
    fun overrideBatch(arguments: IArguments): Int = lua {
        val table = arguments.getTable(0)
        val indexed = table.entries.map { (rawIndex, rawCommand) ->
            val index = (rawIndex as? Number)?.toInt()
                ?: throw LuaException("overrideBatch must be an array")
            val command = rawCommand as? Map<*, *>
                ?: throw LuaException("overrideBatch entry $index must be a table")
            val channel = command["channel"] as? String
                ?: throw LuaException("overrideBatch entry $index is missing 'channel'")
            val value = (command["value"] as? Number)?.toInt()
                ?: throw LuaException("overrideBatch entry $index is missing integer 'value'")
            index to (channel to value)
        }.sortedBy { it.first }
        indexed.forEachIndexed { offset, (index, _) ->
            if (index != offset + 1) throw LuaException("overrideBatch indexes must start at 1 and be consecutive")
        }
        ChannelRegistry.overrideBatch(owner(), indexed.map { it.second })
    }

    @LuaFunction(mainThread = true)
    fun release(pathOrId: String): Boolean = lua { ChannelRegistry.release(owner(), pathOrId) }

    @LuaFunction(mainThread = true)
    fun releaseAll(): Int = de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager.releaseAll(owner())

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

/** Private configuration bridge for the bundled channels CraftOS command and GUI. */
class ComputerChannelAdminLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("__cc_aeroworks_channel_admin")
    override fun getModuleName(): String = "cc_aeroworks.channel_admin"

    @LuaFunction(mainThread = true)
    fun addGroup(name: String): Map<String, Any> = describe(owner().channelGroups().addGroup(name))

    @LuaFunction(mainThread = true)
    fun removeGroup(name: String): Map<String, Any> {
        val bank = owner().channelGroups()
        return describe(bank.removeGroup(bank.group(name).id))
    }

    @LuaFunction(mainThread = true)
    fun renameGroup(oldName: String, newName: String): Map<String, Any> {
        val bank = owner().channelGroups()
        return describe(bank.renameGroup(bank.group(oldName).id, newName))
    }

    @LuaFunction(mainThread = true)
    fun bind(groupName: String, alias: String, targetId: String): Map<String, Any> {
        val owner = owner()
        if (ChannelRegistry.findById(owner, targetId) == null) throw LuaException("Unknown channel id '$targetId'")
        val bank = owner.channelGroups()
        return describe(bank.bind(bank.group(groupName).id, alias, targetId))
    }

    @LuaFunction(mainThread = true)
    fun unbind(groupName: String, alias: String): Map<String, Any> {
        val bank = owner().channelGroups()
        return describe(bank.unbind(bank.group(groupName).id, alias))
    }

    private fun describe(group: de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupDefinition): Map<String, Any> = linkedMapOf(
        "id" to group.id.toString(),
        "name" to group.name,
        "bindings" to group.bindings.map { binding ->
            linkedMapOf("name" to binding.alias, "target" to binding.targetId)
        }
    )

    private fun owner(): ComputerControlDeskBlockEntity = access.owner()
        ?: throw LuaException("The ComputerControlDesk is no longer loaded")
}
