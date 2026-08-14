package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideCommand
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager

/**
 * Embedded-computer-only API for taking explicit authority over continuous Aeroworks controls.
 *
 * This deliberately does not live on the public ControlDesk peripheral. A normal external
 * CC:Tweaked computer can observe desks, while only the ComputerControlDesk embedded computer can
 * own control channels in its active multiblock.
 */
class ComputerControlLuaApi(
    private val access: ComputerConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("controls")

    override fun getModuleName(): String = "cc_aeroworks.controls"

    @LuaFunction(mainThread = true)
    fun getChannels(): List<Map<String, Any>> =
        ControlOverrideManager.listChannels(owner())

    @LuaFunction(mainThread = true)
    fun getState(arguments: IArguments): Map<String, Any> =
        ControlOverrideManager.getState(
            owner(),
            arguments.getString(0),
            arguments.get(1),
            arguments.getString(2)
        )

    @LuaFunction(mainThread = true)
    fun `override`(arguments: IArguments): Map<String, Any> =
        ControlOverrideManager.override(
            owner(),
            ControlOverrideCommand(
                arguments.getString(0),
                arguments.get(1),
                arguments.getString(2),
                arguments.getInt(3)
            )
        )

    @LuaFunction(mainThread = true)
    fun overrideBatch(arguments: IArguments): Int {
        val commands = parseCommands(arguments.getTable(0))
        return ControlOverrideManager.overrideBatch(owner(), commands)
    }

    @LuaFunction(mainThread = true)
    fun release(arguments: IArguments): Boolean =
        ControlOverrideManager.release(
            owner(),
            arguments.getString(0),
            arguments.get(1),
            arguments.getString(2)
        )

    @LuaFunction(mainThread = true)
    fun releaseAll(): Int = ControlOverrideManager.releaseAll(owner())

    private fun parseCommands(table: Map<*, *>): List<ControlOverrideCommand> {
        if (table.isEmpty()) return emptyList()
        val indexed = table.entries.map { (rawIndex, rawValue) ->
            val number = rawIndex as? Number
                ?: throw LuaException("overrideBatch must be an array of command tables")
            val index = number.toDouble()
            if (!index.isFinite() || index % 1.0 != 0.0 || index < 1.0) {
                throw LuaException("overrideBatch command indexes must be positive integers")
            }
            val command = rawValue as? Map<*, *>
                ?: throw LuaException("overrideBatch entry ${index.toInt()} must be a table")
            index.toInt() to command
        }.sortedBy { it.first }

        indexed.forEachIndexed { offset, (index, _) ->
            if (index != offset + 1) {
                throw LuaException("overrideBatch must use consecutive indexes starting at 1")
            }
        }

        return indexed.map { (index, command) ->
            val desk = command["desk"] as? String
                ?: throw LuaException("overrideBatch entry $index is missing string field 'desk'")
            val socket = command["socket"]
                ?: throw LuaException("overrideBatch entry $index is missing field 'socket'")
            if (socket !is String && socket !is Number) {
                throw LuaException("overrideBatch entry $index field 'socket' must be a name or index")
            }
            val channel = command["channel"] as? String
                ?: throw LuaException("overrideBatch entry $index is missing string field 'channel'")
            val valueNumber = command["value"] as? Number
                ?: throw LuaException("overrideBatch entry $index is missing numeric field 'value'")
            val valueDouble = valueNumber.toDouble()
            if (!valueDouble.isFinite() || valueDouble % 1.0 != 0.0 ||
                valueDouble < Int.MIN_VALUE || valueDouble > Int.MAX_VALUE
            ) {
                throw LuaException("overrideBatch entry $index field 'value' must be an integer")
            }
            ControlOverrideCommand(desk, socket, channel, valueDouble.toInt())
        }
    }

    private fun owner(): ComputerControlDeskBlockEntity = access.owner()
        ?: throw LuaException("The ComputerControlDesk is no longer loaded")
}
