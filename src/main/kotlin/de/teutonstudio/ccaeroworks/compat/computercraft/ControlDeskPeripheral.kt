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
import de.teutonstudio.ccaeroworks.display.DeskDisplayFormatter
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayState
import java.lang.ref.WeakReference
import java.util.Optional

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
    fun getModules(): List<Map<String, Any>> {
        val desk = desk()
        return (0 until desk.socketCount()).mapNotNull { socket -> desk.module(socket)?.let { describe(socket, it) } }
    }

    @LuaFunction(mainThread = true)
    fun getModule(socket: Int): Map<String, Any>? {
        val desk = desk()
        validateSocket(desk, socket)
        return desk.module(socket)?.let { describe(socket, it) }
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun getInput(socket: Int): Any {
        val desk = desk()
        validateSocket(desk, socket)
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
    fun getDisplay(socket: Int): Map<String, Any> {
        val desk = desk()
        validateSocket(desk, socket)
        return describeDisplay(AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display"))
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayText(socket: Int, text: String): String {
        val desk = desk()
        validateSocket(desk, socket)
        return AeroworksDeskAccess.setDisplayText(desk, socket, text)?.text
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayNumber(socket: Int, value: Double, zeroPad: Optional<Boolean>): String {
        if (!value.isFinite()) throw LuaException("value must be a finite number")
        val desk = desk()
        validateSocket(desk, socket)
        val display = AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        return setDisplayText(socket, DeskDisplayFormatter.formatNumber(value, display.type.width, zeroPad.orElse(false)))
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun clearDisplay(socket: Int) {
        setDisplayText(socket, "")
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
    fun getDisplaySize(socket: Int): Map<String, Int> {
        val display = requiredDisplay(socket)
        return linkedMapOf(
            "width" to DeskDisplayPixels.pixelWidth(display.type),
            "height" to DeskDisplayPixels.HEIGHT
        )
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun getDisplayPixel(socket: Int, x: Int, y: Int): Boolean {
        val display = requiredDisplay(socket)
        val pixels = display.pixels ?: DeskDisplayPixels.blank(display.type)
        validatePixel(pixels, x, y)
        return pixels.get(x - 1, y - 1)
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setDisplayPixel(socket: Int, x: Int, y: Int, enabled: Boolean): Boolean {
        val desk = desk()
        validateSocket(desk, socket)
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
        val socket = arguments.getInt(0)
        val desk = desk()
        validateSocket(desk, socket)
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
    fun clearDisplayPixels(socket: Int) {
        val desk = desk()
        validateSocket(desk, socket)
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
        "id" to "${CCAeroworks.MOD_ID}:${display.type.modulePath}",
        "width" to display.type.width,
        "text" to display.text,
        "mode" to if (display.pixels == null) "text" else "pixels",
        "pixelWidth" to DeskDisplayPixels.pixelWidth(display.type),
        "pixelHeight" to DeskDisplayPixels.HEIGHT,
        "pixels" to (display.pixels ?: DeskDisplayPixels.blank(display.type)).rows()
    )
}
