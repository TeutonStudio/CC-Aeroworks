package de.teutonstudio.ccaeroworks.client.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayPointerHudLayoutTest {
    @Test
    fun `landscape display retains its aspect ratio in the HUD`() {
        assertEquals(DisplayPointerHudLayout.Surface(57, 40), DisplayPointerHudLayout.surface(160, 112))
        assertEquals(DisplayPointerHudLayout.Surface(64, 16), DisplayPointerHudLayout.surface(400, 100))
        assertEquals(DisplayPointerHudLayout.Surface(40, 40), DisplayPointerHudLayout.surface(112, 112))
    }

    @Test
    fun `portrait display fits within the maximum height`() {
        assertEquals(DisplayPointerHudLayout.Surface(28, 40), DisplayPointerHudLayout.surface(112, 160))
        assertEquals(DisplayPointerHudLayout.Surface(10, 40), DisplayPointerHudLayout.surface(100, 400))
        assertEquals(DisplayPointerHudLayout.Surface(20, 10), DisplayPointerHudLayout.surface(160, 80, 20, 30))
    }

    @Test
    fun `extreme aspect ratios retain at least one pixel in each dimension`() {
        assertEquals(DisplayPointerHudLayout.Surface(64, 1), DisplayPointerHudLayout.surface(Int.MAX_VALUE, 1))
        assertEquals(DisplayPointerHudLayout.Surface(1, 40), DisplayPointerHudLayout.surface(1, Int.MAX_VALUE))
        assertEquals(DisplayPointerHudLayout.Surface(1, 1), DisplayPointerHudLayout.surface(160, 112, 1, 1))
        assertEquals(DisplayPointerHudLayout.Surface(1, 1), DisplayPointerHudLayout.surface(0, -1, 0, -1))
    }

    @Test
    fun `marker endpoints keep the complete marker inside the surface`() {
        assertEquals(2, DisplayPointerHudLayout.marker(0.0, 64))
        assertEquals(61, DisplayPointerHudLayout.marker(1.0, 64))
        assertEquals(2, DisplayPointerHudLayout.marker(-10.0, 64))
        assertEquals(61, DisplayPointerHudLayout.marker(10.0, 64))
        assertEquals(2, DisplayPointerHudLayout.marker(Double.NEGATIVE_INFINITY, 64))
        assertEquals(61, DisplayPointerHudLayout.marker(Double.POSITIVE_INFINITY, 64))
    }

    @Test
    fun `fractional positions map continuously across the available center span`() {
        assertEquals(17, DisplayPointerHudLayout.marker(0.25, 64))
        assertEquals(32, DisplayPointerHudLayout.marker(0.5, 64))
        assertEquals(46, DisplayPointerHudLayout.marker(0.75, 64))
        assertEquals(20, DisplayPointerHudLayout.marker(0.5, 40))
        assertEquals(32, DisplayPointerHudLayout.marker(Double.NaN, 64))
    }

    @Test
    fun `small surfaces keep marker centers inside their bounds`() {
        for (extent in 1..4) {
            assertEquals(0, DisplayPointerHudLayout.marker(-1.0, extent))
            assertEquals(extent - 1, DisplayPointerHudLayout.marker(2.0, extent))
            assertTrue(DisplayPointerHudLayout.marker(0.5, extent) in 0 until extent)
        }
        assertEquals(2, DisplayPointerHudLayout.marker(0.0, 5))
        assertEquals(2, DisplayPointerHudLayout.marker(1.0, 5))
        assertEquals(0, DisplayPointerHudLayout.marker(0.5, 0))
    }
}
