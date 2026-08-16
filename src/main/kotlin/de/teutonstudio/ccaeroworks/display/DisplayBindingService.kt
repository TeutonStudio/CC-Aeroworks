package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
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

        val binding = if (sourceId.isBlank() || sourceId.equals("default", ignoreCase = true) ||
            sourceId.equals("local", ignoreCase = true)
        ) {
            DisplayBinding.Default
        } else {
            val source = RadarSourceRegistry.find(desk, sourceId)
                ?: throw LuaException("Radar source '$sourceId' is not available in this desk network")
            DisplayBinding.RadarSource(source.key)
        }

        if (!DisplayBindings.set(desk, socket, binding)) {
            throw LuaException("Display binding is not supported at socket $socket")
        }
        return DisplayBindings.describe(binding)
    }

    /** Legacy API name. It updates only the controller path and preserves the boot program. */
    @Throws(LuaException::class)
    fun setTouchScript(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        path: String
    ): Map<String, Any> = setController(desk, rawSocket, path)

    @Throws(LuaException::class)
    fun setController(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        path: String
    ): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val normalized = normalizeOptionalPath(path, "Controller")
        val current = DisplayBindings.get(desk, socket)
        return setApplicationBinding(
            desk,
            socket,
            normalized,
            DisplayBindings.bootProgramPath(current)
        )
    }

    @Throws(LuaException::class)
    fun setBootProgram(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        path: String
    ): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val normalized = normalizeOptionalPath(path, "Boot program")
        val current = DisplayBindings.get(desk, socket)
        return setApplicationBinding(
            desk,
            socket,
            DisplayBindings.controllerPath(current),
            normalized
        )
    }

    @Throws(LuaException::class)
    fun setApplication(
        desk: ConsoleBlockEntity,
        rawSocket: Any?,
        controllerPath: String,
        bootProgramPath: String
    ): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        return setApplicationBinding(
            desk,
            socket,
            normalizeOptionalPath(controllerPath, "Controller"),
            normalizeOptionalPath(bootProgramPath, "Boot program")
        )
    }

    @Throws(LuaException::class)
    fun clearBinding(desk: ConsoleBlockEntity, rawSocket: Any?): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        if (!DisplayBindings.clear(desk, socket)) {
            throw LuaException("Socket $socket is invalid")
        }
        return DisplayBindings.describe(DisplayBinding.Default)
    }

    private fun setApplicationBinding(
        desk: ConsoleBlockEntity,
        socket: Int,
        controllerPath: String,
        bootProgramPath: String
    ): Map<String, Any> {
        val binding = when {
            controllerPath.isEmpty() && bootProgramPath.isEmpty() -> DisplayBinding.Default
            bootProgramPath.isEmpty() -> DisplayBinding.LuaHandler(controllerPath)
            else -> DisplayBinding.LuaApplication(controllerPath, bootProgramPath)
        }
        if (!DisplayBindings.set(desk, socket, binding)) {
            throw LuaException("Display applications are supported only by the large Desk Display")
        }
        return DisplayBindings.describe(binding)
    }

    private fun normalizeOptionalPath(path: String, label: String): String {
        val normalized = path.trim()
        if (normalized.length > DisplayBindings.MAX_HANDLER_PATH_LENGTH) {
            throw LuaException("$label path is too long")
        }
        return normalized
    }
}
