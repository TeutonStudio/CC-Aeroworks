package de.teutonstudio.ccaeroworks.radarcompat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.ModList
import java.lang.reflect.Modifier

/**
 * Small read-only topology query for the ComputerControlDesk information-source page. It uses the
 * same authoritative Create: Radars NetworkData endpoint relation as CreateRadarCompat and never
 * scans the world for controller blocks.
 */
object RadarNetworkControllerLookup {
    private const val NETWORK_DATA_CLASS = "com.happysg.radar.block.behavior.networks.NetworkData"

    fun controllerFor(desk: ConsoleBlockEntity): BlockPos? {
        if (!ModList.get().isLoaded(CreateRadarCompat.MOD_ID)) return null
        val level = desk.level as? ServerLevel ?: return null
        return runCatching {
            val clazz = Class.forName(NETWORK_DATA_CLASS)
            val get = clazz.methods.firstOrNull { method ->
                method.name == "get" && Modifier.isStatic(method.modifiers) && method.parameterCount == 1
            } ?: return@runCatching null
            val data = get.invoke(null, level) ?: return@runCatching null
            val lookup = data.javaClass.methods.firstOrNull { method ->
                method.name == "getFiltererForEndpoint" && method.parameterCount == 2
            } ?: return@runCatching null
            lookup.invoke(data, level.dimension(), desk.blockPos) as? BlockPos
        }.getOrNull()
    }
}
