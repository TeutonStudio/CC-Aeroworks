package de.teutonstudio.ccaeroworks.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombinedInputSourceTest {
    @Test
    fun `continuous controls expose their combined channels`() {
        assertEquals(listOf(CombinedInputSource.LEVER_CHANNEL), CombinedInputSource.channelsFor("aeroworks:lever"))
        assertEquals(listOf(CombinedInputSource.X_CHANNEL, CombinedInputSource.Y_CHANNEL), CombinedInputSource.channelsFor("aeroworks:joystick"))
        assertEquals(listOf("wheel"), CombinedInputSource.channelsFor("aeroworks:wheel"))
        assertEquals(listOf("turn", "pitch"), CombinedInputSource.channelsFor("aeroworks:yoke"))
        assertEquals(
            listOf("red", "amber", "green", "blue"),
            CombinedInputSource.channelsFor("aeroworks:throttle_quadrant")
        )
    }

    @Test
    fun `display pointer modules expose combined x y channels`() {
        val pointerChannels = listOf(CombinedInputSource.X_CHANNEL, CombinedInputSource.Y_CHANNEL)

        assertEquals(pointerChannels, CombinedInputSource.channelsFor("cc_aeroworks:three_digit_display"))
        assertEquals(pointerChannels, CombinedInputSource.channelsFor("cc_aeroworks:large_radar_display"))
    }

    @Test
    fun `unrelated modules do not offer combined input`() {
        assertTrue(CombinedInputSource.channelsFor("aeroworks:button").isEmpty())
        assertTrue(CombinedInputSource.channelsFor("cc_aeroworks:computer_control_desk").isEmpty())
        assertTrue(CombinedInputSource.channelsFor("example:unrelated").isEmpty())
    }

    @Test
    fun `mouse axes follow combined channel semantics`() {
        assertEquals(CombinedInputSource.MouseAxis.X, CombinedInputSource.mouseAxis(CombinedInputSource.X_CHANNEL))
        assertEquals(CombinedInputSource.MouseAxis.X, CombinedInputSource.mouseAxis("wheel"))
        assertEquals(CombinedInputSource.MouseAxis.X, CombinedInputSource.mouseAxis("turn"))
        assertEquals(CombinedInputSource.MouseAxis.Y, CombinedInputSource.mouseAxis(CombinedInputSource.Y_CHANNEL))
        assertEquals(CombinedInputSource.MouseAxis.Y, CombinedInputSource.mouseAxis("pitch"))
        assertEquals(CombinedInputSource.MouseAxis.Y, CombinedInputSource.mouseAxis(CombinedInputSource.LEVER_CHANNEL))
    }
}
