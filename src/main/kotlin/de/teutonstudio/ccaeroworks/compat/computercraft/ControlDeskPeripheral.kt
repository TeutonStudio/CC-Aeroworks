package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.MountedModule
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.peripheral.AttachedComputerSet
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.display.DeskDisplayFormatter
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayState
import java.lang.ref.WeakReference

class ControlDeskPeripheral(blockEntity: ConsoleBlockEntity) : IPeripheral {
    private val blockEntity = WeakReference(blockEntity)
    internal val computers = AttachedComputerSet()
    internal var lastInputs: Map<Int, Map<String, Int>>? = null

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
            ControlDeskPeripheralState.deactivate(this)
        }
    }

    override fun equals(other: IPeripheral?): Boolean =
        other is ControlDeskPeripheral && validDesk() != null && validDesk() === other.validDesk()

    @LuaFunction(mainThread = true)
    fun getSocketCount(): Int = desk().socketCount()

    @LuaFunction(mainThread = true)
    fun getSockets(): List<Map<String, Any>> = DeskSockets.entries(desk().socketCount())

    @LuaFunction(mainThread = true)
    fun getModules(): List<Map<String, Any>> {
        val desk = desk()
        return (0 until desk.socketCount()).mapNotNull { socket -> desk.module(socket)?.let { describe(socket, it) } }
    }

    @LuaFunction(mainThread = true)
    fun getModule(arguments: IArguments): Map<String, Any>? {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        return desk.module(socket)?.let { describe(socket, it) }
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun getInput(arguments: IArguments): Any {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        val module = desk.module(socket) ?: throw LuaException("Socket $socket is empty")
        val values = AeroworksModuleAccess.values(module)
        if (values.isEmpty()) throw LuaException("Module at socket $socket is not an input module")
        return if (values.size == 1) values.values.first() else values
    }

    @LuaFunction(mainThread = true)
    fun getInputs(): Map<Int, Any> {
        val desk = desk()
        val result = linkedMapOf<Int, Any>()
        (0 until desk.socketCount()).forEach { socket ->
            val module = desk.module(socket) ?: return@forEach
            val values = AeroworksModuleAccess.values(module)
            if (values.isNotEmpty()) result[socket] = if (values.size == 1) values.values.first() else values
        }
        return result
    }

    @LuaFunction(mainThread = true)
    fun getDisplays(): List<Map<String, Any>> = AeroworksDeskAccess.displays(desk()).map(::describeDisplay)

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun getDisplay(arguments: IArguments): Map<String, Any> {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        return describeDisplay(AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display"))
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayText(arguments: IArguments): String {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        val text = arguments.getString(1)
        return AeroworksDeskAccess.setDisplayText(desk, socket, text)?.text
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayNumber(arguments: IArguments): String {
        val value = arguments.getDouble(1)
        if (!value.isFinite()) throw LuaException("value must be a finite number")
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        val display = AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        val text = DeskDisplayFormatter.formatNumber(value, display.type.width, arguments.optBoolean(2).orElse(false))
        return AeroworksDeskAccess.setDisplayText(desk, socket, text)?.text
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun clearDisplay(arguments: IArguments) {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        if (AeroworksDeskAccess.setDisplayText(desk, socket, "") == null) {
            throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        }
    }

    @LuaFunction(mainThread = true)
    fun clearDisplays(): Int {
        val desk = desk()
        val displays = AeroworksDeskAccess.displays(desk)
        displays.forEach { AeroworksDeskAccess.setDisplayText(desk, it.socket, "") }
        return displays.size
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun getDisplaySize(arguments: IArguments): Map<String, Int> {
        val socket = socketArgument(arguments, 0, desk())
        val display = requiredDisplay(socket)
        return linkedMapOf(
            "width" to DeskDisplayPixels.pixelWidth(display.type),
            "height" to DeskDisplayPixels.HEIGHT
        )
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun getDisplayPixel(arguments: IArguments): Boolean {
        val socket = socketArgument(arguments, 0, desk())
        val x = arguments.getInt(1)
        val y = arguments.getInt(2)
        val display = requiredDisplay(socket)
        val pixels = display.pixels ?: DeskDisplayPixels.blank(display.type)
        validatePixel(pixels, x, y)
        return pixels.get(x - 1, y - 1)
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayPixel(arguments: IArguments): Boolean {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        val x = arguments.getInt(1)
        val y = arguments.getInt(2)
        val enabled = arguments.getBoolean(3)
        val display = AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        val pixels = display.pixels ?: DeskDisplayPixels.blank(display.type)
        validatePixel(pixels, x, y)
        AeroworksDeskAccess.setDisplayPixels(desk, socket, pixels.withPixel(x - 1, y - 1, enabled))
        return enabled
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayPixels(arguments: IArguments): List<String> {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        val display = AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        val table = arguments.getTableUnsafe(1)
        if (table.length() != DeskDisplayPixels.HEIGHT) {
            throw LuaException("pixel table must contain exactly ${DeskDisplayPixels.HEIGHT} rows")
        }
        val rows = (1..DeskDisplayPixels.HEIGHT).map { table.getString(it) }
        val pixels = try {
            DeskDisplayPixels.fromRows(display.type, rows)
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "invalid pixel table")
        }
        AeroworksDeskAccess.setDisplayPixels(desk, socket, pixels)
        return pixels.rows()
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun clearDisplayPixels(arguments: IArguments) {
        val desk = desk()
        val socket = socketArgument(arguments, 0, desk)
        val display = AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        AeroworksDeskAccess.setDisplayPixels(desk, socket, DeskDisplayPixels.blank(display.type))
    }

    internal fun validDesk(): ConsoleBlockEntity? = blockEntity.get()?.takeIf {
        !it.isRemoved && it.level != null && it.level?.isLoaded(it.blockPos) == true
    }

    internal fun snapshotInputs(): Map<Int, Map<String, Int>> {
        val desk = validDesk() ?: return emptyMap()
        val values = linkedMapOf<Int, Map<String, Int>>()
        (0 until desk.socketCount()).forEach { socket ->
            desk.module(socket)?.let { module ->
                AeroworksModuleAccess.values(module).takeIf { it.isNotEmpty() }?.let { values[socket] = it }
            }
        }
        return values
    }

    private fun desk(): ConsoleBlockEntity = validDesk() ?: throw IllegalStateException("Aeroworks Control Desk is no longer loaded")

    @Throws(LuaException::class)
    private fun validateSocket(desk: ConsoleBlockEntity, socket: Int) {
        if (socket !in 0 until desk.socketCount()) {
            throw LuaException("Socket index $socket is outside 0..${desk.socketCount() - 1}")
        }
    }

    @Throws(LuaException::class)
    private fun socketArgument(arguments: IArguments, index: Int, desk: ConsoleBlockEntity): Int {
        val raw = arguments.get(index)
        val socket = when (raw) {
            is String -> DeskSockets.index(raw)
                ?: throw LuaException("Unknown socket '$raw'; expected left, right, or big")
            is Number -> {
                val number = raw.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0) {
                    throw LuaException("Socket must be an integer index or left, right, or big")
                }
                number.toInt()
            }
            else -> throw LuaException("Socket must be an integer index or left, right, or big")
        }
        validateSocket(desk, socket)
        return socket
    }

    @Throws(LuaException::class)
    private fun requiredDisplay(socket: Int): DeskDisplayState {
        val desk = desk()
        validateSocket(desk, socket)
        return AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
    }

    @Throws(LuaException::class)
    private fun validatePixel(pixels: DeskDisplayPixels, x: Int, y: Int) {
        if (x !in 1..pixels.width || y !in 1..pixels.height) {
            throw LuaException("Pixel ($x,$y) is outside 1..${pixels.width}, 1..${pixels.height}")
        }
    }

    private fun describe(socket: Int, module: MountedModule): Map<String, Any> {
        val values = AeroworksModuleAccess.values(module)
        val display = AeroworksDeskAccess.display(desk(), socket)
        return LuaModuleDescription.describe(
            LuaModuleSnapshot(
                socket = socket,
                socketName = DeskSockets.name(socket),
                id = AeroworksModuleAccess.id(module).toString(),
                kind = AeroworksModuleAccess.kind(module),
                values = values,
                displayWidth = display?.type?.width,
                displayText = display?.text,
                displayPixels = display?.pixels?.rows()
            )
        )
    }

    private fun describeDisplay(display: DeskDisplayState): Map<String, Any> = linkedMapOf(
        "socket" to display.socket,
        "socketName" to DeskSockets.name(display.socket),
        "id" to "${CCAeroworks.MOD_ID}:${display.type.modulePath}",
        "width" to display.type.width,
        "text" to display.text,
        "mode" to if (display.pixels == null) "text" else "pixels",
        "pixelWidth" to DeskDisplayPixels.pixelWidth(display.type),
        "pixelHeight" to DeskDisplayPixels.HEIGHT,
        "pixels" to (display.pixels ?: DeskDisplayPixels.blank(display.type)).rows()
    )
}
