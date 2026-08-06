package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList

object CreateRadarCompat {
    const val MOD_ID: String = "create_radar"

    private const val NETWORK_CONTROLLER_CLASS: String =
        "com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity"
    private const val NETWORK_CONTROLLER_BLOCK_ID: String = "create_radar:network_filterer"

    @JvmStatic
    fun refreshController(controller: Any) {
        if (!ModList.get().isLoaded(MOD_ID)) return
        val controllerEntity = controller as? BlockEntity ?: return
        val level = controllerEntity.level as? ServerLevel ?: return

        val networks = adjacentDeskNetworks(level, controllerEntity.blockPos)
        networks.forEach { network ->
            refreshNetwork(level, controllerEntity, network)
        }
    }

    private fun adjacentDeskNetworks(
        level: ServerLevel,
        controllerPos: BlockPos
    ): List<RadarDeskNetwork> {
        val networks = linkedMapOf<List<Long>, RadarDeskNetwork>()
        for (direction in Direction.values()) {
            val desk = level.getBlockEntity(controllerPos.relative(direction)) as? ConsoleBlockEntity ?: continue
            val network = resolveDeskNetwork(desk)
            val key = network.desks.map { it.blockPos.asLong() }.sorted()
            networks.putIfAbsent(key, network)
        }
        return networks.values.toList()
    }

    private fun refreshNetwork(
        level: ServerLevel,
        tickingController: BlockEntity,
        network: RadarDeskNetwork
    ) {
        val controllers = findAdjacentControllers(level, network.desks).toMutableList()
        if (controllers.none { it.blockPos == tickingController.blockPos }) {
            controllers += tickingController
        }

        val updateOwner = controllers.minByOrNull { it.blockPos.asLong() } ?: return
        if (updateOwner.blockPos != tickingController.blockPos) return

        val snapshot = if (isRoutable(network.state) && controllers.size == 1) {
            readControllerSnapshot(level, updateOwner)
        } else {
            RadarDisplaySnapshot.disconnected(
                Vec3.atCenterOf(tickingController.blockPos),
                level.getGameTime()
            )
        }

        val detectedDestinations = network.desks.filter(AeroworksDeskAccess::hasRadarDisplay)
        val destinations = detectedDestinations.ifEmpty { network.desks }
        destinations.forEach { destination ->
            val access = destination as? RadarDeskStateAccess ?: return@forEach
            access.ccaeroworks_setRadarSnapshot(snapshot)
            destination.notifyUpdate()
        }
    }

    private fun findAdjacentControllers(
        level: ServerLevel,
        desks: List<ConsoleBlockEntity>
    ): List<BlockEntity> {
        val controllers = linkedMapOf<BlockPos, BlockEntity>()
        for (desk in desks) {
            for (direction in Direction.values()) {
                val position = desk.blockPos.relative(direction)
                if (!level.isLoaded(position)) continue
                val candidate = level.getBlockEntity(position) ?: continue
                if (isNetworkController(candidate)) {
                    controllers.putIfAbsent(candidate.blockPos, candidate)
                }
            }
        }
        return controllers.values.toList()
    }

    private fun isNetworkController(candidate: BlockEntity): Boolean {
        if (hasClass(candidate, NETWORK_CONTROLLER_CLASS)) return true
        val blockId = BuiltInRegistries.BLOCK.getKey(candidate.blockState.block).toString()
        return blockId == NETWORK_CONTROLLER_BLOCK_ID
    }

