package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaFunction

class ComputerConsoleLuaApi(
    private val access: ComputerConsoleAccess,
    private val system: IComputerSystem
) : ILuaAPI {
    private var runtime: PeripheralNetworkRuntime? = null

    override fun getNames(): Array<String> = arrayOf("peripherals")

    override fun getModuleName(): String = "cc_aeroworks.peripherals"

    override fun startup() {
        check(runtime == null) { "Computer console API is already running" }
        runtime = PeripheralNetworkRuntime(access, system)
    }

    override fun shutdown() {
        runtime?.close()
        runtime = null
    }

    private fun activeRuntime(): PeripheralNetworkRuntime =
        checkNotNull(runtime) { "Computer console API is not running" }

    @LuaFunction(mainThread = true)
    fun find(type: String): Any? = activeRuntime().find(type)

    @LuaFunction(mainThread = true)
    fun findAll(type: String): Map<String, Any> =
        activeRuntime().find(type, alwaysCollection = true) as Map<String, Any>

    @LuaFunction(mainThread = true)
    fun wrap(arguments: IArguments): Any? = activeRuntime().wrap(arguments)

    @LuaFunction(mainThread = true)
    fun getDesks(): Map<String, Any> =
        activeRuntime().find("ControlDesk", alwaysCollection = true) as Map<String, Any>

    @LuaFunction(mainThread = true)
    fun getTree(): Map<String, Any> = activeRuntime().describeTree()

    @LuaFunction(mainThread = true)
    fun getTypes(): Map<String, Int> = activeRuntime().typeCounts()

    @LuaFunction(mainThread = true)
    fun getNetwork(): Map<String, Any> = activeRuntime().describeNetwork()

    @LuaFunction(mainThread = true)
    fun refresh(): Map<String, Any> {
        val current = activeRuntime()
        current.graph(force = true)
        return current.describeNetwork()
    }
}
