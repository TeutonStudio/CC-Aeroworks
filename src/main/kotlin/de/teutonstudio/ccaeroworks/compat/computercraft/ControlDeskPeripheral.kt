package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.Coerced
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
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.display.DisplayBindingService
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers
import de.teutonstudio.ccaeroworks.computer.ConsoleTerminal
import de.teutonstudio.ccaeroworks.computer.ConsoleTerminals
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

open class ControlDeskPeripheral(blockEntity: ConsoleBlockEntity) : IPeripheral {
    private val blockEntity = WeakReference(blockEntity)
    internal val computers = AttachedComputerSet()
    internal var lastInputs: Map<Int, DeskInputSnapshot>? = null

    override fun getType(): String = "ControlDesk"

    override fun getAdditionalTypes(): Set<String> = linkedSetOf(
        "control_desk",
        "cc_aeroworks:control_desk",
        CCAeroworks.PERIPHERAL_TYPE
    ).apply { if (validDesk()?.let(ConsoleTerminals::resolve) != null) add("terminal") }

    override fun getTarget(): Any? = validDesk()

    override fun attach(computer: IComputerAccess) {
        computers.add(computer)
        validDesk()?.let(ConsoleTerminals::resolve)?.peripheral()?.attach(computer)
        ControlDeskPeripheralState.activate(this)
    }

    override fun detach(computer: IComputerAccess) {
        validDesk()?.let(ConsoleTerminals::resolve)?.peripheral()?.detach(computer)
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
        val computer = ConsoleComputers.resolve(desk)
        val terminal = ConsoleTerminals.resolve(desk)
        return linkedMapOf<String, Any>(
            "id" to (desk as DeskIdentityAccess).ccaeroworks_getDeskId().toString(),
            "type" to type,
            "x" to pos.x,
            "y" to pos.y,
            "z" to pos.z,
            "dimension" to desk.level!!.dimension().location().toString(),
            "loaded" to true,
            "device" to when {
                computer?.isAdvanced == true -> "advanced_computer"
                computer != null -> "computer"
                terminal?.isAdvanced == true -> "advanced_terminal"
                terminal != null -> "terminal"
                else -> "none"
            },
            "programmable" to (computer != null)
        ).apply {
            (desk as? ConsoleAssemblyStateAccess)?.ccaeroworks_getAssemblyId()?.let {
                put("assemblyId", it.toString())
            }
        }
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
    fun setDisplayResolution(arguments: IArguments): Map<String, Int> =
        AeroworksDeskService.setDisplayResolution(desk(), arguments.get(0), arguments.get(1))

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
    fun setDisplayPixelBatch(arguments: IArguments): Int = AeroworksDeskService.setDisplayPixelBatch(
        desk(),
        arguments.get(0),
        DisplayPixelBatchArguments.parse(arguments, 1),
        arguments.optBoolean(2).orElse(true)
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
    fun getDisplayColor(arguments: IArguments): Int =
        AeroworksDeskService.getDisplayColor(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun setDisplayColor(arguments: IArguments): Int =
        AeroworksDeskService.setDisplayColor(desk(), arguments.get(0), arguments.get(1))

    @LuaFunction(mainThread = true)
    fun getDisplayBinding(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.getBinding(desk(), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun setDisplayTouchScript(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.setTouchScript(desk(), arguments.get(0), arguments.getString(1))

    /** The selected script executes on the console's embedded computer when this module changes. */
    @LuaFunction(mainThread = true)
    fun setControlScript(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.setControlScript(desk(), arguments.get(0), arguments.getString(1))

    @LuaFunction(mainThread = true)
    fun clearDisplayBinding(arguments: IArguments): Map<String, Any> =
        DisplayBindingService.clearBinding(desk(), arguments.get(0))

    /**
     * Diagnostic bridge used by the bundled display-handler runtime. It intentionally logs at
     * INFO so Lua load/dispatch failures are visible in ordinary latest.log without debug mode.
     */
    @LuaFunction(mainThread = true)
    fun debugDisplayTouchLog(arguments: IArguments) {
        val currentDesk = desk()
        val socket = AeroworksDeskService.parseSocket(currentDesk, arguments.get(0))
        val message = arguments.getString(1).take(2000)
        TouchInputDiagnostics.info(
            "lua",
            "desk=${currentDesk.blockPos.toShortString()} socket=$socket $message"
        )
    }

    internal fun terminalInstalled(terminal: ConsoleTerminal) {
        computers.forEach { terminal.peripheral().attach(it) }
    }

    internal fun terminalRemoved(terminal: ConsoleTerminal) {
        computers.forEach { terminal.peripheral().detach(it) }
    }

    private fun terminalMethods() = validDesk()?.let(ConsoleTerminals::resolve)?.termMethods()
        ?: throw LuaException("No CC: Terminals terminal is installed in this control desk network")

    @LuaFunction fun write(text: Coerced<String>) = terminalMethods().write(text)
    @LuaFunction fun scroll(lines: Int) = terminalMethods().scroll(lines)
    @LuaFunction fun getCursorPos(): Array<Any> = terminalMethods().cursorPos
    @LuaFunction fun setCursorPos(x: Int, y: Int) = terminalMethods().setCursorPos(x, y)
    @LuaFunction fun getCursorBlink(): Boolean = terminalMethods().cursorBlink
    @LuaFunction fun setCursorBlink(blink: Boolean) = terminalMethods().setCursorBlink(blink)
    @LuaFunction fun getSize(): Array<Any> = terminalMethods().size
    @LuaFunction fun clear() = terminalMethods().clear()
    @LuaFunction fun clearLine() = terminalMethods().clearLine()
    @LuaFunction("getTextColour", "getTextColor") fun getTextColour(): Int = terminalMethods().textColour
    @LuaFunction("setTextColour", "setTextColor") fun setTextColour(colour: Int) = terminalMethods().setTextColour(colour)
    @LuaFunction("getBackgroundColour", "getBackgroundColor")
    fun getBackgroundColour(): Int = terminalMethods().backgroundColour
    @LuaFunction("setBackgroundColour", "setBackgroundColor")
    fun setBackgroundColour(colour: Int) = terminalMethods().setBackgroundColour(colour)
    @LuaFunction("isColour", "isColor") fun isColour(): Boolean = terminalMethods().isColour
    @LuaFunction fun blit(text: ByteBuffer, textColour: ByteBuffer, backgroundColour: ByteBuffer) =
        terminalMethods().blit(text, textColour, backgroundColour)
    @LuaFunction("setPaletteColour", "setPaletteColor")
    fun setPaletteColour(arguments: IArguments) = terminalMethods().setPaletteColour(arguments)
    @LuaFunction("getPaletteColour", "getPaletteColor")
    fun getPaletteColour(colour: Int): Array<Any> = terminalMethods().getPaletteColour(colour)

    internal fun validDesk(): ConsoleBlockEntity? = blockEntity.get()?.takeIf {
        !it.isRemoved && it.level != null && it.level?.isLoaded(it.blockPos) == true
    }

    internal fun snapshotInputs(): Map<Int, DeskInputSnapshot> =
        validDesk()?.let(AeroworksDeskService::snapshotInputs).orEmpty()

    protected fun desk(): ConsoleBlockEntity =
        validDesk() ?: throw LuaException("Aeroworks control desk is no longer loaded")
}
