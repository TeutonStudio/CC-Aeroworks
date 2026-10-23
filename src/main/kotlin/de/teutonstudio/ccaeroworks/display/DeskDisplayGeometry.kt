package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import kotlin.math.abs
import kotlin.math.floor
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

data class DeskDisplayPointer(
    val socket: Int,
    val socketName: String,
    val moduleId: String,
    val u: Double,
    val v: Double,
    val width: Int,
    val height: Int,
    val surfaceWidthParts: Int = 10,
    val surfaceHeightParts: Int = 7
) {
    fun toTouch(): DeskDisplayTouch = DeskDisplayTouch(
        socket = socket,
        socketName = socketName,
        moduleId = moduleId,
        x = DeskDisplayGeometry.gridCoordinate(u, width),
        y = DeskDisplayGeometry.gridCoordinate(v, height),
        width = width,
        height = height,
        u = u,
        v = v
    )
}

object DeskDisplayGeometry {
    const val LARGE_SOCKET: Int = 2

    // Programmable surface inside the raised CC:Tweaked-style display frame.
    const val MIN_X: Double = 3.0 / 16.0
    const val MAX_X: Double = 13.0 / 16.0
    const val MIN_Z: Double = 4.5 / 16.0
    const val MAX_Z: Double = 11.5 / 16.0
    const val SURFACE_Y: Double = 2.2 / 16.0
    private const val RAY_EPSILON: Double = 1.0e-7

    @JvmStatic
    fun resolveHit(desk: ConsoleBlockEntity, hitLocation: Vec3): DeskDisplayPointer? =
        (0 until desk.socketCount()).mapNotNull { socket ->
            val descriptor = descriptor(desk, socket) ?: return@mapNotNull null
            val inverse = moduleTransform(desk, socket)?.invert() ?: return@mapNotNull null
            val local = Vector3f(
                (hitLocation.x - desk.blockPos.x).toFloat(),
                (hitLocation.y - desk.blockPos.y).toFloat(),
                (hitLocation.z - desk.blockPos.z).toFloat()
            )
            inverse.transformPosition(local)
            val pointer = pointerFromLocal(descriptor, local.x.toDouble(), local.z.toDouble())
                ?: return@mapNotNull null
            pointer to abs(local.y.toDouble() - SURFACE_Y)
        }.minByOrNull { it.second }?.first

    @JvmStatic
    fun resolveRay(desk: ConsoleBlockEntity, rayStart: Vec3, rayEnd: Vec3): DeskDisplayPointer? =
        (0 until desk.socketCount()).mapNotNull { socket ->
            val descriptor = descriptor(desk, socket) ?: return@mapNotNull null
            val inverse = moduleTransform(desk, socket)?.invert() ?: return@mapNotNull null
            val localStart = Vector3f(
                (rayStart.x - desk.blockPos.x).toFloat(),
                (rayStart.y - desk.blockPos.y).toFloat(),
                (rayStart.z - desk.blockPos.z).toFloat()
            )
            val localEnd = Vector3f(
                (rayEnd.x - desk.blockPos.x).toFloat(),
                (rayEnd.y - desk.blockPos.y).toFloat(),
                (rayEnd.z - desk.blockPos.z).toFloat()
            )
            inverse.transformPosition(localStart)
            inverse.transformPosition(localEnd)
            val dy = localEnd.y.toDouble() - localStart.y.toDouble()
            if (abs(dy) <= RAY_EPSILON) return@mapNotNull null
            val t = (SURFACE_Y - localStart.y.toDouble()) / dy
            if (t < 0.0 || t > 1.0) return@mapNotNull null
            val x = localStart.x.toDouble() + (localEnd.x.toDouble() - localStart.x.toDouble()) * t
            val z = localStart.z.toDouble() + (localEnd.z.toDouble() - localStart.z.toDouble()) * t
            val pointer = pointerFromLocal(descriptor, x, z) ?: return@mapNotNull null
            pointer to t
        }.minByOrNull { it.second }?.first

