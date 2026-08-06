package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaFunction

class ComputerConsoleLuaApi(
    access: ComputerConsoleAccess,
    system: IComputerSystem
) : ILuaAPI {
    private val runtime = PeripheralNetworkRuntime(access, system)

    override fun getNames(): Array<String> = arrayOf("peripherals")

    override fun getModuleName(): String = "cc_aeroworks.peripherals"

    override fun shutdown() {
        runtime.close()
    }

    @LuaFunction(mainThread = true)
    fun find(type: String): Any? = runtime.find(type)

    @LuaFunction(mainThread = true)
    fun findAll(type: String): Map<String, Any> =
        runtime.find(type, alwaysCollection = true) as Map<String, Any>

    @LuaFunction(mainThread = true)
    fun wrap(arguments: IArguments): Any? = runtime.wrap(arguments)

    @LuaFunction(mainThread = true)
    fun getDesks(): Map<String, Any> =
        runtime.find("ControlDesk", alwaysCollection = true) as Map<String, Any>

    @LuaFunction(mainThread = true)
    fun getTypes(): Map<String, Int> = runtime.typeCounts()

    @LuaFunction(mainThread = true)
    fun getNetwork(): Map<String, Any> = runtime.describeNetwork()

    @LuaFunction(mainThread = true)
    fun refresh(): Map<String, Any> {
        runtime.graph(force = true)
        return runtime.describeNetwork()
    }
}
