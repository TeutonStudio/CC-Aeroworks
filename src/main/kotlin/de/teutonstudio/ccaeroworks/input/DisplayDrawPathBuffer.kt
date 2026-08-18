package de.teutonstudio.ccaeroworks.input

import kotlin.math.abs
import kotlin.math.hypot

/** One high-frequency virtual-finger sample before it is batched into the 20 Hz draw stream. */
data class DisplayPointerPathSample(
    val u: Double,
    val v: Double,
    val directionU: Double,
    val directionV: Double,
    val speed: Double
)

/**
 * Bounded sub-tick path buffer.
 *
 * The newest point and the oldest pending point are always retained. If more than [maxSamples]
 * arrive before the next network flush, the least geometrically significant interior point is
 * removed. This preserves bends much better than blindly taking every Nth frame while keeping the
 * packet size bounded for very high frame rates.
 */
class DisplayDrawPathBuffer(
    private val maxSamples: Int = 16
) {
    init {
        require(maxSamples >= 2) { "draw path buffer must retain at least two samples" }
    }

    private val pending: MutableList<DisplayPointerPathSample> = arrayListOf()

    val size: Int
        get() = pending.size

    fun isEmpty(): Boolean = pending.isEmpty()

    fun record(sample: DisplayPointerPathSample) {
        if (!sample.u.isFinite() || !sample.v.isFinite() ||
            !sample.directionU.isFinite() || !sample.directionV.isFinite() || !sample.speed.isFinite()
        ) return

        val last = pending.lastOrNull()
        if (last != null && last.u == sample.u && last.v == sample.v) {
            // Keep the newest tangent/speed at an unchanged raster-space position.
            pending[pending.lastIndex] = sample
            return
        }

        pending += sample
        while (pending.size > maxSamples) removeLeastImportantInteriorPoint()
    }

    fun drain(): List<DisplayPointerPathSample> {
        if (pending.isEmpty()) return emptyList()
        val result = pending.toList()
        pending.clear()
        return result
    }

    fun clear() = pending.clear()

    private fun removeLeastImportantInteriorPoint() {
        if (pending.size <= 2) return

        var removeIndex = 1
        var leastImportance = Double.POSITIVE_INFINITY
        for (index in 1 until pending.lastIndex) {
            val previous = pending[index - 1]
            val current = pending[index]
            val next = pending[index + 1]

            val chordU = next.u - previous.u
            val chordV = next.v - previous.v
            val chordLength = hypot(chordU, chordV)
            val doubledArea = abs(
                (current.u - previous.u) * chordV -
                    (current.v - previous.v) * chordU
            )
            val geometricDistance = if (chordLength > 1.0e-12) {
                doubledArea / chordLength
            } else {
                hypot(current.u - previous.u, current.v - previous.v)
            }

            val directionChange = directionDifference(previous, current) + directionDifference(current, next)
            val importance = geometricDistance + directionChange * 0.002
            if (importance < leastImportance) {
                leastImportance = importance
                removeIndex = index
            }
        }
        pending.removeAt(removeIndex)
    }

    private fun directionDifference(a: DisplayPointerPathSample, b: DisplayPointerPathSample): Double {
        val aLength = hypot(a.directionU, a.directionV)
        val bLength = hypot(b.directionU, b.directionV)
        if (aLength <= 1.0e-12 || bLength <= 1.0e-12) return 0.0
        val dot = ((a.directionU / aLength) * (b.directionU / bLength) +
            (a.directionV / aLength) * (b.directionV / bLength)).coerceIn(-1.0, 1.0)
        return 1.0 - dot
    }
}
