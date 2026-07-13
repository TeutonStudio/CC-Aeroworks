package de.teutonstudio.ccaeroworks.display

import kotlin.math.pow

object DeskDisplayFormatter {
    private val supported = ('0'..'9').toSet() + setOf('-', ' ')

    @JvmStatic
    fun normalizeText(text: String, width: Int): String {
        require(width > 0) { "width must be positive" }
        return text.take(width).map { if (it in supported) it else ' ' }.joinToString("")
    }

    @JvmStatic
    fun formatNumber(value: Double, width: Int, zeroPad: Boolean): String {
        require(value.isFinite()) { "value must be finite" }
        require(width > 0) { "width must be positive" }
        val integral = value.toLong()
        val maximum = 10.0.pow(width).toLong() - 1L
        val minimum = if (width == 1) 0L else -(10.0.pow(width - 1).toLong() - 1L)
        val clamped = integral.coerceIn(minimum, maximum)
        if (!zeroPad) return clamped.toString()
        return if (clamped < 0) {
            "-" + (-clamped).toString().padStart(width - 1, '0')
        } else {
            clamped.toString().padStart(width, '0')
        }
    }
}
