package de.teutonstudio.ccaeroworks.client.hud

import com.mred231.aeroworks.content.controls.channel.ControlChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlHudHintsTest {
    @Test
    fun `button panels and composed child buttons respect individual HUD visibility`() {
        val channels = listOf(
            ControlChannel.Button("panel/button1", "test.one", "key.keyboard.a"),
            ControlChannel.Button("panel/button2", "test.two", "key.keyboard.b"),
            ControlChannel.Button("yoke/head/horn", "test.horn", "key.keyboard.h")
        )
        assertEquals(listOf(0, 2), ControlHudHints.buttonChannels(channels) { it != "panel/button2" })
        assertTrue(ControlHudHints.buttonChannels(channels) { false }.isEmpty())
    }

    @Test
    fun `shared combined hold key appears once with a two axis mouse glyph`() {
        val rows = ControlHudHints.rows(listOf(combined("X", "key.keyboard.e"), combined("Y", "key.keyboard.e")))
        assertEquals(1, rows.size)
        assertEquals("", rows.single().label)
        assertEquals(listOf("key.keyboard.e", "mouse.xy"), rows.single().parts.mapNotNull { it.source })
        assertTrue(rows.single().parts.first().held)
        assertTrue(rows.single().parts.all { it.source != null || it.text == "+" })
    }

    @Test
    fun `different keys hidden axes and unbound channels never become a shared XY hint`() {
        val separate = ControlHudHints.rows(listOf(combined("X", "key.keyboard.e"), combined("Y", "key.keyboard.r")))
        assertEquals(listOf("mouse.x", "mouse.y"), separate.map { it.parts.last().source })
        val one = ControlHudHints.rows(listOf(combined("Y", "key.keyboard.e")))
        assertEquals("mouse.y", one.single().parts.last().source)
        assertEquals(2, ControlHudHints.rows(listOf(combined("X", ""), combined("Y", ""))).size)
    }

    @Test
    fun `analog input hides inactive digital bindings and buttons retain their own input`() {
        val rows = ControlHudHints.rows(listOf(
            ControlHudHints.Binding("Steering", "X", "gamepad.left_x", "key.keyboard.a", "key.keyboard.d"),
            ControlHudHints.Binding("Horn", positive = "gamepad.btn.a"),
            ControlHudHints.Binding("Throttle", "Y", negative = "key.keyboard.s", positive = "key.keyboard.w")
        ))
        assertEquals(listOf("gamepad.left_x"), rows[0].parts.mapNotNull { it.source })
        assertEquals(listOf("gamepad.btn.a"), rows[1].parts.mapNotNull { it.source })
        assertEquals(listOf("key.keyboard.s", "key.keyboard.w"), rows[2].parts.mapNotNull { it.source })
        assertEquals(listOf("↑", "↓"), rows[2].parts.filter { it.source == null }.map { it.text })
    }

    @Test
    fun `compact button hints retain actual channel indices for state indicators`() {
        val rows = ControlHudHints.rows(listOf(
            ControlHudHints.Binding("Attached horn button", positive = "key.keyboard.h", channel = 7),
            ControlHudHints.Binding("Attached second button", positive = "key.keyboard.b", channel = 9)
        ))
        assertEquals(listOf("1", "2"), rows.map { it.label })
        assertEquals(listOf(listOf(7), listOf(9)), rows.map { it.buttons })
        assertEquals("", ControlHudHints.rows(listOf(ControlHudHints.Binding("Horn", positive = "key.keyboard.h", channel = 7))).single().label)
    }

    private fun combined(axis: String, key: String) =
        ControlHudHints.Binding(axis, axis, negative = key, combined = true)
}
