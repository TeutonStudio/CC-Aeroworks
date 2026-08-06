package de.teutonstudio.ccaeroworks.display

import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

object RadarDisplayRaster {
    private const val STALE_AFTER_TICKS: Long = 20

    @JvmStatic
    fun render(
        type: RadarDisplayType,
        snapshot: RadarDisplaySnapshot?,
        gameTime: Long
    ): DeskDisplayPixels {
        val width = type.displayType.pixelWidth
        val height = type.displayType.pixelHeight
        val pixelCount = safePixelCount(width, height)
        val enabled = BooleanArray(pixelCount)

        fun set(x: Int, y: Int) {
            if (x in 0 until width && y in 0 until height) enabled[y * width + x] = true
        }

        if (!isFresh(snapshot, gameTime)) {
            drawMissingLink(width, height, ::set)
        } else {
            val active = requireNotNull(snapshot)
            val centerX = (width - 1) / 2
            val centerY = (height - 1) / 2
            set(centerX, centerY)
            if (width > 2) {
                set(centerX - 1, centerY)
                set(centerX + 1, centerY)
            }
            if (height > 2) {
                set(centerX, centerY - 1)
                set(centerX, centerY + 1)
            }

            active.tracks.forEach { track ->
                val point = project(track.position, active.center, active.range, width, height) ?: return@forEach
                set(point.first, point.second)
                if (track.id == active.selectedTrackId) {
                    set(point.first - 1, point.second)
                    set(point.first + 1, point.second)
                    set(point.first, point.second - 1)
                    set(point.first, point.second + 1)
                }
            }
        }

        val bits = buildString(pixelCount) {
            enabled.forEach { append(if (it) '1' else '0') }
        }
        return DeskDisplayPixels(width, height, bits)
    }

    private fun isFresh(snapshot: RadarDisplaySnapshot?, gameTime: Long): Boolean {
        if (snapshot == null || !snapshot.connected || snapshot.range <= 0.0) return false
        val age = gameTime - snapshot.updatedAt
        return age in 0..STALE_AFTER_TICKS
    }

    private fun project(
        position: Vec3,
        center: Vec3,
        range: Double,
        width: Int,
        height: Int
    ): Pair<Int, Int>? {
        val normalizedX = (position.x - center.x) / range
        val normalizedZ = (position.z - center.z) / range
        if (normalizedX !in -1.0..1.0 || normalizedZ !in -1.0..1.0) return null
        val x = ((normalizedX + 1.0) * 0.5 * (width - 1)).roundToInt()
        val y = ((1.0 - (normalizedZ + 1.0) * 0.5) * (height - 1)).roundToInt()
        return x to y
    }

    private fun drawMissingLink(width: Int, height: Int, set: (Int, Int) -> Unit) {
        if (width == 1 || height == 1) {
            set(width / 2, height / 2)
            return
        }
        for (x in 0 until width) {
            val y = x * (height - 1) / (width - 1)
            set(x, y)
            set(x, height - 1 - y)
        }
    }

    private fun safePixelCount(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "display dimensions must be positive" }
        val count = width.toLong() * height.toLong()
        require(count <= Int.MAX_VALUE) { "display raster exceeds the JVM array limit" }
        return count.toInt()
    }
}
