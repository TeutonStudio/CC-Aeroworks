package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes

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
        if (desk.module(socket)?.let { CCModuleTypes.isInteractiveDisplay(it.type()) } != true) {
            throw LuaException("Touch scripts require an advanced Desk Display")
        }
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
            throw LuaException("Touch scripts require an advanced Desk Display")
        }
        val stored = DisplayBindings.get(desk, socket)
        TouchInputDiagnostics.info(
            "binding",
            "set accepted desk=${desk.blockPos.toShortString()} socket=$socket path='$normalized' readback=${DisplayBindings.describe(stored)}"
        )
        return DisplayBindings.describe(binding)
    }

    @Throws(LuaException::class)
    fun setControlScript(desk: ConsoleBlockEntity, rawSocket: Any?, path: String): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val module = desk.module(socket) ?: throw LuaException("Socket $socket is empty")
        if (!ControlScriptBindings.supports(module)) throw LuaException("Socket $socket is not a control module")
        if (path.isBlank()) return clearBinding(desk, rawSocket)
        val normalized = DisplayScriptCatalog.normalizePath(path) ?: throw LuaException("Invalid control script path")
        val owner = DisplayScriptCatalog.ownerFor(desk) ?: throw LuaException("No embedded computer in this console")
        if (DisplayScriptCatalog.scanControls(owner, force = true).none { it.path == normalized }) {
            throw LuaException("Control script '$normalized' was not found")
        }
        if (!DisplayBindings.set(desk, socket, DisplayBinding.LuaHandler(normalized))) {
            throw LuaException("Cannot bind control script to socket $socket")
        }
        return DisplayBindings.describe(DisplayBindings.get(desk, socket))
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
