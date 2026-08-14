package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.computer.io.DeskIoInventory

/** Read-only inspection API for the same model used by the ControlDesk I/O overview. */
class ComputerDeskIoLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("deskio")

    override fun getModuleName(): String = "cc_aeroworks.deskio"

    @LuaFunction(mainThread = true)
    fun list(): List<Map<String, Any>> = DeskIoInventory.list(owner())

    @LuaFunction(mainThread = true)
    fun getSnapshot(): Map<String, Any> = DeskIoInventory.snapshot(owner())

    @LuaFunction(mainThread = true)
    fun find(category: String): List<Map<String, Any>> {
        val normalized = category.trim().lowercase()
        return DeskIoInventory.list(owner()).filter { it["category"] == normalized }
    }

    private fun owner(): ComputerControlDeskBlockEntity = access.owner()
        ?: throw LuaException("The ComputerControlDesk is no longer loaded")
}
