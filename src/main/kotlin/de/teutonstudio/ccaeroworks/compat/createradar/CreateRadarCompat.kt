package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.display.RadarLinkStatus
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import java.lang.reflect.InvocationTargetException
import java.util.WeakHashMap

object CreateRadarCompat {
    const val MOD_ID: String = "create_radar"

    private const val NETWORK_CONTROLLER_CLASS: String =
        "com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity"
    private const val NETWORK_CONTROLLER_BLOCK_ID: String = "create_radar:network_filterer"
    private const val SNAPSHOT_INTERVAL_TICKS: Long = 5L
    private const val SNAPSHOT_HEARTBEAT_TICKS: Long = 15L

    private val lastStatusByController = WeakHashMap<BlockEntity, RadarLinkStatus>()

    @JvmStatic
    fun refreshController(controller: Any) {
        if (!ModList.get().isLoaded(MOD_ID)) return
        val controllerEntity = controller as? BlockEntity ?: return
        val level = controllerEntity.level as? ServerLevel ?: return
        val gameTime = level.gameTime
        if (Math.floorMod(gameTime + controllerEntity.blockPos.asLong(), SNAPSHOT_INTERVAL_TICKS) != 0L) return

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

        val snapshot = when {
            !isRoutable(network.state) -> RadarDisplaySnapshot.disconnected(
                Vec3.atCenterOf(updateOwner.blockPos),
                level.gameTime,
                RadarLinkStatus.NETWORK_UNAVAILABLE
            )

            controllers.size > 1 -> RadarDisplaySnapshot.disconnected(
                Vec3.atCenterOf(updateOwner.blockPos),
                level.gameTime,
                RadarLinkStatus.MULTIPLE_CONTROLLERS
            )

            else -> readControllerSnapshot(level, updateOwner)
        }

        logStatusTransition(updateOwner, network, controllers.size, snapshot.status)

        network.desks
            .filter(AeroworksDeskAccess::hasRadarDisplay)
            .forEach { destination ->
                val access = destination as? RadarDeskStateAccess ?: return@forEach
                val previous = access.ccaeroworks_getRadarSnapshot()
                if (!shouldSynchronize(previous, snapshot, level.gameTime)) return@forEach
                access.ccaeroworks_setRadarSnapshot(snapshot)
                destination.notifyUpdate()
            }
    }

    private fun shouldSynchronize(
        previous: RadarDisplaySnapshot?,
        next: RadarDisplaySnapshot,
        gameTime: Long
    ): Boolean {
        if (previous == null || previous.contentHash() != next.contentHash()) return true
        val age = gameTime - previous.updatedAt
        return age !in 0 until SNAPSHOT_HEARTBEAT_TICKS
    }

    private fun logStatusTransition(
        controller: BlockEntity,
        network: RadarDeskNetwork,
        controllerCount: Int,
        status: RadarLinkStatus
    ) {
        val previous = lastStatusByController.put(controller, status)
        if (previous == status) return
        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Radar link at {} changed from {} to {} (network={}, controllers={}, desks={})",
            controller.blockPos,
            previous ?: "UNSEEN",
            status,
            network.state,
            controllerCount,
            network.desks.size
        )
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
        val radar = when (val resolution = resolveRadar(level, controller)) {
            is RadarResolution.Found -> resolution.radar
            is RadarResolution.Failure -> {
                resolution.cause?.let {
                    CCAeroworks.LOGGER.debug(
                        "[CC-Aeroworks] Create: Radars access failed at {} during {}",
                        controller.blockPos,
                        resolution.stage,
                        it
                    )
                }
                return RadarDisplaySnapshot.disconnected(
                    fallbackCenter,
                    level.gameTime,
                    resolution.status
                )
            }
        }

        val center = when (val worldPosition = invokeLookup(radar, "getWorldPos").value) {
            is BlockPos -> Vec3.atCenterOf(worldPosition)
            is Vec3 -> worldPosition
            else -> fallbackCenter
        }

        val rangeLookup = invokeLookup(radar, "getRange")
        val runningLookup = invokeLookup(radar, "isRunning")
        if (
            !rangeLookup.found || rangeLookup.error != null ||
            !runningLookup.found || runningLookup.error != null
        ) {
            return RadarDisplaySnapshot.disconnected(
                center,
                level.gameTime,
                RadarLinkStatus.API_INCOMPATIBLE
            )
        }

        val range = (rangeLookup.value as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
        if (runningLookup.value != true) {
            return RadarDisplaySnapshot.disconnected(
                center,
                level.gameTime,
                RadarLinkStatus.RADAR_NOT_RUNNING
            )
        }
        if (range <= 0.0) {
            return RadarDisplaySnapshot.disconnected(
                center,
                level.gameTime,
                RadarLinkStatus.INVALID_RANGE
            )
        }

        val selected = readFieldLookup(controller, "activeTrackCache").value
            ?.let { invokeAny(it, "getId", "id") as? String }
        val tracks = readTracks(radar, center)

        return RadarDisplaySnapshot(
            connected = true,
            center = center,
            range = range,
            selectedTrackId = selected,
            tracks = tracks,
            updatedAt = level.gameTime,
            status = RadarLinkStatus.ACTIVE
        )
    }