    private fun readControllerSnapshot(
        level: ServerLevel,
        controller: BlockEntity
    ): RadarDisplaySnapshot {
        val fallbackCenter = Vec3.atCenterOf(controller.blockPos)
        val radar = invokeDeclared(controller, "getRadar", level)
            ?: readField(controller, "radarCache")
            ?: (readField(controller, "radarPosCache") as? BlockPos)
                ?.takeIf(level::isLoaded)
                ?.let(level::getBlockEntity)
            ?: return RadarDisplaySnapshot.disconnected(fallbackCenter, level.getGameTime())

        val center = when (val worldPosition = invoke(radar, "getWorldPos")) {
            is BlockPos -> Vec3.atCenterOf(worldPosition)
            is Vec3 -> worldPosition
            else -> fallbackCenter
        }
        val range = (invoke(radar, "getRange") as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
        val connected = (invoke(radar, "isRunning") as? Boolean) == true && range > 0.0
        val selected = readField(controller, "activeTrackCache")
            ?.let { invokeAny(it, "getId", "id") as? String }
        val tracks = if (connected) readTracks(radar, center) else emptyList()

        return RadarDisplaySnapshot(
            connected = connected,
            center = center,
            range = range,
            selectedTrackId = selected,
            tracks = tracks,
            updatedAt = level.getGameTime()
        )
    }

    private fun resolveDeskNetwork(sourceDesk: ConsoleBlockEntity): RadarDeskNetwork {
        val level = sourceDesk.level ?: return RadarDeskNetwork(ConsoleNetworkState.NONE, listOf(sourceDesk))
        val network = ConsoleMultiblockManager.resolve(level, sourceDesk.blockPos)
        val desks = network.members.map { it.desk }.ifEmpty { listOf(sourceDesk) }
        return RadarDeskNetwork(network.state, desks)
    }

    private fun isRoutable(state: ConsoleNetworkState): Boolean =
        state == ConsoleNetworkState.ACTIVE || state == ConsoleNetworkState.NONE

    private fun readTracks(radar: Any, center: Vec3): List<RadarDisplayTrack> {
        val values = invoke(radar, "getTracks") as? Iterable<*> ?: return emptyList()
        return values.mapNotNull { raw ->
            raw ?: return@mapNotNull null
            val id = (invokeAny(raw, "getId", "id") as? String)
                ?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            val position = invokeAny(raw, "getPosition", "position") as? Vec3 ?: return@mapNotNull null
            val velocity = invokeAny(raw, "getVelocity", "velocity") as? Vec3 ?: Vec3.ZERO
            val category = invokeAny(raw, "getTrackCategory", "trackCategory")
            RadarDisplayTrack(
                id = id,
                position = position,
                velocity = velocity,
                sprite = RadarDisplayTrackSprite.fromCategory(category)
            )
        }
            .sortedBy { it.position.distanceToSqr(center) }
            .take(RadarDisplaySnapshot.MAX_SYNCED_TRACKS)
    }

    private fun invokeAny(instance: Any, vararg methodNames: String): Any? {
        for (methodName in methodNames) {
            invoke(instance, methodName)?.let { return it }
        }
        return null
    }

    private fun invoke(instance: Any, methodName: String): Any? = runCatching {
        instance.javaClass.getMethod(methodName).invoke(instance)
    }.getOrNull()

    private fun invokeDeclared(instance: Any, methodName: String, vararg arguments: Any): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val type = current
            val method = type.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.parameterCount == arguments.size &&
                    candidate.parameterTypes.zip(arguments).all { (parameterType, argument) ->
                        parameterType.isAssignableFrom(argument.javaClass)
                    }
            }
            if (method != null) {
                return runCatching {
                    method.trySetAccessible()
                    method.invoke(instance, *arguments)
                }.getOrNull()
            }
            current = type.superclass
        }
        return null
    }

    private fun readField(instance: Any, fieldName: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val type = current
            val field = runCatching { type.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.trySetAccessible()
                    field.get(instance)
                }.getOrNull()
            }
            current = type.superclass
        }
        return null
    }

    private fun hasClass(value: Any, className: String): Boolean {
        var current: Class<*>? = value.javaClass
        while (current != null) {
            val type = current
            if (type.name == className) return true
            current = type.superclass
        }
        return false
    }

    private data class RadarDeskNetwork(
        val state: ConsoleNetworkState,
        val desks: List<ConsoleBlockEntity>
    )
}
