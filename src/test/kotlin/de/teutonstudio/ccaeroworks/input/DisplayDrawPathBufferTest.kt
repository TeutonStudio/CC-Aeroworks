package de.teutonstudio.ccaeroworks.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayDrawPathBufferTest {
    @Test
    fun `keeps path bounded and preserves endpoints`() {
        val buffer = DisplayDrawPathBuffer(maxSamples = 4)
        repeat(10) { index ->
            buffer.record(sample(index / 10.0, 0.5))
        }

        val drained = buffer.drain()

        assertEquals(4, drained.size)
        assertEquals(0.0, drained.first().u, 1.0e-12)
        assertEquals(0.9, drained.last().u, 1.0e-12)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `retains a visible bend when reducing dense path`() {
        val buffer = DisplayDrawPathBuffer(maxSamples = 4)
        listOf(
            0.0 to 0.0,
            0.1 to 0.0,
            0.2 to 0.0,
            0.3 to 0.4,
            0.4 to 0.0,
            0.5 to 0.0,
            0.6 to 0.0
        ).forEach { (u, v) -> buffer.record(sample(u, v)) }

        val drained = buffer.drain()

        assertTrue(drained.any { it.v == 0.4 }, "sharp bend should survive reduction")
    }

    @Test
    fun `replaces duplicate position with newest tangent metadata`() {
        val buffer = DisplayDrawPathBuffer(maxSamples = 4)
        buffer.record(sample(0.2, 0.3, directionU = 1.0, speed = 1.0))
        buffer.record(sample(0.2, 0.3, directionU = -1.0, speed = 2.0))

        val drained = buffer.drain()

        assertEquals(1, drained.size)
        assertEquals(-1.0, drained.single().directionU, 0.0)
        assertEquals(2.0, drained.single().speed, 0.0)
    }

    private fun sample(
        u: Double,
        v: Double,
        directionU: Double = 1.0,
        speed: Double = 1.0
    ): DisplayPointerPathSample = DisplayPointerPathSample(
        u = u,
        v = v,
        directionU = directionU,
        directionV = 0.0,
        speed = speed
    )
}
