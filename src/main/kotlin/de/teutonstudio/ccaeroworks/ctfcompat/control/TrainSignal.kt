package de.teutonstudio.ccaeroworks.ctfcompat.control

import kotlin.math.abs

data class TrainSignal(
    val throttle: Float = 0.0f,
    /** Public convention: negative is left, positive is right. */
    val steer: Float = 0.0f,
    val brake: Boolean = false,
    val station: Boolean = false
)

object TrainSignalMixer {
    const val STEER_THRESHOLD = 1.0f / 3.0f

    fun merge(first: TrainSignal, second: TrainSignal): TrainSignal = TrainSignal(
        throttle = strongest(first.throttle, second.throttle),
        steer = strongest(first.steer, second.steer),
        brake = first.brake || second.brake,
        station = first.station || second.station
    )

    fun mergeAll(signals: Iterable<TrainSignal>): TrainSignal {
        val values = signals.toList()
        return TrainSignal(
            throttle = strongest(values.map(TrainSignal::throttle)),
            steer = strongest(values.map(TrainSignal::steer)),
            brake = values.any(TrainSignal::brake),
            station = values.any(TrainSignal::station)
        )
    }

    fun strongest(first: Float, second: Float): Float {
        val firstMagnitude = abs(first)
        val secondMagnitude = abs(second)
        return when {
            secondMagnitude > firstMagnitude -> second
            firstMagnitude > secondMagnitude -> first
            first == 0.0f -> second
            second == 0.0f -> first
            first.sign != second.sign -> 0.0f
            else -> first
        }
    }

    fun strongest(values: Iterable<Float>): Float {
        var magnitude = 0.0f
        var positive = false
        var negative = false
        values.forEach { value ->
            val candidate = abs(value)
            when {
                candidate > magnitude -> {
                    magnitude = candidate
                    positive = value > 0.0f
                    negative = value < 0.0f
                }
                candidate == magnitude && candidate != 0.0f -> {
                    positive = positive || value > 0.0f
                    negative = negative || value < 0.0f
                }
            }
        }
        return when {
            positive && negative -> 0.0f
            positive -> magnitude
            negative -> -magnitude
            else -> 0.0f
        }
    }

    /** CTF 0.1.47 uses +1 for left and -1 for right. */
    fun ctfSteer(value: Float): Int = when {
        value <= -STEER_THRESHOLD -> 1
        value >= STEER_THRESHOLD -> -1
        else -> 0
    }

    private val Float.sign: Int
        get() = when {
            this < 0.0f -> -1
            this > 0.0f -> 1
            else -> 0
        }
}
