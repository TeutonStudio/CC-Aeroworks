package de.teutonstudio.ccaeroworks.computer.channel

import kotlin.math.roundToInt

/**
 * Redstone-facing representation for Aeroworks' signed continuous controls.
 *
 * The low-level controls API deliberately remains native -15..15. The unified channel layer uses
 * ordinary redstone 0..15, with 8 as the exact neutral point so scripts and the GUI never expose a
 * negative "redstone" signal.
 */
object ChannelSignalMapping {
    @JvmStatic
    fun fromControl(value: Int): Int {
        val native = value.coerceIn(-15, 15)
        return if (native <= 0) {
            ((native + 15) * 8.0 / 15.0).roundToInt().coerceIn(0, 8)
        } else {
            (8 + native * 7.0 / 15.0).roundToInt().coerceIn(8, 15)
        }
    }

    @JvmStatic
    fun toControl(signal: Int): Int {
        val redstone = signal.coerceIn(0, 15)
        return if (redstone <= 8) {
            (-15 + redstone * 15.0 / 8.0).roundToInt().coerceIn(-15, 0)
        } else {
            ((redstone - 8) * 15.0 / 7.0).roundToInt().coerceIn(0, 15)
        }
    }
}
