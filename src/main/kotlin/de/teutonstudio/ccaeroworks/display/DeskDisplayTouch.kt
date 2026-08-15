package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import net.minecraft.world.phys.Vec3

data class DeskDisplayTouch(
    val socket: Int,
    val socketName: String,
    val moduleId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val u: Double = if (width > 0) ((x - 0.5) / width.toDouble()).coerceIn(0.0, 1.0) else 0.5,
    val v: Double = if (height > 0) ((y - 0.5) / height.toDouble()).coerceIn(0.0, 1.0) else 0.5
)

object DeskDisplayTouchResolver {
    @JvmStatic
    fun resolve(desk: ConsoleBlockEntity, hitLocation: Vec3): DeskDisplayTouch? =
        DeskDisplayGeometry.resolveHit(desk, hitLocation)?.toTouch()

    internal fun gridCoordinate(normalized: Double, count: Int): Int =
        DeskDisplayGeometry.gridCoordinate(normalized, count)
}
