package de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.access.RadarDeskStateAccess
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSurfaceState
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarModuleTypes

object RadarDeskAccess {
    @JvmStatic
    fun radarDisplayType(desk: ConsoleBlockEntity, socket: Int): RadarDisplayType? =
        if (socket in 0 until desk.socketCount()) desk.module(socket)?.let { RadarModuleTypes.radarDisplayType(it.type()) } else null

    @JvmStatic
    fun hasRadarDisplay(desk: ConsoleBlockEntity): Boolean =
        (0 until desk.socketCount()).any { radarDisplayType(desk, it) != null }

    @JvmStatic
    fun radarSurfaces(desk: ConsoleBlockEntity): List<RadarSurfaceState> {
        val localSnapshot = (desk as? RadarDeskStateAccess)?.ccaeroworks_getRadarSnapshot()
        return (0 until desk.socketCount()).mapNotNull { socket ->
            radarDisplayType(desk, socket)?.let { type ->
                val source = RadarDisplayBindings.source(DisplayBindings.get(desk, socket))
                val snapshot = source?.let { RadarSourceRegistry.resolveSnapshot(desk, it) }
                    ?: if (source != null) RadarDisplaySnapshot.disconnected(desk.level?.gameTime ?: 0L) else localSnapshot
                RadarSurfaceState(socket, type, snapshot)
            }
        }
    }
}
