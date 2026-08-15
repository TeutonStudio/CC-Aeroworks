package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.ObjectLuaTable
import dan200.computercraft.api.peripheral.AttachedComputerSet
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskInputSnapshot
import de.teutonstudio.ccaeroworks.display.DisplayBindingService
import java.lang.ref.WeakReference

class ControlDeskPeripheral(blockEntity: ConsoleBlockEntity) : IPeripheral {
    private val blockEntity = WeakReference(blockEntity)
    internal val computers = AttachedComputerSet()
    internal var lastInputs: Map<Int, DeskInputSnapshot>? = null

    override fun getType(): String = "ControlDesk"

    override fun getAdditionalTypes(): Set<String> = setOf(
        "control_desk",
        "cc_aeroworks:control_desk",
        CCAeroworks.PERIPHERAL_TYPE
    )

    override fun getTarget(): Any? = validDesk()

    override fun attach(computer: IComputerAccess) {
        computers.add(computer)
        ControlDeskPeripheralState.activate(this)
    }

    override fun detach(computer: IComputerAccess) {
        computers.remove(computer)
        if (!computers.hasComputers()) {
            lastInputs = null
            ControlDeskPeripheralState.deactivate(this)
        }
    }

    override fun equals(other: IPeripheral?): Boolean =
        other is ControlDeskPeripheral && validDesk() != null && validDesk() === other.validDesk()

    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> {
        val desk = desk()
        val pos = desk.blockPos
        return linkedMapOf(
            "id" to (desk as DeskIdentityAccess).ccaeroworks_getDeskId().toString(),
            "type" to type,
            "x" to pos.x,
            "y" to pos.y,
            "z" to pos.z,
            "dimension" to desk.level!!.dimension().location().toString(),
            "loaded" to true
        )
    }

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
    fun setDisplayNumber(arguments: IArguments): String = AeroworksDeskService.setDisplayNumber(
        desk(), arguments.get(0), arguments.getDouble(1), arguments.optBoolean(2).orElse(false)
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
    fun getDisplayPixel(arguments: IArguments): Boolean = AeroworksDeskService.getDisplayPixel(
        desk(), arguments.get(0), arguments.getInt(1), arguments.getInt(2)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixel(arguments: IArguments): Boolean = AeroworksDeskService.setDisplayPixel(
        desk(),
        arguments.get(0),
        arguments.getInt(1),
        arguments.getInt(2),
        arguments.getBoolean(3)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixels(arguments: IArguments): List<String> {
        val table = ObjectLuaTable(arguments.getTable(1))
        val rows = (1..table.length()).map(table::getString)
        return AeroworksDeskService.setDisplayPixels(desk(), arguments.get(0), rows)
    }

    @LuaFunction(mainThread = true)
    fun clearDisplayPixels(arguments: IArguments) =
        AeroworksDeskService.clearDisplayPixels(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getRadarSources(): List<Map<String, Any>> =
        DisplayBindingService.getRadarSources(desk())

    @LuaFunction(mainThread = true)
    fun getDisplayBinding(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.getBinding(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun setRadarSource(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.setRadarSource(desk(), arguments.get(0), arguments.getString(1))

    @LuaFunction(mainThread = true)
    fun setDisplayTouchScript(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.setTouchScript(desk(), arguments.get(0), arguments.getString(1))

    @LuaFunction(mainThread = true)
    fun clearDisplayBinding(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.clearBinding(desk(), arguments.get(0))

    internal fun validDesk(): ConsoleBlockEntity? = blockEntity.get()?.takeIf {
        !it.isRemoved && it.level != null && it.level?.isLoaded(it.blockPos) == true
    }

    internal fun snapshotInputs(): Map<Int, DeskInputSnapshot> =
        validDesk()?.let(AeroworksDeskService::snapshotInputs).orEmpty()

    private fun desk(): ConsoleBlockEntity =
        validDesk() ?: throw LuaException("Aeroworks control desk is no longer loaded")
}
