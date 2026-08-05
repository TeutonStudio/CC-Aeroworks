package de.teutonstudio.ccaeroworks.client.guide

import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LuaPixelCodeGeneratorTest {
    @Test
    fun `renders a complete five row Lua call`() {
        var pixels = DeskDisplayPixels.blank(DeskDisplayType.TWO_DIGIT)
        pixels = pixels.withPixel(0, 0, true).withPixel(6, 4, true)

        val code = LuaPixelCodeGenerator.fullRaster("left", pixels)

        assertTrue(code.startsWith("desk.setDisplayPixels(\n  \"left\",\n  {"))
        assertTrue(code.contains("    \"1000000\","))
        assertTrue(code.contains("    \"0000001\""))
        assertEquals(10, code.lines().size)
    }

    @Test
    fun `converts internal coordinates to Lua coordinates`() {
        assertEquals(
            "desk.setDisplayPixel(\"big\", 11, 5, true)",
            LuaPixelCodeGenerator.singlePixel("big", PixelCoordinate(10, 4), true)
        )
    }
}
