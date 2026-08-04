package de.teutonstudio.ccaeroworks.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeverAccumulatorTest {
    @Test fun `clamps initial and accumulated values to configured range`() {
        val accumulator = LeverAccumulator(99)
        assertEquals(15.0, accumulator.value)
        assertEquals(15, accumulator.apply(100.0, 1.0, false))
        assertEquals(-15, accumulator.apply(-100.0, 1.0, false))
    }

    @Test fun `retains fractional mouse movement`() {
        val accumulator = LeverAccumulator(0)
        assertEquals(0, accumulator.apply(0.2, 1.0, false))
        assertEquals(0, accumulator.apply(0.2, 1.0, false))
        assertEquals(1, accumulator.apply(0.2, 1.0, false))
    }

    @Test fun `applies sensitivity before rounding`() {
        val accumulator = LeverAccumulator(0)
        assertEquals(1, accumulator.apply(0.25, 4.0, false))
        assertEquals(3, accumulator.apply(0.5, 4.0, false))
    }

    @Test fun `supports inverted mouse Y`() {
        val accumulator = LeverAccumulator(0)
        assertEquals(-1, accumulator.apply(1.0, 1.0, true))
    }
}
