package de.teutonstudio.ccaeroworks.radarcompat.createradar

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.ModList
import java.lang.reflect.Modifier

/**
 * Read-only topology query shared by the ComputerControlDesk information-source page and the
 * computer-routed radar snapshot path.
 */
object RadarNetworkControllerLookup {
    private const val NETWORK_DATA_CLASS = "com.happysg.radar.block.behavior.networks.NetworkData"
    private val NETWORK_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")

    /**
     * Finds the Create: Radars groups exposed to the one embedded computer in this desk network.
     *
     * A controller can be exposed either by Create: Radars' authoritative endpoint index or by
     * placing the Network Filterer directly against any member of the active console row. The
     * latter is the computer-routed path: the filterer still has to own a normal native radar
     * link, but a second monitor Data Link is no longer required.
     */
    fun controllersFor(desk: ConsoleBlockEntity, knownNetworkData: Any? = null): List<BlockPos> {
        if (!ModList.get().isLoaded(CreateRadarCompat.MOD_ID)) return emptyList()
        val level = desk.level as? ServerLevel ?: return emptyList()
        val network = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        if (network.state != ConsoleNetworkState.ACTIVE || network.owner == null) return emptyList()

        return runCatching {
            val data = knownNetworkData ?: run {
                val clazz = Class.forName(NETWORK_DATA_CLASS)
                val get = clazz.methods.firstOrNull { method ->
                    method.name == "get" && Modifier.isStatic(method.modifiers) && method.parameterCount == 1
                } ?: return@runCatching emptyList()
                get.invoke(null, level) ?: return@runCatching emptyList()
            }
            val lookup = data.javaClass.methods.firstOrNull { method ->
                method.name == "getFiltererForEndpoint" && method.parameterCount == 2
            } ?: return@runCatching emptyList()

            buildSet {
                network.segmentPositions.forEach { segmentPos ->
                    (lookup.invoke(data, level.dimension(), segmentPos) as? BlockPos)?.let { add(it.immutable()) }
                    Direction.entries.forEach { direction ->
                        val candidate = segmentPos.relative(direction)
                        if (!level.isLoaded(candidate)) return@forEach
                        if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(candidate).block) == NETWORK_CONTROLLER_ID) {
                            add(candidate.immutable())
                        }
                    }
                }
            }.sortedWith(compareBy<BlockPos>({ it.x }, { it.y }, { it.z }))
        }.getOrDefault(emptyList())
    }
}

internal data class RadarControllerRoute(
    val filtererPos: BlockPos?,
    val nativeMonitorEndpoint: Boolean,
    val ambiguous: Boolean
)

/** Native monitor registration wins; otherwise the embedded computer may route one controller. */
internal fun selectRadarControllerRoute(
    localFilterer: BlockPos?,
    localIsMonitorEndpoint: Boolean,
    computerControllers: Collection<BlockPos>
): RadarControllerRoute {
    if (localFilterer != null && localIsMonitorEndpoint) {
        return RadarControllerRoute(localFilterer, nativeMonitorEndpoint = true, ambiguous = false)
    }

    val controllers = computerControllers.distinct()
    if (localFilterer != null && localFilterer in controllers) {
        return RadarControllerRoute(localFilterer, nativeMonitorEndpoint = false, ambiguous = false)
    }
    return when (controllers.size) {
        0 -> RadarControllerRoute(null, nativeMonitorEndpoint = false, ambiguous = false)
        1 -> RadarControllerRoute(controllers.single(), nativeMonitorEndpoint = false, ambiguous = false)
        else -> RadarControllerRoute(null, nativeMonitorEndpoint = false, ambiguous = true)
    }
}
