package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.ModList
import java.lang.reflect.Modifier

/** Lightweight optional-mod topology query used by the I/O information-source browser. */
object RadarNetworkTopology {
    private const val NETWORK_DATA_CLASS = "com.happysg.radar.block.behavior.networks.NetworkData"

    fun filtererForEndpoint(desk: ConsoleBlockEntity): BlockPos? {
        if (!ModList.get().isLoaded(CreateRadarCompat.MOD_ID)) return null
        val level = desk.level as? ServerLevel ?: return null
        return runCatching {
            val type = Class.forName(NETWORK_DATA_CLASS)
            val get = type.methods.firstOrNull { method ->
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
