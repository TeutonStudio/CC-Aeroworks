package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.computer.DisplayBindingEvents
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes

object DisplayBindingService {
    fun getRadarSources(desk: ConsoleBlockEntity): List<Map<String, Any>> =
        RadarSourceRegistry.sources(desk).map(RadarSourceDescriptor::toLua)

    @Throws(LuaException::class)
    fun getBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        return DisplayBindings.describe(DisplayBindings.get(desk, socket))
    }

    @Throws(LuaException::class)
    fun setRadarSource(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        sourceId: String
    ): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val module = desk.module(socket) ?: throw LuaException("Socket $socket is empty")
        if (CCModuleTypes.radarDisplayType(module.type()) == null) {
            throw LuaException("Module at socket $socket is not a Radar Display")
        }

        val content = if (sourceId.isBlank() || sourceId.equals("default", ignoreCase = true) ||
            sourceId.equals("local", ignoreCase = true)
        ) {
            DisplayContentSource.Default
        } else {
            val source = RadarSourceRegistry.find(desk, sourceId)
                ?: throw LuaException("Radar source '$sourceId' is not available in this desk network")
            DisplayContentSource.RadarSource(source.key)
        }

        if (!DisplayBindings.setContent(desk, socket, content)) {
            throw LuaException("Display content source is not supported at socket $socket")
        }
        DisplayBindingEvents.notifyChanged(desk, socket)
        return DisplayBindings.describe(DisplayBindings.get(desk, socket))
    }

    @Throws(LuaException::class)
    fun setScriptSource(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        path: String
    ): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val normalized = path.trim()
        val content = if (normalized.isEmpty()) {
            DisplayContentSource.Default
        } else {
            if (normalized.length > DisplayBindings.MAX_SCRIPT_PATH_LENGTH) {
                throw LuaException("Display source script path is too long")
            }
            DisplayContentSource.ScriptSource(normalized)
        }
        if (!DisplayBindings.setContent(desk, socket, content)) {
            throw LuaException("Script sources are supported only by the large Desk Display")
        }
        DisplayBindingEvents.notifyChanged(desk, socket)
        return DisplayBindings.describe(DisplayBindings.get(desk, socket))
    }

    @Throws(LuaException::class)
    fun setTouchScript(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        path: String
    ): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val normalized = path.trim()
        val input = if (normalized.isEmpty()) {
            DisplayInputBinding.Raw
        } else {
            if (normalized.length > DisplayBindings.MAX_HANDLER_PATH_LENGTH) {
                throw LuaException("Touch handler path is too long")
            }
            DisplayInputBinding.LuaHandler(normalized)
        }
        if (!DisplayBindings.setInput(desk, socket, input)) {
            throw LuaException("Touch scripts are supported only by large interactive displays")
        }
        DisplayBindingEvents.notifyChanged(desk, socket)
        return DisplayBindings.describe(DisplayBindings.get(desk, socket))
    }

    @Throws(LuaException::class)
    fun clearBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        if (!DisplayBindings.clear(desk, socket)) {
            throw LuaException("Socket $socket is invalid")
        }
        DisplayBindingEvents.notifyChanged(desk, socket)
        return DisplayBindings.describe(DisplayBinding())
    }
}
