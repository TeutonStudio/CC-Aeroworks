package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.floor

data class DeskDisplayPointer(
    val socket: Int,
    val socketName: String,
    val moduleId: String,
    val u: Double,
    val v: Double,
    val width: Int,
    val height: Int
) {
    fun toTouch(): DeskDisplayTouch = DeskDisplayTouch(
        socket = socket,
        socketName = socketName,
        moduleId = moduleId,
        x = DeskDisplayGeometry.gridCoordinate(u, width),
        y = DeskDisplayGeometry.gridCoordinate(v, height),
        width = width,
        height = height
    )
}

object DeskDisplayGeometry {
    const val LARGE_SOCKET: Int = 2

    // Top surface of models/block/module/three_digit_display.json.
    const val MIN_X: Double = 3.0 / 16.0
    const val MAX_X: Double = 13.0 / 16.0
    const val MIN_Z: Double = 4.5 / 16.0
    const val MAX_Z: Double = 11.5 / 16.0
    const val SURFACE_Y: Double = 2.2 / 16.0

    @JvmStatic
    fun resolveHit(desk: ConsoleBlockEntity, hitLocation: Vec3): DeskDisplayPointer? {
        val descriptor = descriptor(desk, LARGE_SOCKET) ?: return null
        val inverse = moduleTransform(desk, LARGE_SOCKET)?.invert() ?: return null
        val local = Vector3f(
            (hitLocation.x - desk.blockPos.x).toFloat(),
            (hitLocation.y - desk.blockPos.y).toFloat(),
            (hitLocation.z - desk.blockPos.z).toFloat()
        )
        inverse.transformPosition(local)

        val x = local.x.toDouble()
        val z = local.z.toDouble()
        if (x !in MIN_X..MAX_X || z !in MIN_Z..MAX_Z) return null

        return descriptor.copy(
            u = ((x - MIN_X) / (MAX_X - MIN_X)).coerceIn(0.0, 1.0),
            v = ((MAX_Z - z) / (MAX_Z - MIN_Z)).coerceIn(0.0, 1.0)
        )
    }

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
    fun localX(u: Double): Double = MIN_X + u.coerceIn(0.0, 1.0) * (MAX_X - MIN_X)

    @JvmStatic
    fun localZ(v: Double): Double = MAX_Z - v.coerceIn(0.0, 1.0) * (MAX_Z - MIN_Z)

    @JvmStatic
    fun gridCoordinate(normalized: Double, count: Int): Int {
        if (count <= 1) return 1
        return floor(normalized.coerceIn(0.0, 1.0) * count)
            .toInt()
            .coerceIn(0, count - 1) + 1
    }

    private fun descriptor(desk: ConsoleBlockEntity, socket: Int): DeskDisplayPointer? {
        if (socket != LARGE_SOCKET) return null
        val module = desk.module(socket) ?: return null
        val displayType = CCModuleTypes.displayType(module.type())
        val radarType = CCModuleTypes.radarDisplayType(module.type())
        if (displayType != DeskDisplayType.THREE_DIGIT && radarType != RadarDisplayType.LARGE) return null

        return DeskDisplayPointer(
            socket = socket,
            socketName = DeskSockets.name(socket),
            moduleId = AeroworksModuleAccess.id(module).toString(),
            u = 0.5,
            v = 0.5,
            width = DeskDisplayType.THREE_DIGIT.pixelWidth,
            height = DeskDisplayType.THREE_DIGIT.pixelHeight
        )
    }
}
