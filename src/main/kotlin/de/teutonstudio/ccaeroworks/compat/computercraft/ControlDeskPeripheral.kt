package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.AttachedComputerSet
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskInputSnapshot
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSnapshot
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import java.lang.ref.WeakReference

internal data class PeripheralNetworkDeskSnapshot(
    val index: Int,
    val inputs: Map<Int, DeskInputSnapshot>
)

internal data class PeripheralNetworkSnapshot(
    val state: ConsoleNetworkState,
    val memberCount: Int,
    val revision: Long,
    val signature: String,
    val desks: Map<String, PeripheralNetworkDeskSnapshot>
)

class ControlDeskPeripheral(blockEntity: ConsoleBlockEntity) : IPeripheral {
    private val blockEntity = WeakReference(blockEntity)
    internal val computers = AttachedComputerSet()
    internal var lastInputs: Map<Int, DeskInputSnapshot>? = null
    internal var lastNetwork: PeripheralNetworkSnapshot? = null

    override fun getType(): String = CCAeroworks.PERIPHERAL_TYPE

    override fun getTarget(): Any? = validDesk()

    override fun attach(computer: IComputerAccess) {
        computers.add(computer)
        ControlDeskPeripheralState.activate(this)
    }

    override fun detach(computer: IComputerAccess) {
        computers.remove(computer)
        if (!computers.hasComputers()) {
            lastInputs = null
            lastNetwork = null
            ControlDeskPeripheralState.deactivate(this)
        }
    }

    override fun equals(other: IPeripheral?): Boolean =
        other is ControlDeskPeripheral && validDesk() != null && validDesk() === other.validDesk()

    @LuaFunction(mainThread = true)
    fun getSocketCount(): Int = desk().socketCount()

    @LuaFunction(mainThread = true)
    fun getSockets(): List<Map<String, Any>> = AeroworksDeskService.getSockets(desk())

    @LuaFunction(mainThread = true)
    fun getModules(): List<Map<String, Any>> = AeroworksDeskService.getModules(desk())

