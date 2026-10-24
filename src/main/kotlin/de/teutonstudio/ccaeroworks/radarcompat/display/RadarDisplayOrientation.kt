package de.teutonstudio.ccaeroworks.radarcompat.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import net.minecraft.core.Direction
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

object RadarDisplayOrientation {
    fun effectiveFacing(desk: ConsoleBlockEntity, socketIndex: Int): Direction {
        val socket = desk.sockets().getOrNull(socketIndex) ?: return Direction.NORTH
        val rotation = Quaternionf(ConsoleBlock.rotationFor(desk.blockState)).mul(socket.orientation())
        val forward = rotation.transform(Vector3f(0.0f, 0.0f, -1.0f))
        return if (abs(forward.x) > abs(forward.z)) {
            if (forward.x >= 0.0f) Direction.EAST else Direction.WEST
        } else {
            if (forward.z >= 0.0f) Direction.SOUTH else Direction.NORTH
        }
    }
}
