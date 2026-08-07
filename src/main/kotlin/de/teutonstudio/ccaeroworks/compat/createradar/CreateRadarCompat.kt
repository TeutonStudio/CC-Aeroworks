package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarLinkStatus
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
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
    private const val RADAR_TRACK_UTIL_CLASS =
        "com.happysg.radar.block.radar.track.RadarTrackUtil"
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

        val state = desk as? RadarDeskStateAccess
        RadarTrace.event(
            "S00_REFRESH_ENTER",
            level,
            desk.blockPos,
            "deskClass=${desk.javaClass.name} hasRadarDisplay=${AeroworksDeskAccess.hasRadarDisplay(desk)} " +
                "implementsRadarState=${state != null} blockState=${desk.blockState} sockets=${desk.socketCount()} " +
                "previous=${snapshotSummary(state?.ccaeroworks_getRadarSnapshot())}"
        )
        if (state == null) {
            RadarTrace.event("S00_REFRESH_ABORT", level, desk.blockPos, "desk does not implement RadarDeskStateAccess")
            return
        }

        if (!AeroworksDeskAccess.hasRadarDisplay(desk)) {
            lastDiagnosticByDesk.remove(desk)
            lastFailureByDesk.remove(desk)
            val previous = state.ccaeroworks_getRadarSnapshot()
            RadarTrace.event(
                "S01_NO_RADAR_DISPLAY",
                level,
                desk.blockPos,
                "clearingPrevious=${previous != null} previous=${snapshotSummary(previous)}"
            )
            if (previous != null) {
                state.ccaeroworks_setRadarSnapshot(null)
                desk.notifyUpdate()
                RadarTrace.event("S19_NOTIFY_UPDATE", level, desk.blockPos, "reason=radar-display-removed")
            }
            return
        }

        val result = resolveSnapshot(level, desk)
        logDiagnosticTransition(desk, result)

        val previous = state.ccaeroworks_getRadarSnapshot()
        val sameContent = result.snapshot.sameContentAs(previous)
        val heartbeatDue = previous == null ||
            level.gameTime - previous.updatedAt !in 0 until SNAPSHOT_HEARTBEAT_TICKS
        RadarTrace.event(
            "S18_SNAPSHOT_DECISION",
            level,
            desk.blockPos,
            "sameContent=$sameContent heartbeatDue=$heartbeatDue previous=${snapshotSummary(previous)} next=${snapshotSummary(result.snapshot)}"
        )
        if (sameContent && !heartbeatDue) {
            RadarTrace.event("S18_SNAPSHOT_UNCHANGED", level, desk.blockPos, "no packet required this five-tick cycle")
            return
        }

        state.ccaeroworks_setRadarSnapshot(result.snapshot)
        RadarTrace.event(
            "S19_SNAPSHOT_STORE",
            level,
            desk.blockPos,
            "stored=${snapshotSummary(result.snapshot)} nativeTracks=${RadarTrace.tag(result.snapshot.nativeTracks)}"
        )
        desk.notifyUpdate()
        RadarTrace.event("S19_NOTIFY_UPDATE", level, desk.blockPos, "ConsoleBlockEntity.notifyUpdate invoked")
    }

    private fun resolveSnapshot(
        level: ServerLevel,
        desk: ConsoleBlockEntity
    ): SnapshotResult {
        return try {
            RadarTrace.event("S10_NETWORK_LOOKUP_BEGIN", level, desk.blockPos, "calling NetworkData.get(ServerLevel)")
            val networkData = invokeStatic(NETWORK_DATA_CLASS, "get", level)
                ?: throw IllegalStateException("NetworkData.get returned null")
            RadarTrace.event(
                "S10_NETWORK_DATA",
                level,
                desk.blockPos,
                "instanceClass=${networkData.javaClass.name} identity=${System.identityHashCode(networkData)}"
            )

            val filtererPos = invokePublic(
                networkData,
                "getFiltererForEndpoint",
                level.dimension(),
                desk.blockPos
            ) as? BlockPos
            RadarTrace.event(
                "S11_FILTERER_LOOKUP",
                level,
                desk.blockPos,
                "getFiltererForEndpoint(dimension=${level.dimension().location()}, endpoint=${desk.blockPos}) -> $filtererPos"
            )
            if (filtererPos == null) {
                return tracedResult(
                    level,
                    desk,
                    RadarDisplaySnapshot.disconnected(level.gameTime, RadarLinkStatus.NOT_LINKED),
                    null,
                    null,
                    "endpoint is not registered"
                )
            }

            val group = invokePublic(
                networkData,
                "getGroup",
                level.dimension(),
                filtererPos
            )
            RadarTrace.event(
                "S12_GROUP_LOOKUP",
                level,
                desk.blockPos,
                "filterer=$filtererPos groupClass=${group?.javaClass?.name} groupNull=${group == null}"
            )
            if (group == null) {
                return tracedResult(
                    level,
                    desk,
                    RadarDisplaySnapshot.disconnected(level.gameTime, RadarLinkStatus.NOT_LINKED),
                    filtererPos,
                    null,
                    "registered filterer group is missing"
                )
            }

            RadarTrace.periodic(
                "S12_GROUP_FIELDS",
                level,
                desk.blockPos,
                20L,
                describeObjectFields(group)
            )

            val monitorEndpoints = readField(group, "monitorEndpoints") as? Collection<*>
                ?: throw IllegalStateException("NetworkData.Group.monitorEndpoints is not a collection")
            RadarTrace.event(
                "S12_MONITOR_ENDPOINTS",
                level,
                desk.blockPos,
                "count=${monitorEndpoints.size} containsDesk=${desk.blockPos in monitorEndpoints} endpoints=${monitorEndpoints.take(64)}"
            )
            if (desk.blockPos !in monitorEndpoints) {
                return tracedResult(
                    level,
                    desk,
                    RadarDisplaySnapshot.disconnected(level.gameTime, RadarLinkStatus.NOT_LINKED),
                    filtererPos,
                    null,
                    "group does not contain the desk endpoint"
                )
            }

            val detectionTag = (readField(group, "detectionTag") as? CompoundTag)?.copy()
                ?: throw IllegalStateException("NetworkData.Group.detectionTag is unavailable")
            val selectedTargetId = readField(group, "selectedTargetId") as? String
            val radarPos = readField(group, "radarPos") as? BlockPos
            RadarTrace.event(
                "S13_GROUP_STATE",
                level,
                desk.blockPos,
                "filterer=$filtererPos radarPos=$radarPos selectedTargetId=$selectedTargetId detection=${RadarTrace.tag(detectionTag)}"
            )
            if (radarPos == null) {
                return tracedResult(
                    level,
                    desk,
                    inactiveSnapshot(
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_NOT_ASSIGNED,
                        radarPos = null,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId
                    ),
                    filtererPos,
                    null,
                    "group has no radarPos"
                )
            }

            val loaded = level.isLoaded(radarPos)
            RadarTrace.event("S14_RADAR_CHUNK", level, desk.blockPos, "radarPos=$radarPos level.isLoaded=$loaded")
            if (!loaded) {
                return tracedResult(
                    level,
                    desk,
                    inactiveSnapshot(
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_NOT_LOADED,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId
                    ),
                    filtererPos,
                    radarPos,
                    "radar chunk is not loaded"
                )
            }

            val radar = level.getBlockEntity(radarPos)
            RadarTrace.event(
                "S14_RADAR_BLOCK_ENTITY",
                level,
                desk.blockPos,
                "radarPos=$radarPos class=${radar?.javaClass?.name} blockState=${level.getBlockState(radarPos)}"
            )
            if (radar == null) {
                return tracedResult(
                    level,
                    desk,
                    inactiveSnapshot(
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_NOT_LOADED,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId
                    ),
                    filtererPos,
                    radarPos,
                    "radar block entity is unavailable"
                )
            }

            val range = (invokePublic(radar, "getRange") as? Number)
                ?.toDouble()
                ?.coerceAtLeast(0.0)
                ?: throw IllegalStateException("IRadar.getRange returned no number")
            val running = invokePublic(radar, "isRunning") as? Boolean
                ?: throw IllegalStateException("IRadar.isRunning returned no boolean")
            val radarType = runCatching { invokePublic(radar, "getRadarType") }.getOrNull()
            val globalAngle = runCatching { invokePublic(radar, "getGlobalAngle") }.getOrNull()
            val radarDirection = runCatching { invokePublic(radar, "getradarDirection") }.getOrNull()
            val relative = runCatching { invokePublic(radar, "renderRelativeToMonitor") }.getOrNull()
            val worldPos = runCatching { invokePublic(radar, "getWorldPos") }.getOrNull()
            RadarTrace.event(
                "S15_RADAR_STATE",
                level,
                desk.blockPos,
                "class=${radar.javaClass.name} range=$range running=$running radarType=$radarType globalAngle=$globalAngle " +
                    "radarDirection=$radarDirection renderRelativeToMonitor=$relative getWorldPos=$worldPos"
            )

            if (!running) {
                return tracedResult(
                    level,
                    desk,
                    inactiveSnapshot(
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.RADAR_STOPPED,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId,
                        range = range,
                        connected = true
                    ),
                    filtererPos,
                    radarPos,
                    "IRadar.isRunning is false"
                )
            }
            if (range <= 0.0) {
                return tracedResult(
                    level,
                    desk,
                    inactiveSnapshot(
                        gameTime = level.gameTime,
                        status = RadarLinkStatus.INVALID_RANGE,
                        radarPos = radarPos,
                        detectionTag = detectionTag,
                        selectedTargetId = selectedTargetId,
                        range = range,
                        connected = true
                    ),
                    filtererPos,
                    radarPos,
                    "IRadar.getRange is not positive"
                )
            }

            val filter = invokeStatic(DETECTION_CONFIG_CLASS, "fromTag", detectionTag.copy())
                ?: throw IllegalStateException("DetectionConfig.fromTag returned null")
            RadarTrace.event(
                "S16_FILTER_READY",
                level,
                desk.blockPos,
                "filterClass=${filter.javaClass.name} filterState=$filter"
            )
            val rawTracks = invokePublic(radar, "getTracks") as? Iterable<*>
                ?: throw IllegalStateException("IRadar.getTracks returned no iterable")
            val nativeTracks = serializeFilteredTracks(filter, rawTracks)
            RadarTrace.event(
                "S16_TRACKS_SERIALIZED",
                level,
                desk.blockPos,
                "rawCount=${nativeTracks.rawCount} acceptedCount=${nativeTracks.count} payload=${RadarTrace.tag(nativeTracks.tag)}"
            )

            tracedResult(
                level,
                desk,
                RadarDisplaySnapshot(
                    connected = true,
                    operational = true,
                    radarPos = radarPos,
                    range = range,
                    detectionTag = detectionTag,
                    selectedTrackId = selectedTargetId,
                    nativeTracks = nativeTracks.tag,
                    trackCount = nativeTracks.count,
                    updatedAt = level.gameTime,
                    status = RadarLinkStatus.ACTIVE
                ),
                filtererPos,
                radarPos,
                "endpoint registered; native MonitorBlockEntity payload synchronized"
            )
        } catch (throwable: Throwable) {
            val cause = unwrapInvocationException(throwable)
            reportFailure(desk, cause)
            RadarTrace.event(
                "S99_API_ERROR",
                level,
                desk.blockPos,
                "failure=${RadarTrace.throwable(cause)}"
            )
            tracedResult(
                level,
                desk,
                RadarDisplaySnapshot.disconnected(level.gameTime, RadarLinkStatus.API_ERROR),
                null,
                null,
                "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}"
            )
        }
    }

    private fun tracedResult(
        level: ServerLevel,
        desk: ConsoleBlockEntity,
        snapshot: RadarDisplaySnapshot,
        filtererPos: BlockPos?,
        radarPos: BlockPos?,
        reason: String
    ): SnapshotResult {
        RadarTrace.event(
            "S17_SNAPSHOT_RESULT",
            level,
            desk.blockPos,
            "filterer=$filtererPos radar=$radarPos reason=$reason snapshot=${snapshotSummary(snapshot)}"
        )
        return SnapshotResult(snapshot, filtererPos, radarPos, reason)
    }

    /**
     * Applies Create: Radars' own DetectionConfig and then delegates serialization
     * to RadarTrackUtil. No RadarTrack fields or sprite categories are recreated
     * by CC-Aeroworks.
     */
    private fun serializeFilteredTracks(
        filter: Any,
        rawTracks: Iterable<*>
    ): NativeTrackPayload {
        val filtered = ArrayList<Any>(RadarDisplaySnapshot.MAX_SYNCED_TRACKS)
        var rawCount = 0
        for (raw in rawTracks) {
            raw ?: continue
            rawCount++
            val accepted = invokePublic(filter, "test", raw) as? Boolean
                ?: throw IllegalStateException("DetectionConfig.test returned no boolean")
            if (!accepted) continue
            filtered += raw
            if (filtered.size >= RadarDisplaySnapshot.MAX_SYNCED_TRACKS) break
        }

        val serialized = invokeStatic(RADAR_TRACK_UTIL_CLASS, "serializeNBTList", filtered) as? CompoundTag
            ?: throw IllegalStateException("RadarTrackUtil.serializeNBTList returned no CompoundTag")
        return NativeTrackPayload(serialized.copy(), filtered.size, rawCount)
    }

    private fun inactiveSnapshot(
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
        range = range,
        detectionTag = detectionTag.copy(),
        selectedTrackId = selectedTargetId,
        nativeTracks = CompoundTag(),
        trackCount = 0,
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
            trackCount = result.snapshot.trackCount,
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
            result.snapshot.trackCount,
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

    private fun snapshotSummary(snapshot: RadarDisplaySnapshot?): String = if (snapshot == null) {
        "null"
    } else {
        "status=${snapshot.status},connected=${snapshot.connected},operational=${snapshot.operational},radar=${snapshot.radarPos}," +
            "range=${snapshot.range},tracks=${snapshot.trackCount},selected=${snapshot.selectedTrackId}," +
            "serverTick=${snapshot.updatedAt},clientReceipt=${snapshot.receivedAtClientTick}"
    }

    private fun describeObjectFields(instance: Any): String {
        val parts = mutableListOf<String>()
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val value = runCatching {
                    if (!field.canAccess(instance)) field.trySetAccessible()
                    field.get(instance)
                }.getOrElse { "<${it.javaClass.simpleName}:${it.message}>" }
                parts += "${current.simpleName}.${field.name}=${summarizeValue(value)}"
            }
            current = current.superclass
        }
        return "groupObject=${instance.javaClass.name} fields={${parts.joinToString(", ")}}"
    }

    private fun summarizeValue(value: Any?): String = when (value) {
        null -> "null"
        is CompoundTag -> RadarTrace.tag(value, 8000)
        is Collection<*> -> "Collection(size=${value.size}, values=${value.take(64)})"
        is Map<*, *> -> "Map(size=${value.size}, entries=${value.entries.take(64)})"
        else -> value.toString().take(8000)
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
            val type = current
            type.declaredMethods.firstOrNull { method ->
                method.name == methodName &&
                    Modifier.isStatic(method.modifiers) == requireStatic &&
                    parametersCompatible(method.parameterTypes, arguments)
            }?.let { return it }
            current = type.superclass
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
            val type = current
            val field = runCatching { type.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                if (!field.canAccess(instance) && !field.trySetAccessible()) {
                    throw IllegalAccessException("Cannot access ${type.name}#$fieldName")
                }
                return field.get(instance)
            }
            current = type.superclass
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

    private data class NativeTrackPayload(
        val tag: CompoundTag,
        val count: Int,
        val rawCount: Int
    )

    private data class DiagnosticState(
        val filtererPos: BlockPos?,
        val radarPos: BlockPos?,
        val status: RadarLinkStatus,
        val trackCount: Int,
        val reason: String
    )
}
