package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.display.RadarLinkStatus
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

object CreateRadarCompat {
    const val MOD_ID: String = "create_radar"

    private const val NETWORK_DATA_CLASS =
        "com.happysg.radar.block.behavior.networks.NetworkData"
    private const val DETECTION_CONFIG_CLASS =
        "com.happysg.radar.block.behavior.networks.config.DetectionConfig"
    private const val PHYSICS_HANDLER_CLASS =
        "com.happysg.radar.compat.vs2.PhysicsHandler"
    private const val NATIVE_MONITOR_INTERVAL_TICKS = 5L
    private const val SNAPSHOT_HEARTBEAT_TICKS = 15L

    private val lastDiagnosticByDesk = WeakHashMap<ConsoleBlockEntity, DiagnosticState>()
    private val lastFailureByDesk = WeakHashMap<ConsoleBlockEntity, String>()

    /**
     * Runs from ConsoleBlockEntity.tick at the same global five-tick phase as
     * Create: Radars' MonitorBlockEntity. NetworkData remains authoritative.
     */
    @JvmStatic
    fun refreshDesk(desk: ConsoleBlockEntity) {
        if (!ModList.get().isLoaded(MOD_ID)) return
        val level = desk.level as? ServerLevel ?: return
        if (level.gameTime % NATIVE_MONITOR_INTERVAL_TICKS != 0L) return

        val state = desk as? RadarDeskStateAccess ?: return
        if (!AeroworksDeskAccess.hasRadarDisplay(desk)) {
            lastDiagnosticByDesk.remove(desk)
            lastFailureByDesk.remove(desk)
            if (state.ccaeroworks_getRadarSnapshot() != null) {
                state.ccaeroworks_setRadarSnapshot(null)
                desk.notifyUpdate()
            }
            return
        }

        val result = resolveSnapshot(level, desk)
        logDiagnosticTransition(desk, result)

        val previous = state.ccaeroworks_getRadarSnapshot()
        val heartbeatDue = previous == null ||
            level.gameTime - previous.updatedAt !in 0 until SNAPSHOT_HEARTBEAT_TICKS
        if (result.snapshot.sameContentAs(previous) && !heartbeatDue) return

        state.ccaeroworks_setRadarSnapshot(result.snapshot)
        desk.notifyUpdate()
    }

