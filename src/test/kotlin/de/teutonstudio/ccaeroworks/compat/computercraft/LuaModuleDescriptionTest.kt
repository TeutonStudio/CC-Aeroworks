package de.teutonstudio.ccaeroworks.compat.computercraft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LuaModuleDescriptionTest {
    @Test fun `filters invalid sockets and preserves socket order`() {
        val modules = listOf(
            LuaModuleSnapshot(3, "test:three", "module"),
            LuaModuleSnapshot(-1, "test:negative", "module"),
            LuaModuleSnapshot(1, "test:one", "module"),
            LuaModuleSnapshot(4, "test:outside", "module")
        )
        assertEquals(listOf(1, 3), LuaModuleDescription.validSockets(4, modules).map { it.socket })
    }

    @Test fun `describes scalar input for Lua`() {
        val result = LuaModuleDescription.describe(
            LuaModuleSnapshot(0, "aeroworks:lever", "lever", mapOf("lever" to 4))
        )
        assertEquals(4, result["value"])
        assertEquals("left", result["socketName"])
        assertFalse(result["display"] as Boolean)
    }

    @Test fun `describes multi channel input without losing channel names`() {
        val values = linkedMapOf("x" to 3, "y" to -2)
        val result = LuaModuleDescription.describe(
            LuaModuleSnapshot(1, "aeroworks:joystick", "joystick", values)
        )
        assertEquals(values, result["values"])
        assertEquals("right", result["socketName"])
    }

    @Test fun `describes text and pixel displays with resolution constants`() {
        val text = LuaModuleDescription.describe(
            LuaModuleSnapshot(
                2,
                "cc_aeroworks:three_digit_display",
                "display",
                displayWidth = 3,
                displayText = "007",
                displayPixelWidth = 13,
                displayPixelHeight = 7
            )
        )
        assertEquals(3, text["width"])
        assertEquals("007", text["text"])
        assertEquals("text", text["mode"])
        assertEquals(13, text["pixelWidth"])
        assertEquals(7, text["pixelHeight"])
        assertEquals(13, text["PIXEL_WIDTH"])
        assertEquals(7, text["PIXEL_HEIGHT"])

        val pixels = listOf("1000000000001", "0000000000000")
        val pixelResult = LuaModuleDescription.describe(
            LuaModuleSnapshot(
                2,
                "cc_aeroworks:three_digit_display",
                "display",
                displayWidth = 3,
                displayPixels = pixels,
                displayPixelWidth = 13,
                displayPixelHeight = 7
            )
        )
        assertEquals("pixels", pixelResult["mode"])
        assertEquals(pixels, pixelResult["pixels"])
        assertEquals(true, pixelResult["display"])
        assertEquals(13, pixelResult["PIXEL_WIDTH"])
        assertEquals(7, pixelResult["PIXEL_HEIGHT"])
    }
}
