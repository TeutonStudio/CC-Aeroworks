package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelDefinition

class ComputerWireLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("wires")

    override fun getModuleName(): String = "cc_aeroworks.wires"

    @LuaFunction(mainThread = true)
    fun list(): Map<String, Any> = owner().wireBank.describeChannels()

    @LuaFunction(mainThread = true)
    fun exists(name: String): Boolean = owner().wireBank.exists(name)

    @LuaFunction(mainThread = true)
    fun get(name: String): Int = lua { owner().wireBank.value(name) }

    @LuaFunction(mainThread = true)
    fun set(name: String, value: Int) {
        lua { owner().wireBank.setValue(name, value) }
    }

    @LuaFunction(mainThread = true)
    fun pulse(arguments: IArguments) {
        val name = arguments.getString(0)
        val duration = if (arguments.count() > 1) arguments.getInt(1) else 2
        val value = if (arguments.count() > 2) arguments.getInt(2) else 15
        lua { owner().wireBank.pulse(name, duration, value) }
    }

    @LuaFunction(mainThread = true)
    fun reset(name: String) {
        lua { owner().wireBank.reset(name) }
    }

    @LuaFunction(mainThread = true)
    fun resetAll() {
        owner().wireBank.resetAll()
    }

    @LuaFunction(mainThread = true)
    fun getInfo(name: String): Map<String, Any> = lua {
        owner().wireBank.describeChannel(name)
    }

    @LuaFunction(mainThread = true)
    fun getBackend(): String = owner().wireBank.backendName()

    @LuaFunction(mainThread = true)
    fun isEnabled(): Boolean = owner().wireBank.isOutputEnabled()

    private fun owner(): ComputerControlDeskBlockEntity = access.owner()
        ?: throw LuaException("The ComputerControlDesk is no longer loaded")

    private inline fun <T> lua(block: () -> T): T = try {
        block()
    } catch (exception: LuaException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw LuaException(exception.message ?: "Wire operation failed")
    }
}

/**
 * Private command bridge used by the bundled `wires` CraftOS program.
 *
 * Configuration mutations intentionally do not exist on the public `wires` API. This global is
 * an implementation detail of the ROM command, not part of the supported programming API.
 */
class ComputerWireAdminLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("__cc_aeroworks_wire_admin")

    override fun getModuleName(): String = "cc_aeroworks.wire_admin"

    @LuaFunction(mainThread = true)
    fun add(name: String): Map<String, Any> = lua {
        describe(owner().wireBank.addChannel(name))
    }

    @LuaFunction(mainThread = true)
    fun remove(name: String): Map<String, Any> = lua {
        describe(owner().wireBank.removeChannel(name))
    }

    @LuaFunction(mainThread = true)
    fun rename(oldName: String, newName: String): Map<String, Any> = lua {
        describe(owner().wireBank.renameChannel(oldName, newName))
    }

    private fun describe(definition: WireChannelDefinition): Map<String, Any> = linkedMapOf(
        "id" to definition.id.toString(),
        "name" to definition.name
    )

    private fun owner(): ComputerControlDeskBlockEntity = access.owner()
        ?: throw LuaException("The ComputerControlDesk is no longer loaded")

    private inline fun <T> lua(block: () -> T): T = try {
        block()
    } catch (exception: LuaException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw LuaException(exception.message ?: "Wire configuration failed")
    }
}
