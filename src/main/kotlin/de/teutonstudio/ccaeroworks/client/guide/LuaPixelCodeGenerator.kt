package de.teutonstudio.ccaeroworks.client.guide

import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels

object LuaPixelCodeGenerator {
    @JvmStatic
    fun fullRaster(socket: String, pixels: DeskDisplayPixels): String = buildString {
        appendLine("desk.setDisplayPixels(")
        appendLine("  \"$socket\",")
        appendLine("  {")
        pixels.rows().forEachIndexed { index, row ->
            val comma = if (index < pixels.height - 1) "," else ""
            appendLine("    \"$row\"$comma")
        }
        appendLine("  }")
        append(")")
    }

    @JvmStatic
    fun singlePixel(socket: String, coordinate: PixelCoordinate, enabled: Boolean): String =
        "desk.setDisplayPixel(\"$socket\", ${coordinate.x + 1}, ${coordinate.y + 1}, $enabled)"
}
