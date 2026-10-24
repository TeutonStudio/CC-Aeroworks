package de.teutonstudio.ccaeroworks.radarcompat.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayInput
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.radarcompat.createradar.CreateRadarCompat
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarNetworkControllerLookup
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarTrace
import de.teutonstudio.ccaeroworks.radarcompat.createradar.selectRadarControllerRoute
import de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks.RadarDeskAccess
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns a tap on the native radar pseudo-script into Create: Radars' normal selected-target flow.
 * The optional mod remains behind reflection so CC-Aeroworks can still load without it.
 */
object RadarTargetSelection {
    private const val NETWORK_DATA_CLASS = "com.happysg.radar.block.behavior.networks.NetworkData"
    private const val PHYSICS_HANDLER_CLASS = "com.happysg.radar.compat.vs2.PhysicsHandler"
    private const val SABLE_UTILS_CLASS = "com.happysg.radar.compat.vs2.SableUtils"
    private const val CONTACT_TOUCH_RADIUS = 0.06

    private val lastFailureByDesk = WeakHashMap<ConsoleBlockEntity, String>()

    fun handle(desk: ConsoleBlockEntity, source: RadarSourceKey, input: DeskDisplayInput) {
        if (input.action != DisplayPointerAction.TAP.eventName) return
        val level = desk.level as? ServerLevel ?: return
        val snapshot = RadarSourceRegistry.resolveSnapshot(desk, source) ?: return
        if (!RadarDisplaySnapshot.isFresh(snapshot, level.gameTime)) return

        val facing = RadarDisplayOrientation.effectiveFacing(desk, input.touch.socket)
        val displayType = RadarDeskAccess.radarDisplayType(desk, input.touch.socket) ?: return
        val nativeTouch = nativeRadarTouch(displayType, input.touch.u, input.touch.v) ?: return
        val projection = resolveProjection(level, desk, snapshot)
        val trackId = findDisplayedTrack(
            snapshot,
            facing,
            nativeTouch.u,
            nativeTouch.v,
            projection
        ) ?: return

        runCatching {
            selectNativeTrack(level, desk, source, snapshot, trackId)
        }.onSuccess { selected ->
            if (selected) {
                lastFailureByDesk.remove(desk)
                RadarTrace.event("S30_TOUCH_SELECT", level, desk.blockPos) {
                    "socket=${input.touch.socket} source=${source.id} track=$trackId u=${input.touch.u} v=${input.touch.v}"
                }
            }
        }.onFailure { throwable ->
            val cause = unwrap(throwable)
            val signature = "${cause.javaClass.name}:${cause.message.orEmpty()}"
            if (lastFailureByDesk.put(desk, signature) != signature) {
                CCAeroworks.LOGGER.warn(
                    "[CC-Aeroworks] Native radar target selection failed for desk {}",
                    desk.blockPos,
                    cause
                )
            }
        }
    }

