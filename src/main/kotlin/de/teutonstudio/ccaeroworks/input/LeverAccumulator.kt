package de.teutonstudio.ccaeroworks.input

import kotlin.math.roundToInt

class LeverAccumulator(initialValue: Int, private val minimum: Int = -15, private val maximum: Int = 15) {
    var value: Double = initialValue.coerceIn(minimum, maximum).toDouble()
        private set

    fun apply(deltaY: Double, sensitivity: Double, invertY: Boolean): Int {
        val direction = if (invertY) -1.0 else 1.0
        value = (value + deltaY * sensitivity * direction).coerceIn(minimum.toDouble(), maximum.toDouble())
        return value.roundToInt().coerceIn(minimum, maximum)
    }
}
