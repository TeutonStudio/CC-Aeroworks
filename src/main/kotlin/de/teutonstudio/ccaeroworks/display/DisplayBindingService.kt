package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService

object DisplayBindingService {
    @Throws(LuaException::class)
    fun getBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        return DisplayBindings.describe(DisplayBindings.get(desk, socket))
    }

    @Throws(LuaException::class)
    fun setTouchScript(desk: ConsoleBlockEntity, rawSocket: Any?, path: String): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val normalized = path.trim()
        if (normalized.isEmpty()) throw LuaException("Touch handler path must not be empty")
        if (normalized.length > DisplayBindings.MAX_HANDLER_PATH_LENGTH) throw LuaException("Touch handler path is too long")
        val binding = DisplayBinding.LuaHandler(normalized)
        if (!DisplayBindings.set(desk, socket, binding)) {
            throw LuaException("Touch scripts are supported only by the large Desk Display")
        }
        return DisplayBindings.describe(binding)
    }

    @Throws(LuaException::class)
    fun clearBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        if (!DisplayBindings.clear(desk, socket)) throw LuaException("Socket $socket is invalid")
        return DisplayBindings.describe(DisplayBinding.Default)
    }
}