    private fun findDisplayedTrack(
        snapshot: RadarDisplaySnapshot,
        facing: Direction,
        touchU: Double,
        touchV: Double,
        projection: RadarProjectionContext
    ): String? {
        val tracks = snapshot.nativeTracks.getList("tracks", Tag.TAG_COMPOUND.toInt())
        val maximumDistanceSquared = CONTACT_TOUCH_RADIUS * CONTACT_TOUCH_RADIUS

        return (0 until tracks.size)
            .asSequence()
            .map { tracks.getCompound(it) }
            .mapNotNull { track ->
                val id = track.getString("id").takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val position = projectRadarContact(
                    track.getDouble("x") - projection.centerX,
                    track.getDouble("z") - projection.centerZ,
                    snapshot.range,
                    facing,
                    projection.rotationRadians
                ) ?: return@mapNotNull null
                val du = position.u - touchU
                val dv = position.v - touchV
                val distanceSquared = du * du + dv * dv
                if (distanceSquared <= maximumDistanceSquared) id to distanceSquared else null
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun resolveProjection(
        level: ServerLevel,
        desk: ConsoleBlockEntity,
        snapshot: RadarDisplaySnapshot
    ): RadarProjectionContext {
        val radarPos = snapshot.radarPos ?: return RadarProjectionContext(0.0, 0.0, 0.0)
        return runCatching {
            val radar = level.getBlockEntity(radarPos) ?: return@runCatching null
            val nativeRadarPos = invokePublic(radar, "getWorldPos") as? BlockPos ?: radarPos
            val worldRadarPos = invokeStatic(PHYSICS_HANDLER_CLASS, "getWorldPos", level, nativeRadarPos) as? BlockPos
                ?: nativeRadarPos
            val relativeToMonitor = invokePublic(radar, "renderRelativeToMonitor") as? Boolean == true
            val rotation = if (relativeToMonitor) shipRelativeRotation(level, desk) else 0.0
            RadarProjectionContext(worldRadarPos.x + 0.5, worldRadarPos.z + 0.5, rotation)
        }.getOrNull() ?: RadarProjectionContext(radarPos.x + 0.5, radarPos.z + 0.5, 0.0)
    }

    /** Mirrors MonitorRenderer's ship-relative yaw adjustment for mobile Sable radar displays. */
    private fun shipRelativeRotation(level: ServerLevel, desk: ConsoleBlockEntity): Double {
        val ship = invokeStatic(SABLE_UTILS_CLASS, "getShipManagingPos", level, desk.blockPos) ?: return 0.0
        val pose = invokePublic(ship, "logicalPose") ?: return 0.0
        val forward = invokePublic(pose, "transformNormal", Vector3d(0.0, 0.0, 1.0)) as? Vector3d
            ?: return 0.0
        val shipYaw = atan2(forward.x(), -forward.z())
        return -(shipYaw + PI)
    }

    private fun selectNativeTrack(
        level: ServerLevel,
        displayDesk: ConsoleBlockEntity,
        source: RadarSourceKey,
        snapshot: RadarDisplaySnapshot,
        trackId: String
    ): Boolean {
        val sourceDesk = ConsoleMultiblockManager.resolve(level, displayDesk.blockPos)
            .memberAt(source.ingressPos)
            ?.desk
            ?: return false
        val networkData = invokeStatic(NETWORK_DATA_CLASS, "get", level) ?: return false
        val localFilterer = invokePublic(
            networkData,
            "getFiltererForEndpoint",
            level.dimension(),
            sourceDesk.blockPos
        ) as? BlockPos
        val localGroup = localFilterer?.let {
            invokePublic(networkData, "getGroup", level.dimension(), it)
        }
        val monitorEndpoints = localGroup?.let { readField(it, "monitorEndpoints") as? Collection<*> }.orEmpty()
        val route = selectRadarControllerRoute(
            localFilterer,
            sourceDesk.blockPos in monitorEndpoints,
            RadarNetworkControllerLookup.controllersFor(sourceDesk, networkData)
        )
        val filtererPos = route.filtererPos?.takeUnless { route.ambiguous } ?: return false
        val radarPos = snapshot.radarPos ?: return false
        val radar = level.getBlockEntity(radarPos) ?: return false
        val rawTracks = invokePublic(radar, "getTracks") as? Iterable<*> ?: return false
        val target = rawTracks.firstOrNull { track ->
            track != null && invokePublic(track, "getId") == trackId
        } ?: return false
        val filterer = level.getBlockEntity(filtererPos) ?: return false
        invokePublic(filterer, "receiveSelectedTargetFromMonitor", target, emptyList<AABB>())
        return true
    }

    private fun invokePublic(instance: Any, methodName: String, vararg arguments: Any?): Any? {
        val method = findCompatibleMethod(instance.javaClass, methodName, arguments, false)
            ?: throw NoSuchMethodException("${instance.javaClass.name}#$methodName")
        return invokeMethod(method, instance, arguments)
    }

    private fun invokeStatic(className: String, methodName: String, vararg arguments: Any?): Any? {
        val type = Class.forName(className, true, CreateRadarCompat::class.java.classLoader)
        val method = findCompatibleMethod(type, methodName, arguments, true)
            ?: throw NoSuchMethodException("$className#$methodName")
        return invokeMethod(method, null, arguments)
    }

    private fun findCompatibleMethod(
        startType: Class<*>,
        methodName: String,
        arguments: Array<out Any?>,
        requireStatic: Boolean
    ): Method? = startType.methods.firstOrNull { method ->
        method.name == methodName &&
            Modifier.isStatic(method.modifiers) == requireStatic &&
            method.parameterTypes.size == arguments.size &&
            method.parameterTypes.zip(arguments).all { (parameterType, argument) ->
                argument == null && !parameterType.isPrimitive ||
                    argument != null && boxed(parameterType).isAssignableFrom(argument.javaClass)
            }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
        java.lang.Byte.TYPE -> Byte::class.javaObjectType
        java.lang.Short.TYPE -> Short::class.javaObjectType
        java.lang.Integer.TYPE -> Int::class.javaObjectType
        java.lang.Long.TYPE -> Long::class.javaObjectType
        java.lang.Float.TYPE -> Float::class.javaObjectType
        java.lang.Double.TYPE -> Double::class.javaObjectType
        java.lang.Character.TYPE -> Char::class.javaObjectType
        else -> type
    }

    private fun invokeMethod(method: Method, receiver: Any?, arguments: Array<out Any?>): Any? = try {
        method.invoke(receiver, *arguments)
    } catch (exception: InvocationTargetException) {
        throw exception.targetException ?: exception
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
            current = current.superclass
        }
        throw NoSuchFieldException("${instance.javaClass.name}#$fieldName")
    }

    private fun unwrap(throwable: Throwable): Throwable =
        if (throwable is InvocationTargetException) throwable.targetException ?: throwable else throwable
}

internal data class RadarContactScreenPosition(val u: Double, val v: Double)
private data class RadarProjectionContext(val centerX: Double, val centerZ: Double, val rotationRadians: Double)

private const val NATIVE_TRACK_POSITION_SCALE = 0.75

/** Converts the complete framed display hit area to the centered square used by MonitorRenderer. */
internal fun nativeRadarTouch(type: RadarDisplayType, u: Double, v: Double): RadarContactScreenPosition? {
    val width = type.displayType.surfaceWidthParts / 16.0
    val height = type.displayType.surfaceHeightParts / 16.0
    val nativeU = 0.5 + (u - 0.5) * width / type.nativeSurfaceScale
    val nativeV = 0.5 + (v - 0.5) * height / type.nativeSurfaceScale
    if (nativeU !in 0.0..1.0 || nativeV !in 0.0..1.0) return null
    return RadarContactScreenPosition(nativeU, nativeV)
}

/** Mirrors MonitorRenderer.calculateTrackOffset(...), including its 0.75 display scale. */
internal fun projectRadarContact(
    relativeX: Double,
    relativeZ: Double,
    range: Double,
    facing: Direction,
    rotationRadians: Double = 0.0
): RadarContactScreenPosition? {
    if (!range.isFinite() || range <= 0.0) return null
    val cosine = cos(rotationRadians)
    val sine = sin(rotationRadians)
    val rotatedX = relativeX * cosine - relativeZ * sine
    val rotatedZ = relativeX * sine + relativeZ * cosine
    var horizontal = if (facing.axis == Direction.Axis.Z) rotatedX else rotatedZ
    var vertical = if (facing.axis == Direction.Axis.Z) rotatedZ else rotatedX
    if (facing == Direction.NORTH || facing == Direction.EAST) horizontal = -horizontal
    if (facing == Direction.NORTH || facing == Direction.WEST) vertical = -vertical

    val horizontalOffset = horizontal / range / 2.0
    val verticalOffset = vertical / range / 2.0
    if (abs(horizontalOffset) > 0.5 || abs(verticalOffset) > 0.5) return null
    return RadarContactScreenPosition(
        u = 0.5 + horizontalOffset * NATIVE_TRACK_POSITION_SCALE,
        v = 0.5 - verticalOffset * NATIVE_TRACK_POSITION_SCALE
    )
}
