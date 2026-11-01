package de.teutonstudio.ccaeroworks.client.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlHudLayoutTest {
    @Test
    fun `many controls on a small screen wrap without dropping or overlapping widgets`() {
        val sizes = List(24) { ControlHudLayout.Size(90 + it % 3 * 25, 80 + it % 4 * 20) }
        val layout = ControlHudLayout.arrange(sizes, 320, 240)
        assertEquals(sizes.indices.toList(), layout.placements.map { it.index })
        assertTrue(layout.placements.map { it.y }.distinct().size > 1)
        assertTrue(layout.scale > 0f && layout.scale <= 1f)
        for (a in layout.placements) {
            assertTrue(a.x * layout.scale >= 0)
            assertTrue((a.x + sizes[a.index].width) * layout.scale <= 320)
            assertTrue(a.y * layout.scale >= 0)
            assertTrue((a.y + sizes[a.index].height) * layout.scale <= 161)
            for (b in layout.placements.filter { it.index > a.index }) {
                assertTrue(a.x + sizes[a.index].width <= b.x || b.x + sizes[b.index].width <= a.x ||
                    a.y + sizes[a.index].height <= b.y || b.y + sizes[b.index].height <= a.y)
            }
        }
    }

    @Test
    fun `a wide binding fits while ordinary widgets keep their original scale`() {
        val wide = ControlHudLayout.arrange(listOf(ControlHudLayout.Size(600, 90)), 320, 240)
        assertTrue(wide.scale < 1f)
        val normal = ControlHudLayout.arrange(List(3) { ControlHudLayout.Size(100, 90) }, 854, 480)
        assertEquals(1f, normal.scale)
        assertEquals(1, normal.placements.map { it.y }.distinct().size)
        assertTrue(ControlHudLayout.arrange(emptyList(), 320, 240).placements.isEmpty())
    }

    @Test
    fun `dense layouts try extra columns to preserve readable icon size`() {
        val layout = ControlHudLayout.arrange(List(6) { ControlHudLayout.Size(100, 90) }, 320, 240)
        assertTrue(layout.scale > 0.75f)
        assertEquals(2, layout.placements.map { it.y }.distinct().size)
    }

    @Test
    fun `combined joystick binding and attached button fit on the same symbol line`() {
        val flow = ControlHudLayout.flow(listOf(ControlHudLayout.Size(42, 16), ControlHudLayout.Size(21, 16)), 112, gap = 4)
        assertEquals(16, flow.height)
        assertEquals(67, flow.width)
        assertEquals(listOf(0, 0), flow.placements.map { it.y })
    }

    @Test
    fun `nine button panel uses three compact rows instead of nine full lines`() {
        val flow = ControlHudLayout.flow(List(9) { ControlHudLayout.Size(28, 16) }, 112, gap = 4)
        assertEquals(56, flow.height)
        assertEquals(3, flow.placements.map { it.y }.distinct().size)
        assertEquals((0..8).toList(), flow.placements.map { it.index })
    }
}
