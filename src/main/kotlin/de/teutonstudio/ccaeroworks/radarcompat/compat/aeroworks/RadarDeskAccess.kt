package de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DeskDisplayTier
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSurfaceState
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes

object RadarDeskAccess {
    @JvmStatic
    fun radarCapableType(desk: ConsoleBlockEntity, socket: Int): RadarDisplayType? {
        if (socket !in 0 until desk.socketCount()) return null
        val variant = desk.module(socket)?.let { CCModuleTypes.displayVariant(it.type()) } ?: return null
        if (variant.tier != DeskDisplayTier.ADVANCED) return null
        return RadarDisplayType.from(variant.type)
    }

    @JvmStatic
    fun radarDisplayType(desk: ConsoleBlockEntity, socket: Int): RadarDisplayType? =
        radarCapableType(desk, socket)?.takeIf {
            RadarDisplayBindings.source(DisplayBindings.get(desk, socket)) != null
        }

    @JvmStatic
    fun hasRadarCapableDisplay(desk: ConsoleBlockEntity): Boolean =
        (0 until desk.socketCount()).any { radarCapableType(desk, it) != null }

    @JvmStatic
    fun hasRadarDisplay(desk: ConsoleBlockEntity): Boolean =
        (0 until desk.socketCount()).any { radarDisplayType(desk, it) != null }

    @JvmStatic
    fun radarSurfaces(desk: ConsoleBlockEntity): List<RadarSurfaceState> {
        return (0 until desk.socketCount()).mapNotNull { socket ->
            radarDisplayType(desk, socket)?.let { type ->
                val source = RadarDisplayBindings.source(DisplayBindings.get(desk, socket))
                val snapshot = source?.let { RadarSourceRegistry.resolveSnapshot(desk, it) }
                    ?: RadarDisplaySnapshot.disconnected(desk.level?.gameTime ?: 0L)
                RadarSurfaceState(socket, type, snapshot)
            }
        }
    }
}
