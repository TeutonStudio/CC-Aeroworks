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

data class DeskDisplayTouch(
    val socket: Int,
    val socketName: String,
    val moduleId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

object DeskDisplayTouchResolver {
    private const val LARGE_SOCKET = 2

    // The large display and large radar share the three-digit module shell.
    // These bounds are the top surface of models/block/module/three_digit_display.json.
    private const val MIN_X = 3.0 / 16.0
    private const val MAX_X = 13.0 / 16.0
    private const val MIN_Z = 4.5 / 16.0
    private const val MAX_Z = 11.5 / 16.0

    @JvmStatic
    fun resolve(desk: ConsoleBlockEntity, hitLocation: Vec3): DeskDisplayTouch? {
        val module = desk.module(LARGE_SOCKET) ?: return null
        val displayType = CCModuleTypes.displayType(module.type())
        val radarType = CCModuleTypes.radarDisplayType(module.type())
        if (displayType != DeskDisplayType.THREE_DIGIT && radarType != RadarDisplayType.LARGE) {
            return null
        }

        val socket = desk.sockets().getOrNull(LARGE_SOCKET) ?: return null
        val inverse = Matrix4f()
            .translate(0.5f, 0.5f, 0.5f)
            .rotate(ConsoleBlock.rotationFor(desk.blockState))
            .translate(
                (socket.offset().x - 0.5).toFloat(),
                (socket.offset().y - 0.5).toFloat(),
                (socket.offset().z - 0.5).toFloat()
            )
            .rotate(socket.orientation())
            .translate(-0.5f, 0.0f, -0.5f)
            .invert()

        val local = Vector3f(
            (hitLocation.x - desk.blockPos.x).toFloat(),
            (hitLocation.y - desk.blockPos.y).toFloat(),
            (hitLocation.z - desk.blockPos.z).toFloat()
        )
        inverse.transformPosition(local)

        val localX = local.x.toDouble()
        val localZ = local.z.toDouble()
        if (localX !in MIN_X..MAX_X || localZ !in MIN_Z..MAX_Z) return null

        val width = DeskDisplayType.THREE_DIGIT.pixelWidth
        val height = DeskDisplayType.THREE_DIGIT.pixelHeight
        val normalizedX = (localX - MIN_X) / (MAX_X - MIN_X)
        val normalizedY = (MAX_Z - localZ) / (MAX_Z - MIN_Z)

        return DeskDisplayTouch(
            socket = LARGE_SOCKET,
            socketName = DeskSockets.name(LARGE_SOCKET),
            moduleId = AeroworksModuleAccess.id(module).toString(),
            x = gridCoordinate(normalizedX, width),
            y = gridCoordinate(normalizedY, height),
            width = width,
            height = height
        )
    }

    internal fun gridCoordinate(normalized: Double, count: Int): Int {
        if (count <= 1) return 1
        return (floor(normalized.coerceIn(0.0, 1.0) * count).toInt())
            .coerceIn(0, count - 1) + 1
    }
}