    @LuaFunction(mainThread = true)
    fun getModule(arguments: IArguments): Map<String, Any>? =
        AeroworksDeskService.getModule(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getInput(arguments: IArguments): Any =
        AeroworksDeskService.getInput(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getInputs(): Map<Int, Any> = AeroworksDeskService.getInputs(desk())

    @LuaFunction(mainThread = true)
    fun getDisplays(): List<Map<String, Any>> = AeroworksDeskService.getDisplays(desk())

    @LuaFunction(mainThread = true)
    fun getDisplay(arguments: IArguments): Map<String, Any> =
        AeroworksDeskService.getDisplay(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun setDisplayText(arguments: IArguments): String =
        AeroworksDeskService.setDisplayText(desk(), arguments.get(0), arguments.getString(1))

    @LuaFunction(mainThread = true)
    fun setDisplayNumber(arguments: IArguments): String =
        AeroworksDeskService.setDisplayNumber(
            desk(),
            arguments.get(0),
            arguments.getDouble(1),
            arguments.optBoolean(2).orElse(false)
        )

    @LuaFunction(mainThread = true)
    fun clearDisplay(arguments: IArguments) =
        AeroworksDeskService.clearDisplay(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun clearDisplays(): Int = AeroworksDeskService.clearDisplays(desk())

    @LuaFunction(mainThread = true)
    fun getDisplaySize(arguments: IArguments): Map<String, Int> =
        AeroworksDeskService.getDisplaySize(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getDisplayPixel(arguments: IArguments): Boolean =
        AeroworksDeskService.getDisplayPixel(
            desk(),
            arguments.get(0),
            arguments.getInt(1),
            arguments.getInt(2)
        )

    @LuaFunction(mainThread = true)
    fun setDisplayPixel(arguments: IArguments): Boolean =
        AeroworksDeskService.setDisplayPixel(
            desk(),
            arguments.get(0),
            arguments.getInt(1),
            arguments.getInt(2),
            arguments.getBoolean(3)
        )

    @LuaFunction(mainThread = true)
    fun setDisplayPixels(arguments: IArguments): List<String> {
        val table = arguments.getTableUnsafe(1)
        val rows = (1..table.length()).map { index -> table.getString(index) }
        return AeroworksDeskService.setDisplayPixels(desk(), arguments.get(0), rows)
    }

    @LuaFunction(mainThread = true)
    fun clearDisplayPixels(arguments: IArguments) =
        AeroworksDeskService.clearDisplayPixels(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getDesks(): List<Map<String, Any>> =
        usableSnapshot().members.map(::describeDesk)

    @LuaFunction(mainThread = true)
    fun getDesk(arguments: IArguments): Map<String, Any> =
        describeDesk(member(arguments.get(0)))

    @LuaFunction(mainThread = true)
    fun getNetwork(): Map<String, Any> {
        val snapshot = usableSnapshot()
        return linkedMapOf(
            "state" to snapshot.state.name.lowercase(),
            "memberCount" to snapshot.members.size,
            "revision" to snapshot.revision
        )
    }

    @LuaFunction(mainThread = true)
    fun getDeskSocketCount(arguments: IArguments): Int =
        member(arguments.get(0)).desk.socketCount()

    @LuaFunction(mainThread = true)
    fun getDeskSockets(arguments: IArguments): List<Map<String, Any>> =
        AeroworksDeskService.getSockets(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDeskModules(arguments: IArguments): List<Map<String, Any>> =
        AeroworksDeskService.getModules(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDeskModule(arguments: IArguments): Map<String, Any>? =
        AeroworksDeskService.getModule(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getDeskInput(arguments: IArguments): Any =
        AeroworksDeskService.getInput(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getDeskInputs(arguments: IArguments): Map<Int, Any> =
        AeroworksDeskService.getInputs(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDeskDisplays(arguments: IArguments): List<Map<String, Any>> =
        AeroworksDeskService.getDisplays(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDeskDisplay(arguments: IArguments): Map<String, Any> =
        AeroworksDeskService.getDisplay(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun setDeskDisplayText(arguments: IArguments): String =
        AeroworksDeskService.setDisplayText(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getString(2)
        )

    @LuaFunction(mainThread = true)
    fun setDeskDisplayNumber(arguments: IArguments): String =
        AeroworksDeskService.setDisplayNumber(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getDouble(2),
            arguments.optBoolean(3).orElse(false)
        )

    @LuaFunction(mainThread = true)
    fun clearDeskDisplay(arguments: IArguments) =
        AeroworksDeskService.clearDisplay(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun clearDeskDisplays(arguments: IArguments): Int =
        AeroworksDeskService.clearDisplays(member(arguments.get(0)).desk)

    @LuaFunction(mainThread = true)
    fun getDeskDisplaySize(arguments: IArguments): Map<String, Int> =
        AeroworksDeskService.getDisplaySize(member(arguments.get(0)).desk, arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getDeskDisplayPixel(arguments: IArguments): Boolean =
        AeroworksDeskService.getDisplayPixel(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getInt(2),
            arguments.getInt(3)
        )

    @LuaFunction(mainThread = true)
    fun setDeskDisplayPixel(arguments: IArguments): Boolean =
        AeroworksDeskService.setDisplayPixel(
            member(arguments.get(0)).desk,
            arguments.get(1),
            arguments.getInt(2),
            arguments.getInt(3),
            arguments.getBoolean(4)
        )

    @LuaFunction(mainThread = true)
    fun setDeskDisplayPixels(arguments: IArguments): List<String> {
        val table = arguments.getTableUnsafe(2)
        val rows = (1..table.length()).map { index -> table.getString(index) }
        return AeroworksDeskService.setDisplayPixels(
            member(arguments.get(0)).desk,
            arguments.get(1),
            rows
        )
    }

    @LuaFunction(mainThread = true)
    fun clearDeskDisplayPixels(arguments: IArguments) =
        AeroworksDeskService.clearDisplayPixels(member(arguments.get(0)).desk, arguments.get(1))

    internal fun validDesk(): ConsoleBlockEntity? = blockEntity.get()?.takeIf {
        !it.isRemoved && it.level != null && it.level?.isLoaded(it.blockPos) == true
    }

    internal fun snapshotInputs(): Map<Int, DeskInputSnapshot> =
        validDesk()?.let(AeroworksDeskService::snapshotInputs).orEmpty()

    internal fun snapshotNetwork(): PeripheralNetworkSnapshot? {
        val attached = validDesk() ?: return null
        val level = attached.level ?: return null
        val snapshot = ConsoleMultiblockManager.resolve(level, attached.blockPos)
        val desks = snapshot.members.associate { member ->
            member.id to PeripheralNetworkDeskSnapshot(
                index = member.index,
                inputs = AeroworksDeskService.snapshotInputs(member.desk)
            )
        }
        val signature = buildString {
            append(snapshot.state.name)
            snapshot.members.forEach { member ->
                append('|')
                append(member.id)
                append(':')
                append(member.kind.name)
            }
        }
        return PeripheralNetworkSnapshot(
            state = snapshot.state,
            memberCount = snapshot.members.size,
            revision = snapshot.revision,
            signature = signature,
            desks = desks
        )
    }

    private fun desk(): ConsoleBlockEntity =
        validDesk() ?: throw LuaException("Aeroworks control desk is no longer loaded")

    private fun usableSnapshot(): ConsoleMultiblockSnapshot {
        val attached = desk()
        val level = attached.level ?: throw LuaException("Aeroworks control desk is not in a level")
        val snapshot = ConsoleMultiblockManager.resolve(level, attached.blockPos)
        when (snapshot.state) {
            ConsoleNetworkState.TOO_LARGE ->
                throw LuaException("The control desk multiblock exceeds 64 blocks")

            ConsoleNetworkState.PARTIALLY_LOADED ->
                throw LuaException("The control desk multiblock is only partially loaded")

            else -> return snapshot
        }
    }

    private fun member(raw: Any?): ConsoleMember {
        val members = usableSnapshot().members
        return when (raw) {
            is Number -> {
                val number = raw.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0) {
                    throw LuaException("Desk must be a one-based integer index or desk id")
                }
                members.getOrNull(number.toInt() - 1)
                    ?: throw LuaException(
                        "Desk index ${number.toInt()} is outside 1..${members.size}"
                    )
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
        "attached" to (member.desk === validDesk()),
        "variant" to member.kind.name.lowercase(),
        "facing" to member.facing.name.lowercase(),
        "loaded" to true
    )
}
