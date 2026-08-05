package de.teutonstudio.ccaeroworks.client.guide

import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayType

data class PixelCoordinate(val x: Int, val y: Int)

class PixelEditorState {
    var displayType: DeskDisplayType = DeskDisplayType.TWO_DIGIT
        private set

    var socketName: String = "left"
        private set

    var selectedPixelValue: Boolean = true
        private set

    var lastEditedPixel: PixelCoordinate? = null
        private set

    private val pixelsByType: MutableMap<DeskDisplayType, DeskDisplayPixels> =
        DeskDisplayType.entries.associateWith(DeskDisplayPixels::blank).toMutableMap()

    val pixels: DeskDisplayPixels
        get() = pixelsByType.getValue(displayType)

    val width: Int
        get() = pixels.width

    val height: Int
        get() = pixels.height

    fun selectDisplayType(type: DeskDisplayType) {
        displayType = type
        if (type == DeskDisplayType.THREE_DIGIT) socketName = "big"
        lastEditedPixel = null
    }

    fun availableSockets(): List<String> = when (displayType) {
        DeskDisplayType.TWO_DIGIT -> SMALL_DISPLAY_SOCKETS
        DeskDisplayType.THREE_DIGIT -> LARGE_DISPLAY_SOCKETS
    }

    fun selectSocket(socket: String) {
        require(socket in availableSockets()) { "Socket '$socket' is not valid for $displayType" }
        socketName = socket
    }

    fun selectPixelValue(enabled: Boolean) {
        selectedPixelValue = enabled
    }

    fun setPixel(x: Int, y: Int, enabled: Boolean = selectedPixelValue) {
        pixelsByType[displayType] = pixels.withPixel(x, y, enabled)
        lastEditedPixel = PixelCoordinate(x, y)
    }

    fun clear() {
        pixelsByType[displayType] = DeskDisplayPixels.blank(displayType)
        lastEditedPixel = null
    }

    fun fill() {
        pixelsByType[displayType] = DeskDisplayPixels.fromRows(
            displayType,
            List(pixels.height) { "1".repeat(pixels.width) }
        )
        lastEditedPixel = null
    }

    fun invert() {
        pixelsByType[displayType] = DeskDisplayPixels.fromRows(
            displayType,
            pixels.rows().map { row ->
                row.map { bit -> if (bit == '1') '0' else '1' }.joinToString("")
            }
        )
        lastEditedPixel = null
    }

    private companion object {
        val SMALL_DISPLAY_SOCKETS: List<String> = listOf("left", "right", "big")
        val LARGE_DISPLAY_SOCKETS: List<String> = listOf("big")
    }
}