    @JvmStatic
    fun pointer(desk: ConsoleBlockEntity, socket: Int, u: Double, v: Double): DeskDisplayPointer? {
        if (!u.isFinite() || !v.isFinite() || u !in 0.0..1.0 || v !in 0.0..1.0) return null
        return descriptor(desk, socket)?.copy(u = u, v = v)
    }

    @JvmStatic
    fun touch(desk: ConsoleBlockEntity, socket: Int, u: Double, v: Double): DeskDisplayTouch? =
        pointer(desk, socket, u, v)?.toTouch()

    @JvmStatic
    fun isInteractiveDisplay(desk: ConsoleBlockEntity, socket: Int): Boolean = descriptor(desk, socket) != null

    @JvmStatic
    fun moduleTransform(desk: ConsoleBlockEntity, socketIndex: Int): Matrix4f? {
        val socket = desk.sockets().getOrNull(socketIndex) ?: return null
        return Matrix4f()
            .translate(0.5f, 0.5f, 0.5f)
            .rotate(ConsoleBlock.rotationFor(desk.blockState))
            .translate(
                (socket.offset().x - 0.5).toFloat(),
                (socket.offset().y - 0.5).toFloat(),
                (socket.offset().z - 0.5).toFloat()
            )
            .rotate(socket.orientation())
            .translate(-0.5f, 0.0f, -0.5f)
    }

    @JvmStatic
    @JvmOverloads
    fun localX(u: Double, surfaceParts: Int = 10): Double =
        0.5 + (u.coerceIn(0.0, 1.0) - 0.5) * surfaceParts / 16.0

    @JvmStatic
    @JvmOverloads
    fun localZ(v: Double, surfaceParts: Int = 7): Double =
        0.5 + (0.5 - v.coerceIn(0.0, 1.0)) * surfaceParts / 16.0

    @JvmStatic
    fun gridCoordinate(normalized: Double, count: Int): Int {
        if (count <= 1) return 1
        return floor(normalized.coerceIn(0.0, 1.0) * count)
            .toInt()
            .coerceIn(0, count - 1) + 1
    }

    internal fun pointerFromLocal(
        descriptor: DeskDisplayPointer,
        x: Double,
        z: Double
    ): DeskDisplayPointer? {
        val minX = localX(0.0, descriptor.surfaceWidthParts)
        val maxX = localX(1.0, descriptor.surfaceWidthParts)
        val minZ = localZ(1.0, descriptor.surfaceHeightParts)
        val maxZ = localZ(0.0, descriptor.surfaceHeightParts)
        if (x !in minX..maxX || z !in minZ..maxZ) return null
        return descriptor.copy(
            u = ((x - minX) / (maxX - minX)).coerceIn(0.0, 1.0),
            v = ((maxZ - z) / (maxZ - minZ)).coerceIn(0.0, 1.0)
        )
    }

    private fun descriptor(desk: ConsoleBlockEntity, socket: Int): DeskDisplayPointer? {
        if (socket !in 0 until desk.socketCount() || !DisplayTouchAccess.enabled(desk)) return null
        val module = desk.module(socket) ?: return null
        val displayVariant = CCModuleTypes.displayVariant(module.type())
        if (displayVariant != null && !displayVariant.tier.touchCapable) return null
        // Extension bindings own the complete surface. In particular, the Radar compatibility
        // pseudo-script renders native radar output and must not also dispatch Lua touch events.
        if (DisplayBindings.get(desk, socket) is DisplayBinding.Extension) return null
        val displayType = displayVariant?.type
        val type = displayType ?: InteractiveDisplayExtensions.displayType(module.type()) ?: return null
        // Third-party interactive module types retain their own fixed raster.
        val ppb = if (displayType != null) DisplayResolutionSettings.partsPerBlock(desk, socket) else type.partsPerBlock

        return DeskDisplayPointer(
            socket = socket,
            socketName = DeskSockets.name(socket),
            moduleId = AeroworksModuleAccess.id(module).toString(),
            u = 0.5,
            v = 0.5,
            width = type.pixelWidthAt(ppb),
            height = type.pixelHeightAt(ppb),
            surfaceWidthParts = type.surfaceWidthParts,
            surfaceHeightParts = type.surfaceHeightParts
        )
    }
}
