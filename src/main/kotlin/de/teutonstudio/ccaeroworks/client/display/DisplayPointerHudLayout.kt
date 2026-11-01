package de.teutonstudio.ccaeroworks.client.display

import kotlin.math.min
import kotlin.math.roundToInt

/** Display-space geometry shared by the pointer HUD and its renderer. */
object DisplayPointerHudLayout {
    data class Surface(val width: Int, val height: Int)

    /** Fit the display aspect ratio inside the available HUD area. */
    fun surface(pixelWidth: Int, pixelHeight: Int, maxWidth: Int = 64, maxHeight: Int = 40): Surface {
        val width = pixelWidth.coerceAtLeast(1)
        val height = pixelHeight.coerceAtLeast(1)
        val availableWidth = maxWidth.coerceAtLeast(1)
        val availableHeight = maxHeight.coerceAtLeast(1)
        val scale = min(availableWidth.toDouble() / width, availableHeight.toDouble() / height)
        return Surface(
            (width * scale).toInt().coerceIn(1, availableWidth),
            (height * scale).toInt().coerceIn(1, availableHeight)
        )
    }

    /**
     * Map a normalized coordinate onto marker centers, keeping a five-pixel marker inside the
     * surface where possible. Both U and V increase here; the renderer handles screen-Y inversion.
     */
    fun marker(coordinate: Double, extent: Int): Int {
        val size = extent.coerceAtLeast(1)
        val first = if (size >= 5) 2 else 0
        val last = if (size >= 5) size - 3 else size - 1
        val normalized = if (coordinate.isNaN()) 0.5 else coordinate.coerceIn(0.0, 1.0)
        return (first + normalized * (last - first)).roundToInt().coerceIn(first, last)
    }
}
