package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState

class ComputerConsoleLuaApi(private val access: ComputerConsoleAccess) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("aeroworks")

    override fun getModuleName(): String = "cc_aeroworks.aeroworks"

    @LuaFunction(mainThread = true)
    fun getDesks(): List<Map<String, Any>> = snapshot().members.map(::describeDesk)

    @LuaFunction(mainThread = true)
    fun getDesk(arguments: IArguments): Map<String, Any> =
        describeDesk(member(arguments.get(0)))

    @LuaFunction(mainThread = true)
    fun getModules(arguments: IArguments): List<Map<String, Any>> =
        AeroworksDeskService.getModules(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getModule(arguments: IArguments): Map<String, Any>? =
        AeroworksDeskService.getModule(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getInput(arguments: IArguments): Any =
        AeroworksDeskService.getInput(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getInputs(arguments: IArguments): Map<Int, Any> =
        AeroworksDeskService.getInputs(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDisplays(arguments: IArguments): List<Map<String, Any>> =
        AeroworksDeskService.getDisplays(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDisplay(arguments: IArguments): Map<String, Any> =
        AeroworksDeskService.getDisplay(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun setDisplayText(arguments: IArguments): String =
        AeroworksDeskService.setDisplayText(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getString(2)
        )

    @LuaFunction(mainThread = true)
    fun setDisplayNumber(arguments: IArguments): String =
        AeroworksDeskService.setDisplayNumber(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getDouble(2),
            arguments.optBoolean(3).orElse(false)
        )

    @LuaFunction(mainThread = true)
    fun clearDisplay(arguments: IArguments) {
        AeroworksDeskService.clearDisplay(member(arguments.get(0)).desk, arguments.get(1))
    }

    @LuaFunction(mainThread = true)
    fun clearDisplays(arguments: IArguments): Int =
        AeroworksDeskService.clearDisplays(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDisplaySize(arguments: IArguments): Map<String, Int> =
        AeroworksDeskService.getDisplaySize(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getDisplayPixel(arguments: IArguments): Boolean =
        AeroworksDeskService.getDisplayPixel(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getInt(2),
            arguments.getInt(3)
        )

    @LuaFunction(mainThread = true)
    fun setDisplayPixel(arguments: IArguments): Boolean =
        AeroworksDeskService.setDisplayPixel(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getInt(2),
            arguments.getInt(3),
            arguments.getBoolean(4)
        )

    @LuaFunction(mainThread = true)
    fun setDisplayPixels(arguments: IArguments): List<String> {
        val table = arguments.getTableUnsafe(2)
        val rows = (1..table.length()).map { index -> table.getString(index) }
        return AeroworksDeskService.setDisplayPixels(
            member(arguments.get(0)).desk,
            arguments.get(1),
            rows
        )
    }

    @LuaFunction(mainThread = true)
    fun clearDisplayPixels(arguments: IArguments) {
        AeroworksDeskService.clearDisplayPixels(member(arguments.get(0)).desk, arguments.get(1))
    }

    private fun owner(): ComputerControlDeskBlockEntity =
        access.owner() ?: throw LuaException("The computer control desk is no longer loaded")

    private fun snapshot() = owner().let { blockEntity ->
        val level = blockEntity.level ?: throw LuaException("The computer control desk is not in a level")
        val snapshot = ConsoleMultiblockManager.resolve(level, blockEntity.blockPos)
        if (snapshot.state == ConsoleNetworkState.CONFLICT) {
            throw LuaException("Multiple computer control desks are connected")
        }
        if (snapshot.state == ConsoleNetworkState.TOO_LARGE) {
            throw LuaException("The control desk multiblock exceeds 64 blocks")
        }
        if (snapshot.state == ConsoleNetworkState.PARTIALLY_LOADED) {
            throw LuaException("The control desk multiblock is only partially loaded")
        }
        snapshot
    }

    private fun member(raw: Any?): ConsoleMember {
        val members = snapshot().members
        return when (raw) {
            is Number -> {
                val number = raw.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0) {
                    throw LuaException("Desk must be a one-based integer index or desk id")
                }
                members.getOrNull(number.toInt() - 1)
                    ?: throw LuaException("Desk index ${number.toInt()} is outside 1..${members.size}")
            }
            is String -> members.firstOrNull { it.id.equals(raw, ignoreCase = true) }
                ?: throw LuaException("Unknown desk id '$raw'")
            else -> throw LuaException("Desk must be a one-based integer index or desk id")
        }
    }

    private fun describeDesk(member: ConsoleMember): Map<String, Any> = linkedMapOf(
        "id" to member.id,
        "index" to member.index,
        "x" to member.pos.x,
        "y" to member.pos.y,
        "z" to member.pos.z,
        "computer" to (member.desk is ComputerControlDeskBlockEntity),
        "owner" to (member.desk === access.owner()),
        "variant" to member.kind.name.lowercase(),
        "facing" to member.facing.name.lowercase(),
        "loaded" to true
    )
}