    private fun resolveSnapshot(
        level: ServerLevel,
        desk: ConsoleBlockEntity
    ): SnapshotResult {
        val fallbackCenter = Vec3.atCenterOf(desk.blockPos)
        return try {
            val networkData = invokeStatic(NETWORK_DATA_CLASS, "get", level)
                ?: throw IllegalStateException("NetworkData.get returned null")

            val filtererPos = invokePublic(
                networkData,
                "getFiltererForEndpoint",
                level.dimension(),
                desk.blockPos
            ) as? BlockPos ?: return SnapshotResult(
                snapshot = RadarDisplaySnapshot.disconnected(
                    fallbackCenter,
                    level.gameTime,
                    RadarLinkStatus.NOT_LINKED
                ),
                filtererPos = null,
                radarPos = null,
                reason = "endpoint is not registered"
            )

            val group = invokePublic(
                networkData,
                "getGroup",
                level.dimension(),
                filtererPos
            ) ?: return SnapshotResult(
                snapshot = RadarDisplaySnapshot.disconnected(
                    fallbackCenter,
                    level.gameTime,
                    RadarLinkStatus.NOT_LINKED
                ),
                filtererPos = filtererPos,
                radarPos = null,
                reason = "registered filterer group is missing"
            )

            val monitorEndpoints = readField(group, "monitorEndpoints") as? Collection<*>
                ?: throw IllegalStateException("NetworkData.Group.monitorEndpoints is not a collection")
            if (desk.blockPos !in monitorEndpoints) {
                return SnapshotResult(
                    snapshot = RadarDisplaySnapshot.disconnected(
                        fallbackCenter,
                        level.gameTime,
                        RadarLinkStatus.NOT_LINKED
                    ),
                    filtererPos = filtererPos,
                    radarPos = null,
                    reason = "group does not contain the desk endpoint"
                )
            }

            val detectionTag = (readField(group, "detectionTag") as? CompoundTag)?.copy()
                ?: throw IllegalStateException("NetworkData.Group.detectionTag is unavailable")
            val selectedTargetId = readField(group, "selectedTargetId") as? String
            val radarPos = readField(group, "radarPos") as? BlockPos
                ?: return SnapshotResult(
                    snapshot = inactiveSnapshot(
                        center = fallbackCenter,
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_NOT_ASSIGNED,
                        radarPos = null,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId
                    ),
                    filtererPos = filtererPos,
                    radarPos = null,
                    reason = "group has no radarPos"
                )

            if (!level.isLoaded(radarPos)) {
                return SnapshotResult(
                    snapshot = inactiveSnapshot(
                        center = fallbackCenter,
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_NOT_LOADED,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId
                    ),
                    filtererPos = filtererPos,
                    radarPos = radarPos,
                    reason = "radar chunk is not loaded"
                )
            }

            val radar = level.getBlockEntity(radarPos)
                ?: return SnapshotResult(
                    snapshot = inactiveSnapshot(
                        center = fallbackCenter,
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_NOT_LOADED,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId
                    ),
                    filtererPos = filtererPos,
                    radarPos = radarPos,
                    reason = "radar block entity is unavailable"
                )

            val center = invokeStatic(PHYSICS_HANDLER_CLASS, "getWorldVec", level, radarPos) as? Vec3
                ?: throw IllegalStateException("PhysicsHandler.getWorldVec returned no Vec3")
            val range = (invokePublic(radar, "getRange") as? Number)
                ?.toDouble()
                ?.coerceAtLeast(0.0)
                ?: throw IllegalStateException("IRadar.getRange returned no number")
            val running = invokePublic(radar, "isRunning") as? Boolean
                ?: throw IllegalStateException("IRadar.isRunning returned no boolean")

            if (!running) {
                return SnapshotResult(
                    snapshot = inactiveSnapshot(
                        center = center,
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_STOPPED,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId,
                        range = range,
                        connected = true
                    ),
                    filtererPos = filtererPos,
                    radarPos = radarPos,
                    reason = "IRadar.isRunning is false"
                )
            }
            if (range <= 0.0) {
                return SnapshotResult(
                    snapshot = inactiveSnapshot(
                        center = center,
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.INVALID_RANGE,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId,
                        range = range,
                        connected = true
                    ),
                    filtererPos = filtererPos,
                    radarPos = radarPos,
                    reason = "IRadar.getRange is not positive"
                )
            }

            val filter = invokeStatic(DETECTION_CONFIG_CLASS, "fromTag", detectionTag.copy())
                ?: throw IllegalStateException("DetectionConfig.fromTag returned null")
            val rawTracks = invokePublic(radar, "getTracks") as? Iterable<*>
                ?: throw IllegalStateException("IRadar.getTracks returned no iterable")
            val tracks = readFilteredTracks(filter, rawTracks, center)

            SnapshotResult(
                snapshot = RadarDisplaySnapshot(
                    connected = true,
                    operational = true,
                    radarPos = radarPos,
                    center = center,
                    range = range,
                    detectionTag = detectionTag,
                    selectedTrackId = selectedTargetId,
                    tracks = tracks,
                    updatedAt = level.gameTime,
                    status = RadarLinkStatus.ACTIVE
                ),
                filtererPos = filtererPos,
                radarPos = radarPos,
                reason = "endpoint registered and native radar state synchronized"
            )
        } catch (throwable: Throwable) {
            val cause = unwrapInvocationException(throwable)
            reportFailure(desk, cause)
            SnapshotResult(
                snapshot = RadarDisplaySnapshot.disconnected(
                    fallbackCenter,
                    level.gameTime,
                    RadarLinkStatus.API_ERROR
                ),
                filtererPos = null,
                radarPos = null,
                reason = "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}"
            )
        }
    }

    private fun readFilteredTracks(
        filter: Any,
        rawTracks: Iterable<*>,
        center: Vec3
    ): List<RadarDisplayTrack> {
        val tracks = mutableListOf<RadarDisplayTrack>()
        for (raw in rawTracks) {
            raw ?: continue
            val accepted = invokePublic(filter, "test", raw) as? Boolean
                ?: throw IllegalStateException("DetectionConfig.test returned no boolean")
            if (!accepted) continue

            val id = invokeFirst(raw, "getId", "id") as? String
                ?: throw IllegalStateException("RadarTrack ID is unavailable")
            if (id.isEmpty()) continue
            val position = invokeFirst(raw, "getPosition", "position") as? Vec3
                ?: throw IllegalStateException("RadarTrack position is unavailable")
            val velocity = invokeFirst(raw, "getVelocity", "velocity") as? Vec3 ?: Vec3.ZERO
            val category = invokeFirst(raw, "getTrackCategory", "trackCategory")

            tracks += RadarDisplayTrack(
                id = id,
                position = position,
                velocity = velocity,
                sprite = RadarDisplayTrackSprite.fromCategory(category)
            )
        }
        return tracks
            .sortedBy { it.position.distanceToSqr(center) }
            .take(RadarDisplaySnapshot.MAX_SYNCED_TRACKS)
    }

    private fun inactiveSnapshot(
        center: Vec3,
        gameTime: Long,
        status: RadarLinkStatus,
        radarPos: BlockPos?,
        detectionTag: CompoundTag,
        selectedTargetId: String?,
        range: Double = 0.0,
        connected: Boolean = false
    ): RadarDisplaySnapshot = RadarDisplaySnapshot(
        connected = connected,
        operational = false,
        radarPos = radarPos,
        center = center,
        range = range,
        detectionTag = detectionTag.copy(),
        selectedTrackId = selectedTargetId,
        tracks = emptyList(),
        updatedAt = gameTime,
        status = status
    )

