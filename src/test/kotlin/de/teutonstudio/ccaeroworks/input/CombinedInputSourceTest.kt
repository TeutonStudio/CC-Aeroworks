package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.channel.ControlChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombinedInputSourceTest {
    @Test
    fun `pedals and copper control axes are discovered from channel type`() {
        val channels = listOf(
            axis("pedal", horizontal = false),
            axis("turn", horizontal = true),
            axis("pitch", horizontal = false)
        )

        assertEquals(
            listOf("pedal", "turn", "pitch"),
            CombinedInputSource.axisChannelIds(channels)
        )
    }

    @Test
    fun `composed module paths survive axis discovery`() {
        val nested = axis("pedal", horizontal = false).withId("rudder_left/pedal")

        assertTrue(nested is ControlChannel.Axis)
        assertEquals(
            listOf("rudder_left/pedal"),
            CombinedInputSource.axisChannelIds(listOf(nested))
        )
    }

    @Test
    fun `button-only modules do not offer combined input`() {
        val button = ControlChannel.Button("press", "test.button", "key.keyboard.space")

        assertTrue(CombinedInputSource.axisChannelIds(listOf(button)).isEmpty())
    }

    @Test
    fun `native axis metadata selects horizontal or vertical mouse motion`() {
        assertEquals(CombinedInputSource.MouseAxis.X, CombinedInputSource.mouseAxis(axis("turn", horizontal = true)))
        assertEquals(CombinedInputSource.MouseAxis.Y, CombinedInputSource.mouseAxis(axis("pedal", horizontal = false)))
    }

    private fun axis(id: String, horizontal: Boolean) = ControlChannel.Axis(
        id,
        "test.$id",
        true,
        false,
        false,
        horizontal,
        false,
        "",
        "",
        ""
    )
}
