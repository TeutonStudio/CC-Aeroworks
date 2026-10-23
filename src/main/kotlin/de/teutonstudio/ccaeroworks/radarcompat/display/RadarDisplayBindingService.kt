package de.teutonstudio.ccaeroworks.radarcompat.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.lua.LuaException
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks.RadarDeskAccess

object RadarDisplayBindingService {
    fun getRadarSources(desk: ConsoleBlockEntity): List<Map<String, Any>> =
        RadarSourceRegistry.sources(desk).map(RadarSourceDescriptor::toLua)

    @Throws(LuaException::class)
    fun setRadarSource(desk: ConsoleBlockEntity, rawSocket: Any?, sourceId: String): Map<String, Any> {
        val socket = AeroworksDeskService.parseSocket(desk, rawSocket)
        if (desk.module(socket) == null) throw LuaException("Socket $socket is empty")
        if (RadarDeskAccess.radarCapableType(desk, socket) == null) {
            throw LuaException("Module at socket $socket is not an advanced display")
        }
        val binding = if (sourceId.isBlank() || sourceId.equals("default", true)) {
            DisplayBinding.Default
        } else {
            val source = if (sourceId.equals("local", true)) {
                RadarSourceRegistry.sources(desk).firstOrNull { it.ingressPos == desk.blockPos }
            } else {
                RadarSourceRegistry.find(desk, sourceId)
            }
                ?: throw LuaException("Radar source '$sourceId' is not available in this desk network")
            RadarDisplayBindings.binding(source.key)
        }
        if (!DisplayBindings.set(desk, socket, binding)) {
            throw LuaException("Display binding is not supported at socket $socket")
        }
        return DisplayBindings.describe(binding)
    }
}