    private fun logDiagnosticTransition(
        desk: ConsoleBlockEntity,
        result: SnapshotResult
    ) {
        val diagnostic = DiagnosticState(
            filtererPos = result.filtererPos,
            radarPos = result.radarPos,
            status = result.snapshot.status,
            trackCount = result.snapshot.tracks.size,
            reason = result.reason
        )
        if (lastDiagnosticByDesk.put(desk, diagnostic) == diagnostic) return
        if (result.snapshot.status == RadarLinkStatus.ACTIVE) {
            lastFailureByDesk.remove(desk)
        }

        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Radar endpoint desk={} filterer={} radar={} status={} filteredTracks={} reason={}",
            desk.blockPos,
            result.filtererPos,
            result.radarPos,
            result.snapshot.status,
            result.snapshot.tracks.size,
            result.reason
        )
    }

    private fun reportFailure(desk: ConsoleBlockEntity, cause: Throwable) {
        val signature = "${cause.javaClass.name}:${cause.message.orEmpty()}"
        if (lastFailureByDesk.put(desk, signature) == signature) return
        CCAeroworks.LOGGER.warn(
            "[CC-Aeroworks] Create: Radars API access failed for desk {}",
            desk.blockPos,
            cause
        )
    }

    private fun invokeFirst(instance: Any, vararg methodNames: String): Any? {
        var lastMissing: NoSuchMethodException? = null
        for (methodName in methodNames) {
            try {
                return invokePublic(instance, methodName)
            } catch (missing: NoSuchMethodException) {
                lastMissing = missing
            }
        }
        throw lastMissing ?: NoSuchMethodException("No method candidates supplied")
    }

    private fun invokePublic(instance: Any, methodName: String, vararg arguments: Any?): Any? {
        val method = findCompatibleMethod(instance.javaClass, methodName, arguments, requireStatic = false)
            ?: throw NoSuchMethodException("${instance.javaClass.name}#$methodName")
        return invokeMethod(method, instance, arguments)
    }

    private fun invokeStatic(className: String, methodName: String, vararg arguments: Any?): Any? {
        val type = Class.forName(className, true, CreateRadarCompat::class.java.classLoader)
        val method = findCompatibleMethod(type, methodName, arguments, requireStatic = true)
            ?: throw NoSuchMethodException("$className#$methodName")
        return invokeMethod(method, null, arguments)
    }

    private fun findCompatibleMethod(
        startType: Class<*>,
        methodName: String,
        arguments: Array<out Any?>,
        requireStatic: Boolean
    ): Method? {
        startType.methods.firstOrNull { method ->
            method.name == methodName &&
                Modifier.isStatic(method.modifiers) == requireStatic &&
                parametersCompatible(method.parameterTypes, arguments)
        }?.let { return it }

        var current: Class<*>? = startType
        while (current != null) {
            current.declaredMethods.firstOrNull { method ->
                method.name == methodName &&
                    Modifier.isStatic(method.modifiers) == requireStatic &&
                    parametersCompatible(method.parameterTypes, arguments)
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun parametersCompatible(
        parameterTypes: Array<Class<*>>,
        arguments: Array<out Any?>
    ): Boolean {
        if (parameterTypes.size != arguments.size) return false
        return parameterTypes.zip(arguments).all { (parameterType, argument) ->
            argument == null && !parameterType.isPrimitive ||
                argument != null && boxed(parameterType).isAssignableFrom(argument.javaClass)
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> type
    }

    private fun invokeMethod(
        method: Method,
        receiver: Any?,
        arguments: Array<out Any?>
    ): Any? {
        if (!method.canAccess(receiver) && !method.trySetAccessible()) {
            throw IllegalAccessException("Cannot access ${method.declaringClass.name}#${method.name}")
        }
        return try {
            method.invoke(receiver, *arguments)
        } catch (exception: InvocationTargetException) {
            throw exception.targetException ?: exception
        }
    }

    private fun readField(instance: Any, fieldName: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val field = runCatching { current.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                if (!field.canAccess(instance) && !field.trySetAccessible()) {
                    throw IllegalAccessException("Cannot access ${current.name}#$fieldName")
                }
                return field.get(instance)
            }
            current = current.superclass
        }
        throw NoSuchFieldException("${instance.javaClass.name}#$fieldName")
    }

    private fun unwrapInvocationException(throwable: Throwable): Throwable =
        if (throwable is InvocationTargetException) throwable.targetException ?: throwable else throwable

    private data class SnapshotResult(
        val snapshot: RadarDisplaySnapshot,
        val filtererPos: BlockPos?,
        val radarPos: BlockPos?,
        val reason: String
    )

    private data class DiagnosticState(
        val filtererPos: BlockPos?,
        val radarPos: BlockPos?,
        val status: RadarLinkStatus,
        val trackCount: Int,
        val reason: String
    )
}
