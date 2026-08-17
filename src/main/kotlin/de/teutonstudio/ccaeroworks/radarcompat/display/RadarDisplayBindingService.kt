package de.teutonstudio.ccaeroworks.radarcompat.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarModuleTypes

object RadarDisplayBindingService {
    fun getRadarSources(desk: ConsoleBlockEntity): List<Map<String, Any>> =
        RadarSourceRegistry.sources(desk).map(RadarSourceDescriptor::toLua)

    @Throws(LuaException::class)
    fun setRadarSource(desk: ConsoleBlockEntity, rawSocket: Any?, sourceId: String): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        val module = desk.module(socket) ?: throw LuaException("Socket $socket is empty")
        if (RadarModuleTypes.radarDisplayType(module.type()) == null) {
            throw LuaException("Module at socket $socket is not a Radar Display")
        }
        val binding = if (
            sourceId.isBlank() || sourceId.equals("default", true) || sourceId.equals("local", true)
        ) {
            DisplayBinding.Default
        } else {
            val source = RadarSourceRegistry.find(desk, sourceId)
                ?: throw LuaException("Radar source '$sourceId' is not available in this desk network")
            RadarDisplayBindings.binding(source.key)
        }
        if (!DisplayBindings.set(desk, socket, binding)) {
            throw LuaException("Display binding is not supported at socket $socket")
        }
        return DisplayBindings.describe(binding)
    }
}
