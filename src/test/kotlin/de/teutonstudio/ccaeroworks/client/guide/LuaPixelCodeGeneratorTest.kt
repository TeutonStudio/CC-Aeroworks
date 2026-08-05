package de.teutonstudio.ccaeroworks.client.guide

import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LuaPixelCodeGeneratorTest {
    @Test
    fun `renders a complete configured raster Lua call`() {
        val type = DeskDisplayType.TWO_DIGIT
        var pixels = DeskDisplayPixels.blank(type)
        pixels = pixels
            .withPixel(0, 0, true)
            .withPixel(pixels.width - 1, pixels.height - 1, true)

        val code = LuaPixelCodeGenerator.fullRaster("left", pixels)
        val firstRow = "1" + "0".repeat(pixels.width - 1)
        val lastRow = "0".repeat(pixels.width - 1) + "1"

        assertTrue(code.startsWith("desk.setDisplayPixels(\n  \"left\",\n  {"))
        assertTrue(code.contains("    \"$firstRow\","))
        assertTrue(code.contains("    \"$lastRow\""))
        assertEquals(pixels.height + 5, code.lines().size)
    }

    @Test
    fun `converts internal coordinates to Lua coordinates`() {
        val type = DeskDisplayType.THREE_DIGIT
        val coordinate = PixelCoordinate(type.pixelWidth - 1, type.pixelHeight - 1)

        assertEquals(
            "desk.setDisplayPixel(\"big\", ${type.pixelWidth}, ${type.pixelHeight}, true)",
            LuaPixelCodeGenerator.singlePixel("big", coordinate, true)
        )
    }
}
