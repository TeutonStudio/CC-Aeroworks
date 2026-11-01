package de.teutonstudio.ccaeroworks.client.hud

import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InputPromptIconsTest {
    @Test
    fun `keyboard glyphs follow the displayed character on non US layouts`() {
        assertEquals("keyboard_z", InputPromptIcons.glyph("key.keyboard.y", "Z").asset)
        assertEquals("keyboard_y", InputPromptIcons.glyph("key.keyboard.z", "Y").asset)
        assertEquals("keyboard_plus", InputPromptIcons.glyph("key.keyboard.right.bracket", "+").asset)
        assertEquals("ü", InputPromptIcons.glyph("key.keyboard.left.bracket", "ü").caption)
    }

    @Test
    fun `left right modifiers and numpad keys remain distinguishable`() {
        assertEquals("L", InputPromptIcons.glyph("key.keyboard.left.shift").caption)
        assertEquals("R", InputPromptIcons.glyph("key.keyboard.right.shift").caption)
        assertEquals("keyboard_3", InputPromptIcons.glyph("key.keyboard.keypad.3").asset)
        assertEquals("N", InputPromptIcons.glyph("key.keyboard.keypad.3").caption)
        assertEquals("F13", InputPromptIcons.glyph("key.keyboard.f13", "F13").caption)
    }

    @Test
    fun `mouse and both controller sticks retain distinct axis glyphs`() {
        val axes = listOf("mouse.x", "mouse.y", "mouse.xy", "gamepad.left_x", "gamepad.left_y", "gamepad.right_x", "gamepad.right_y")
        assertEquals(axes.size, axes.map { InputPromptIcons.glyph(it).asset }.distinct().size)
        assertNotEquals(InputPromptIcons.glyph("gamepad.left_trigger"), InputPromptIcons.glyph("gamepad.right_trigger"))
        assertNotEquals(InputPromptIcons.glyph("mouse.wheel.up"), InputPromptIcons.glyph("mouse.wheel.down"))
    }

    @Test
    fun `raw joystick indices survive without assuming a gamepad layout`() {
        assertEquals(InputPromptIcons.Glyph("generic_stick", "A3"), InputPromptIcons.glyph("joystick.device.guid.axis.3"))
        assertEquals(InputPromptIcons.Glyph("generic_button_circle", "B12"), InputPromptIcons.glyph("joystick.device.guid.btn.12"))
        assertEquals(InputPromptIcons.Glyph("generic_stick_left", "H1"), InputPromptIcons.glyph("joystick.device.guid.hat.1.left"))
        assertTrue(InputPromptIcons.glyph("unknown.input", "Custom input").caption.contains("Custom"))
    }

    @Test
    fun `held input uses the negative keycap while preserving layout and modifier captions`() {
        val held = InputPromptIcons.negative(InputPromptIcons.glyph("key.keyboard.y", "Z"))
        assertEquals("keyboard_z_outline", held.asset)
        assertEquals(held, InputPromptIcons.negative(held))
        val shift = InputPromptIcons.negative(InputPromptIcons.glyph("key.keyboard.right.shift"))
        assertEquals("keyboard_shift_outline", shift.asset)
        assertEquals("R", shift.caption)
        assertEquals("mouse_left_outline", InputPromptIcons.negative(InputPromptIcons.glyph("key.mouse.left")).asset)
    }

    @Test
    fun `supported sources resolve to bundled original 64 pixel textures`() {
        val sources = buildList {
            addAll(('a'..'z').map { "key.keyboard.$it" })
            addAll((0..9).map { "key.keyboard.$it" })
            addAll((1..25).map { "key.keyboard.f$it" })
            addAll((0..9).map { "key.keyboard.keypad.$it" })
            addAll(listOf("left.shift", "right.shift", "left.control", "right.control", "left.alt", "right.alt", "left.win", "right.win",
                "space", "escape", "enter", "tab", "backspace", "insert", "delete", "right", "left", "down", "up", "page.up", "page.down",
                "home", "end", "caps.lock", "scroll.lock", "num.lock", "print.screen", "pause", "menu", "keypad.enter", "keypad.add",
                "keypad.subtract", "keypad.multiply", "keypad.divide", "keypad.decimal", "keypad.equal").map { "key.keyboard.$it" })
            addAll(listOf("mouse.x", "mouse.y", "mouse.xy", "mouse.wheel.up", "mouse.wheel.down", "key.mouse.left", "key.mouse.right", "key.mouse.middle", "key.mouse.4", "key.mouse.5", "key.mouse.6"))
            addAll(listOf("left_x", "left_y", "right_x", "right_y", "left_trigger", "right_trigger").map { "gamepad.$it" })
            addAll(listOf("a", "b", "x", "y", "left_bumper", "right_bumper", "back", "start", "guide", "left_thumb", "right_thumb", "dpad_up", "dpad_right", "dpad_down", "dpad_left").map { "gamepad.btn.$it" })
            addAll(listOf("joystick.guid.axis.0", "joystick.guid.btn.0", "joystick.guid.hat.0.up", "joystick.guid.hat.0.down", "joystick.guid.hat.0.left", "joystick.guid.hat.0.right", ""))
        }
        val assets = sources.flatMap { source ->
            val glyph = InputPromptIcons.glyph(source)
            listOf(glyph.asset, InputPromptIcons.negative(glyph).asset)
        }.distinct()
        for (asset in assets) {
            val stream = javaClass.getResourceAsStream("/assets/cc_aeroworks/textures/gui/input/$asset.png")
            assertNotNull(stream, asset)
            val image = stream.use(ImageIO::read)
            assertEquals(64, image.width, asset)
            assertEquals(64, image.height, asset)
        }
        assertNotNull(javaClass.getResourceAsStream("/META-INF/licenses/kenney-input-prompts.txt"))
    }
}
