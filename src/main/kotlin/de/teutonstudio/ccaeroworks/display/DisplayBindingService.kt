package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics

object DisplayBindingService {
    @Throws(LuaException::class)
    fun getBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val binding = DisplayBindings.get(desk, socket)
        TouchInputDiagnostics.info(
            "binding",
            "read desk=${desk.blockPos.toShortString()} socket=$socket -> ${DisplayBindings.describe(binding)}"
        )
        return DisplayBindings.describe(binding)
    }

    @Throws(LuaException::class)
    fun setTouchScript(desk: ConsoleBlockEntity, rawSocket: Any?, path: String): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val normalized = path.trim()
        TouchInputDiagnostics.info(
            "binding",
            "set requested desk=${desk.blockPos.toShortString()} socket=$socket rawPath='$path' normalized='$normalized'"
        )
        if (normalized.isEmpty()) throw LuaException("Touch handler path must not be empty")
        if (normalized.length > DisplayBindings.MAX_HANDLER_PATH_LENGTH) throw LuaException("Touch handler path is too long")
        val binding = DisplayBinding.LuaHandler(normalized)
        if (!DisplayBindings.set(desk, socket, binding)) {
            TouchInputDiagnostics.warn(
                "binding",
                "set rejected desk=${desk.blockPos.toShortString()} socket=$socket path='$normalized': display binding does not support Lua handlers"
            )
            throw LuaException("Touch scripts are supported only by the large Desk Display")
        }
        val stored = DisplayBindings.get(desk, socket)
        TouchInputDiagnostics.info(
            "binding",
            "set accepted desk=${desk.blockPos.toShortString()} socket=$socket path='$normalized' readback=${DisplayBindings.describe(stored)}"
        )
        return DisplayBindings.describe(binding)
    }

    @Throws(LuaException::class)
    fun clearBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        if (!DisplayBindings.clear(desk, socket)) throw LuaException("Socket $socket is invalid")
        TouchInputDiagnostics.info(
            "binding",
            "cleared desk=${desk.blockPos.toShortString()} socket=$socket"
        )
        return DisplayBindings.describe(DisplayBinding.Default)
    }
}