    private fun resolveRadar(level: ServerLevel, controller: BlockEntity): RadarResolution {
        var apiSurfaceFound = false

        val methodLookup = invokeDeclaredLookup(controller, "getRadar", level)
        apiSurfaceFound = apiSurfaceFound || methodLookup.found
        methodLookup.error?.let {
            return RadarResolution.Failure(RadarLinkStatus.API_INCOMPATIBLE, "getRadar", it)
        }
        methodLookup.value?.let { return RadarResolution.Found(it) }

        val radarCache = readFieldLookup(controller, "radarCache")
        apiSurfaceFound = apiSurfaceFound || radarCache.found
        radarCache.error?.let {
            return RadarResolution.Failure(RadarLinkStatus.API_INCOMPATIBLE, "radarCache", it)
        }
        radarCache.value?.let { return RadarResolution.Found(it) }

        val radarPosition = readFieldLookup(controller, "radarPosCache")
        apiSurfaceFound = apiSurfaceFound || radarPosition.found
        radarPosition.error?.let {
            return RadarResolution.Failure(RadarLinkStatus.API_INCOMPATIBLE, "radarPosCache", it)
        }
        val position = radarPosition.value as? BlockPos
        if (position != null) {
            if (!level.isLoaded(position)) {
                return RadarResolution.Failure(RadarLinkStatus.RADAR_NOT_LOADED, "radarPosCache")
            }
            val radar = level.getBlockEntity(position)
                ?: return RadarResolution.Failure(RadarLinkStatus.RADAR_NOT_LOADED, "radarPosCache")
            return RadarResolution.Found(radar)
        }

        return if (apiSurfaceFound) {
            RadarResolution.Failure(RadarLinkStatus.RADAR_NOT_LINKED, "controller cache")
        } else {
            RadarResolution.Failure(RadarLinkStatus.API_INCOMPATIBLE, "controller API")
        }
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
        val values = invokeLookup(radar, "getTracks").value as? Iterable<*> ?: return emptyList()
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
            val lookup = invokeLookup(instance, methodName)
            if (lookup.found && lookup.error == null && lookup.value != null) return lookup.value
        }
        return null
    }

    private fun invokeLookup(instance: Any, methodName: String): ReflectionLookup {
        val method = runCatching { instance.javaClass.getMethod(methodName) }
            .getOrElse { return ReflectionLookup(found = false) }
        return try {
            ReflectionLookup(found = true, value = method.invoke(instance))
        } catch (exception: Throwable) {
            ReflectionLookup(found = true, error = unwrapInvocationException(exception))
        }
    }

    private fun invokeDeclaredLookup(
        instance: Any,
        methodName: String,
        vararg arguments: Any
    ): ReflectionLookup {
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
                return try {
                    if (!method.trySetAccessible()) {
                        return ReflectionLookup(
                            found = true,
                            error = IllegalAccessException("Cannot access ${type.name}#$methodName")
                        )
                    }
                    ReflectionLookup(found = true, value = method.invoke(instance, *arguments))
                } catch (exception: Throwable) {
                    ReflectionLookup(found = true, error = unwrapInvocationException(exception))
                }
            }
            current = type.superclass
        }
        return ReflectionLookup(found = false)
    }

    private fun readFieldLookup(instance: Any, fieldName: String): ReflectionLookup {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val type = current
            val field = runCatching { type.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                return try {
                    if (!field.trySetAccessible()) {
                        return ReflectionLookup(
                            found = true,
                            error = IllegalAccessException("Cannot access ${type.name}#$fieldName")
                        )
                    }
                    ReflectionLookup(found = true, value = field.get(instance))
                } catch (exception: Throwable) {
                    ReflectionLookup(found = true, error = unwrapInvocationException(exception))
                }
            }
            current = type.superclass
        }
        return ReflectionLookup(found = false)
    }

    private fun unwrapInvocationException(exception: Throwable): Throwable =
        if (exception is InvocationTargetException) exception.targetException ?: exception else exception

    private fun hasClass(value: Any, className: String): Boolean {
        var current: Class<*>? = value.javaClass
        while (current != null) {
            val type = current
            if (type.name == className) return true
            current = type.superclass
        }
        return false
    }

    private data class ReflectionLookup(
        val found: Boolean,
        val value: Any? = null,
        val error: Throwable? = null
    )

    private sealed interface RadarResolution {
        data class Found(val radar: Any) : RadarResolution
        data class Failure(
            val status: RadarLinkStatus,
            val stage: String,
            val cause: Throwable? = null
        ) : RadarResolution
    }

    private data class RadarDeskNetwork(
        val state: ConsoleNetworkState,
        val desks: List<ConsoleBlockEntity>
    )
}
