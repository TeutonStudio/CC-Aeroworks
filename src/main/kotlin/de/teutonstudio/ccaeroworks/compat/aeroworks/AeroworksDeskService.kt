package de.teutonstudio.ccaeroworks.compat.aeroworks

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.MountedModule
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.computercraft.LuaModuleDescription
import de.teutonstudio.ccaeroworks.compat.computercraft.LuaModuleSnapshot
import de.teutonstudio.ccaeroworks.display.DeskDisplayFormatter
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayState

data class DeskInputSnapshot(
    val moduleId: String,
    val channels: Map<String, Int>
)

object AeroworksDeskService {
    fun getSockets(desk: ConsoleBlockEntity): List<Map<String, Any>> =
        DeskSockets.entries(desk.socketCount())

    fun getModules(desk: ConsoleBlockEntity): List<Map<String, Any>> =
        (0 until desk.socketCount()).mapNotNull { socket ->
            desk.module(socket)?.let { describeModule(desk, socket, it) }
        }

    fun getModule(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any>? {
        val socket = parseSocket(desk, rawSocket)
        return desk.module(socket)?.let { describeModule(desk, socket, it) }
    }

    @Throws(LuaException::class)
    fun getInput(desk: ConsoleBlockEntity, rawSocket: Any?): Any {
        val socket = parseSocket(desk, rawSocket)
        val module = desk.module(socket) ?: throw LuaException("Socket $socket is empty")
        val values = AeroworksModuleAccess.values(module)
        if (values.isEmpty()) throw LuaException("Module at socket $socket is not an input module")
        return if (values.size == 1) values.values.first() else values
    }

    fun getInputs(desk: ConsoleBlockEntity): Map<Int, Any> {
        val result = linkedMapOf<Int, Any>()
        (0 until desk.socketCount()).forEach { socket ->
            val module = desk.module(socket) ?: return@forEach
            val values = AeroworksModuleAccess.values(module)
            if (values.isNotEmpty()) {
                result[socket] = if (values.size == 1) values.values.first() else values
            }
        }
        return result
    }

    fun getDisplays(desk: ConsoleBlockEntity): List<Map<String, Any>> =
        AeroworksDeskAccess.displays(desk).map(::describeDisplay)

    @Throws(LuaException::class)
    fun getDisplay(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> =
        describeDisplay(requiredDisplay(desk, parseSocket(desk, rawSocket)))

    @Throws(LuaException::class)
    fun setDisplayText(desk: ConsoleBlockEntity, rawSocket: Any?, text: String): String {
        val socket = parseSocket(desk, rawSocket)
        return AeroworksDeskAccess.setDisplayText(desk, socket, text)?.text
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
    }

    @Throws(LuaException::class)
    fun setDisplayNumber(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        value: Double,
        zeroPad: Boolean
    ): String {
        if (!value.isFinite()) throw LuaException("value must be a finite number")
        val socket = parseSocket(desk, rawSocket)
        val display = requiredDisplay(desk, socket)
        val text = DeskDisplayFormatter.formatNumber(value, display.type.width, zeroPad)
        return AeroworksDeskAccess.setDisplayText(desk, socket, text)?.text
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
    }

    @Throws(LuaException::class)
    fun clearDisplay(desk: ConsoleBlockEntity, rawSocket: Any?) {
        val socket = parseSocket(desk, rawSocket)
        if (AeroworksDeskAccess.setDisplayText(desk, socket, "") == null) {
            throw LuaException("Module at socket $socket is not a CC-Aeroworks display")
        }
    }

    fun clearDisplays(desk: ConsoleBlockEntity): Int {
        val displays = AeroworksDeskAccess.displays(desk)
        displays.forEach { AeroworksDeskAccess.setDisplayText(desk, it.socket, "") }
        return displays.size
    }

    @Throws(LuaException::class)
    fun getDisplaySize(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Int> {
        val display = requiredDisplay(desk, parseSocket(desk, rawSocket))
        return linkedMapOf(
            "width" to display.type.pixelWidth,
            "height" to display.type.pixelHeight
        )
    }

    @Throws(LuaException::class)
    fun getDisplayPixel(desk: ConsoleBlockEntity, rawSocket: Any?, x: Int, y: Int): Boolean {
        val display = requiredDisplay(desk, parseSocket(desk, rawSocket))
        val pixels = display.pixels ?: DeskDisplayPixels.blank(display.type)
        validatePixel(pixels, x, y)
        return pixels.get(x - 1, y - 1)
    }

    @Throws(LuaException::class)
    fun setDisplayPixel(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        x: Int,
        y: Int,
        enabled: Boolean
    ): Boolean {
        val socket = parseSocket(desk, rawSocket)
        val display = requiredDisplay(desk, socket)
        val pixels = display.pixels ?: DeskDisplayPixels.blank(display.type)
        validatePixel(pixels, x, y)
        AeroworksDeskAccess.setDisplayPixels(desk, socket, pixels.withPixel(x - 1, y - 1, enabled))
        return enabled
    }

    @Throws(LuaException::class)
    fun setDisplayPixels(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        rows: List<String>
    ): List<String> {
        val socket = parseSocket(desk, rawSocket)
        val display = requiredDisplay(desk, socket)
        val pixels = try {
            DeskDisplayPixels.fromRows(display.type, rows)
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "invalid pixel table")
        }
        AeroworksDeskAccess.setDisplayPixels(desk, socket, pixels)
        return pixels.rows()
    }

    @Throws(LuaException::class)
    fun clearDisplayPixels(desk: ConsoleBlockEntity, rawSocket: Any?) {
        val socket = parseSocket(desk, rawSocket)
        val display = requiredDisplay(desk, socket)
        AeroworksDeskAccess.setDisplayPixels(desk, socket, DeskDisplayPixels.blank(display.type))
    }

    fun snapshotInputs(desk: ConsoleBlockEntity): Map<Int, DeskInputSnapshot> {
        val result = linkedMapOf<Int, DeskInputSnapshot>()
        (0 until desk.socketCount()).forEach { socket ->
            val module = desk.module(socket) ?: return@forEach
            val values = AeroworksModuleAccess.values(module)
            if (values.isNotEmpty()) {
                result[socket] = DeskInputSnapshot(
                    AeroworksModuleAccess.id(module).toString(),
                    values.toMap()
                )
            }
        }
        return result
    }

    @Throws(LuaException::class)
    fun parseSocket(desk: ConsoleBlockEntity, raw: Any?): Int {
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
        if (socket !in 0 until desk.socketCount()) {
            throw LuaException("Socket index $socket is outside 0..${desk.socketCount() - 1}")
        }
        return socket
    }

    private fun requiredDisplay(desk: ConsoleBlockEntity, socket: Int): DeskDisplayState =
        AeroworksDeskAccess.display(desk, socket)
            ?: throw LuaException("Module at socket $socket is not a CC-Aeroworks display")

    private fun validatePixel(pixels: DeskDisplayPixels, x: Int, y: Int) {
        if (x !in 1..pixels.width || y !in 1..pixels.height) {
            throw LuaException("Pixel ($x,$y) is outside 1..${pixels.width}, 1..${pixels.height}")
        }
    }

    private fun describeModule(
        desk: ConsoleBlockEntity,
        socket: Int,
        module: MountedModule
    ): Map<String, Any> {
        val values = AeroworksModuleAccess.values(module)
        val display = AeroworksDeskAccess.display(desk, socket)
        return LuaModuleDescription.describe(
            LuaModuleSnapshot(
                socket = socket,
                socketName = DeskSockets.name(socket),
                id = AeroworksModuleAccess.id(module).toString(),
                kind = AeroworksModuleAccess.kind(module),
                values = values,
                displayWidth = display?.type?.width,
                displayText = display?.text,
                displayPixels = display?.pixels?.rows(),
                displayPixelWidth = display?.type?.pixelWidth,
                displayPixelHeight = display?.type?.pixelHeight
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
        "pixelWidth" to display.type.pixelWidth,
        "pixelHeight" to display.type.pixelHeight,
        "PIXEL_WIDTH" to display.type.pixelWidth,
        "PIXEL_HEIGHT" to display.type.pixelHeight,
        "pixels" to (display.pixels ?: DeskDisplayPixels.blank(display.type)).rows()
    )
}
