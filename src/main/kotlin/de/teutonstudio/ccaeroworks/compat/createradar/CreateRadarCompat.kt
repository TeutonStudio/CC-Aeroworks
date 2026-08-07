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

    private const val NETWORK_DATA_CLASS: String =
        "com.happysg.radar.block.behavior.networks.NetworkData"
    private const val DETECTION_CONFIG_CLASS: String =
        "com.happysg.radar.block.behavior.networks.config.DetectionConfig"
    private const val PHYSICS_HANDLER_CLASS: String =
        "com.happysg.radar.compat.vs2.PhysicsHandler"
    private const val NATIVE_MONITOR_INTERVAL_TICKS: Long = 5L
    private const val SNAPSHOT_HEARTBEAT_TICKS: Long = 15L

    private val lastStatusByDesk = WeakHashMap<ConsoleBlockEntity, RadarLinkStatus>()
    private val lastFailureByDesk = WeakHashMap<ConsoleBlockEntity, String>()

    /**
     * Called from the optional DataLinkBlockItem mixin. Returning true makes a desk carrying a
     * Radar Display participate in Create: Radars' normal MONITOR target path.
     */
    @JvmStatic
    fun isRadarDeskTarget(candidate: Any?): Boolean {
        val desk = candidate as? ConsoleBlockEntity ?: return false
        return AeroworksDeskAccess.hasRadarDisplay(desk)
    }

    /**
     * Mirrors MonitorBlockEntity.tick(): resolve the Data Link endpoint through NetworkData,
     * copy radar/filter/selection state from that group, rebuild filtered tracks, then sync the
     * desk block entity to clients every five ticks.
     */
    @JvmStatic
    fun refreshDesk(desk: ConsoleBlockEntity) {
        if (!ModList.get().isLoaded(MOD_ID)) return
        val level = desk.level as? ServerLevel ?: return
        if (level.gameTime % NATIVE_MONITOR_INTERVAL_TICKS != 0L) return

        val access = desk as? RadarDeskStateAccess ?: return
        if (!AeroworksDeskAccess.hasRadarDisplay(desk)) {
            if (access.ccaeroworks_getRadarSnapshot() != null) {
                access.ccaeroworks_setRadarSnapshot(null)
                desk.notifyUpdate()
            }
            return
        }

        val snapshot = readNetworkSnapshot(level, desk)
        logStatusTransition(desk, snapshot)

        val previous = access.ccaeroworks_getRadarSnapshot()
        if (!shouldSynchronize(previous, snapshot, level.gameTime)) return
        access.ccaeroworks_setRadarSnapshot(snapshot)
        desk.notifyUpdate()
    }

    private fun readNetworkSnapshot(
        level: ServerLevel,
        desk: ConsoleBlockEntity
    ): RadarDisplaySnapshot {
        val fallbackCenter = Vec3.atCenterOf(desk.blockPos)
        val networkLookup = invokeStaticLookup(NETWORK_DATA_CLASS, "get", level)
        if (!networkLookup.found || networkLookup.error != null || networkLookup.value == null) {
            return apiFailure(desk, fallbackCenter, level.gameTime, "NetworkData#get", networkLookup.error)
        }
        val networkData = networkLookup.value

        val filtererLookup = invokeLookup(
            networkData,
            "getFiltererForEndpoint",
            level.dimension(),
            desk.blockPos
        )
        if (!filtererLookup.found || filtererLookup.error != null) {
            return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "NetworkData#getFiltererForEndpoint",
                filtererLookup.error
            )
        }
        val filtererPos = filtererLookup.value as? BlockPos
            ?: return disconnected(fallbackCenter, level, RadarLinkStatus.RADAR_NOT_LINKED)

        val groupLookup = invokeLookup(networkData, "getGroup", level.dimension(), filtererPos)
        if (!groupLookup.found || groupLookup.error != null) {
            return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "NetworkData#getGroup",
                groupLookup.error
            )
        }
        val group = groupLookup.value
            ?: return disconnected(fallbackCenter, level, RadarLinkStatus.RADAR_NOT_LINKED)

        val endpointsLookup = readFieldLookup(group, "monitorEndpoints")
        if (!endpointsLookup.found || endpointsLookup.error != null) {
            return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "NetworkData.Group#monitorEndpoints",
                endpointsLookup.error
            )
        }
        val endpoints = endpointsLookup.value as? Collection<*>
            ?: return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "NetworkData.Group#monitorEndpoints result"
            )
        if (desk.blockPos !in endpoints) {
            return disconnected(fallbackCenter, level, RadarLinkStatus.RADAR_NOT_LINKED)
        }

        val radarPosLookup = readFieldLookup(group, "radarPos")
        if (!radarPosLookup.found || radarPosLookup.error != null) {
            return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "NetworkData.Group#radarPos",
                radarPosLookup.error
            )
        }
        val radarPos = radarPosLookup.value as? BlockPos
            ?: return disconnected(fallbackCenter, level, RadarLinkStatus.RADAR_NOT_LINKED)
        if (!level.isLoaded(radarPos)) {
            return disconnected(fallbackCenter, level, RadarLinkStatus.RADAR_NOT_LOADED)
        }
        val radar = level.getBlockEntity(radarPos)
            ?: return disconnected(fallbackCenter, level, RadarLinkStatus.RADAR_NOT_LOADED)

        val centerLookup = invokeStaticLookup(PHYSICS_HANDLER_CLASS, "getWorldVec", level, radarPos)
        if (!centerLookup.found || centerLookup.error != null) {
            return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "PhysicsHandler#getWorldVec",
                centerLookup.error
            )
        }
        val center = centerLookup.value as? Vec3
            ?: return apiFailure(
                desk,
                fallbackCenter,
                level.gameTime,
                "PhysicsHandler#getWorldVec result"
            )

        val rangeLookup = invokeLookup(radar, "getRange")
        if (!rangeLookup.found || rangeLookup.error != null) {
            return apiFailure(desk, center, level.gameTime, "IRadar#getRange", rangeLookup.error)
        }
        val range = (rangeLookup.value as? Number)?.toDouble()?.coerceAtLeast(0.0)
            ?: return apiFailure(desk, center, level.gameTime, "IRadar#getRange result")

        val runningLookup = invokeLookup(radar, "isRunning")
        if (!runningLookup.found || runningLookup.error != null) {
            return apiFailure(desk, center, level.gameTime, "IRadar#isRunning", runningLookup.error)
        }
        val running = runningLookup.value as? Boolean
            ?: return apiFailure(desk, center, level.gameTime, "IRadar#isRunning result")
        if (!running) return disconnected(center, level, RadarLinkStatus.RADAR_NOT_RUNNING)
        if (range <= 0.0) return disconnected(center, level, RadarLinkStatus.INVALID_RANGE)

        val detectionLookup = readFieldLookup(group, "detectionTag")
        if (!detectionLookup.found || detectionLookup.error != null) {
            return apiFailure(
                desk,
                center,
                level.gameTime,
                "NetworkData.Group#detectionTag",
                detectionLookup.error
            )
        }
        val detectionTag = detectionLookup.value as? CompoundTag
            ?: return apiFailure(
                desk,
                center,
                level.gameTime,
                "NetworkData.Group#detectionTag result"
            )

        val tracks = when (val result = readFilteredTracks(radar, detectionTag, center)) {
            is TrackReadResult.Success -> result.tracks
            is TrackReadResult.Failure -> return apiFailure(
                desk,
                center,
                level.gameTime,
                result.stage,
                result.cause
            )
        }

        val selectedLookup = readFieldLookup(group, "selectedTargetId")
        if (!selectedLookup.found || selectedLookup.error != null) {
            return apiFailure(
                desk,
                center,
                level.gameTime,
                "NetworkData.Group#selectedTargetId",
                selectedLookup.error
            )
        }
        val selected = selectedLookup.value as? String

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

    private fun readFilteredTracks(
        radar: Any,
        detectionTag: CompoundTag,
        center: Vec3
    ): TrackReadResult {
        val filterLookup = invokeStaticLookup(DETECTION_CONFIG_CLASS, "fromTag", detectionTag)
        if (!filterLookup.found || filterLookup.error != null || filterLookup.value == null) {
            return TrackReadResult.Failure("DetectionConfig#fromTag", filterLookup.error)
        }
        val filter = filterLookup.value

        val tracksLookup = invokeLookup(radar, "getTracks")
        if (!tracksLookup.found || tracksLookup.error != null) {
            return TrackReadResult.Failure("IRadar#getTracks", tracksLookup.error)
        }
        val rawTracks = tracksLookup.value as? Iterable<*>
            ?: return TrackReadResult.Failure("IRadar#getTracks result")

        val tracks = mutableListOf<RadarDisplayTrack>()
        for (raw in rawTracks) {
            raw ?: continue

            val acceptedLookup = invokeLookup(filter, "test", raw)
            if (!acceptedLookup.found || acceptedLookup.error != null) {
                return TrackReadResult.Failure("DetectionConfig#test", acceptedLookup.error)
            }
            val accepted = acceptedLookup.value as? Boolean
                ?: return TrackReadResult.Failure("DetectionConfig#test result")
            if (!accepted) continue

            val id = (invokeAny(raw, "getId", "id") as? String)
                ?.takeIf(String::isNotEmpty)
                ?: return TrackReadResult.Failure("RadarTrack#getId")
            val position = invokeAny(raw, "getPosition", "position") as? Vec3
                ?: return TrackReadResult.Failure("RadarTrack#getPosition")
            val velocity = invokeAny(raw, "getVelocity", "velocity") as? Vec3 ?: Vec3.ZERO
            val category = invokeAny(raw, "getTrackCategory", "trackCategory")

            tracks += RadarDisplayTrack(
                id = id,
                position = position,
                velocity = velocity,
                sprite = RadarDisplayTrackSprite.fromCategory(category)
            )
        }

        return TrackReadResult.Success(
            tracks.sortedBy { it.position.distanceToSqr(center) }
                .take(RadarDisplaySnapshot.MAX_SYNCED_TRACKS)
        )
    }

    private fun disconnected(
        center: Vec3,
        level: ServerLevel,
        status: RadarLinkStatus
    ): RadarDisplaySnapshot = RadarDisplaySnapshot.disconnected(center, level.gameTime, status)

    private fun apiFailure(
        desk: ConsoleBlockEntity,
        center: Vec3,
        gameTime: Long,
        stage: String,
        cause: Throwable? = null
    ): RadarDisplaySnapshot {
        reportAccessFailure(desk, stage, cause)
        return RadarDisplaySnapshot.disconnected(
            center,
            gameTime,
            RadarLinkStatus.API_INCOMPATIBLE
        )
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
        desk: ConsoleBlockEntity,
        snapshot: RadarDisplaySnapshot
    ) {
        val previous = lastStatusByDesk.put(desk, snapshot.status)
        if (snapshot.status == RadarLinkStatus.ACTIVE) {
            lastFailureByDesk.remove(desk)
        }
        if (previous == snapshot.status) return

        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Radar Display endpoint at {} changed from {} to {} (tracks={})",
            desk.blockPos,
            previous ?: "UNSEEN",
            snapshot.status,
            snapshot.tracks.size
        )
    }

    private fun reportAccessFailure(
        desk: ConsoleBlockEntity,
        stage: String,
        cause: Throwable? = null
    ) {
        val signature = buildString {
            append(stage)
            append(':').append(cause?.javaClass?.name.orEmpty())
            append(':').append(cause?.message.orEmpty())
        }
        if (lastFailureByDesk.put(desk, signature) == signature) return

        if (cause == null) {
            CCAeroworks.LOGGER.warn(
                "[CC-Aeroworks] Create: Radars endpoint API is unavailable at {} during {}",
                desk.blockPos,
                stage
            )
        } else {
            CCAeroworks.LOGGER.warn(
                "[CC-Aeroworks] Create: Radars endpoint access failed at {} during {}",
                desk.blockPos,
                stage,
                cause
            )
        }
    }

    private fun invokeAny(instance: Any, vararg methodNames: String): Any? {
        for (methodName in methodNames) {
            val lookup = invokeLookup(instance, methodName)
            if (lookup.found && lookup.error == null && lookup.value != null) return lookup.value
        }
        return null
    }

    private fun invokeLookup(
        instance: Any,
        methodName: String,
        vararg arguments: Any?
    ): ReflectionLookup {
        val method = findCompatibleMethod(instance.javaClass, methodName, arguments, requireStatic = false)
            ?: return ReflectionLookup(found = false)
        return invokeMethod(method, instance, arguments)
    }

    private fun invokeStaticLookup(
        className: String,
        methodName: String,
        vararg arguments: Any?
    ): ReflectionLookup {
        val type = runCatching { Class.forName(className) }
            .getOrElse { return ReflectionLookup(found = false, error = it) }
        val method = findCompatibleMethod(type, methodName, arguments, requireStatic = true)
            ?: return ReflectionLookup(found = false)
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
            argument == null
                ? !parameterType.isPrimitive
                : boxed(parameterType).isAssignableFrom(argument.javaClass)
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
    ): ReflectionLookup = try {
        if (!method.canAccess(receiver) && !method.trySetAccessible()) {
            ReflectionLookup(
                found = true,
                error = IllegalAccessException("Cannot access ${method.declaringClass.name}#${method.name}")
            )
        } else {
            ReflectionLookup(found = true, value = method.invoke(receiver, *arguments))
        }
    } catch (exception: Throwable) {
        ReflectionLookup(found = true, error = unwrapInvocationException(exception))
    }

    private fun readFieldLookup(instance: Any, fieldName: String): ReflectionLookup {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val field = runCatching { current.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                return try {
                    if (!field.canAccess(instance) && !field.trySetAccessible()) {
                        ReflectionLookup(
                            found = true,
                            error = IllegalAccessException("Cannot access ${current.name}#$fieldName")
                        )
                    } else {
                        ReflectionLookup(found = true, value = field.get(instance))
                    }
                } catch (exception: Throwable) {
                    ReflectionLookup(found = true, error = unwrapInvocationException(exception))
                }
            }
            current = current.superclass
        }
        return ReflectionLookup(found = false)
    }

    private fun unwrapInvocationException(exception: Throwable): Throwable =
        if (exception is InvocationTargetException) exception.targetException ?: exception else exception

    private data class ReflectionLookup(
        val found: Boolean,
        val value: Any? = null,
        val error: Throwable? = null
    )

    private sealed interface TrackReadResult {
        data class Success(val tracks: List<RadarDisplayTrack>) : TrackReadResult
        data class Failure(val stage: String, val cause: Throwable? = null) : TrackReadResult
    }
}
