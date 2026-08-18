package de.teutonstudio.ccaeroworks.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.min

class DisplayPointerMotionTest {
    @Test
    fun `normalizes first non-zero display velocity`() {
        val motion = DisplayPointerMotion()

        motion.observe(0.03, 0.04, 0.01)

        assertEquals(0.6, motion.directionU, 1.0e-12)
        assertEquals(0.8, motion.directionV, 1.0e-12)
        assertEquals(5.0, motion.speed, 1.0e-12)
    }

    @Test
    fun `speed is independent from sampling interval before filtering begins`() {
        val slowSampling = DisplayPointerMotion()
        val fastSampling = DisplayPointerMotion()

        slowSampling.observe(0.02, 0.0, 0.02)
        fastSampling.observe(0.005, 0.0, 0.005)

        assertEquals(1.0, slowSampling.speed, 1.0e-12)
        assertEquals(1.0, fastSampling.speed, 1.0e-12)
    }

    @Test
    fun `filtered motion is independent from frame rate over equal wall clock time`() {
        val rates = listOf(20.0, 60.0, 144.0, 240.0)
        val results = rates.map(::runTurnAtRate)
        val reference = results.first()

        results.drop(1).forEach { result ->
            assertEquals(reference.directionU, result.directionU, 1.0e-10)
            assertEquals(reference.directionV, result.directionV, 1.0e-10)
            assertEquals(reference.speed, result.speed, 1.0e-10)
        }
    }

    @Test
    fun `zero motion preserves release direction but breaks smoothing history`() {
        val motion = DisplayPointerMotion()
        motion.observe(0.01, 0.0, 0.01)
        val previousSpeed = motion.speed

        motion.observe(0.0, 0.0, 0.01)

        assertEquals(1.0, motion.directionU, 1.0e-12)
        assertEquals(0.0, motion.directionV, 1.0e-12)
        assertEquals(previousSpeed, motion.speed, 1.0e-12)

        motion.observe(-0.0025, 0.0, 0.01)
        assertEquals(-1.0, motion.directionU, 1.0e-12)
        assertEquals(0.0, motion.directionV, 1.0e-12)
        assertEquals(0.25, motion.speed, 1.0e-12)
    }

    @Test
    fun `reset clears direction history`() {
        val motion = DisplayPointerMotion()
        motion.observe(0.0, -0.02, 0.01)

        motion.reset()

        assertEquals(0.0, motion.directionU, 0.0)
        assertEquals(0.0, motion.directionV, 0.0)
        assertEquals(0.0, motion.speed, 0.0)
    }

    private fun runTurnAtRate(rate: Double): DisplayPointerMotion {
        val motion = DisplayPointerMotion()
        motion.observe(0.01, 0.0, 0.01)

        var remaining = 0.12
        val step = 1.0 / rate
        while (remaining > 1.0e-12) {
            val dt = min(step, remaining)
            motion.observe(0.0, dt, dt)
            remaining -= dt
        }
        return motion
    }
}
