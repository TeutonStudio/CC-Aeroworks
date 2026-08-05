package de.teutonstudio.ccaeroworks.display

import de.teutonstudio.ccaeroworks.config.CCServerConfig

data class DeskDisplayResolution(val width: Int, val height: Int)

enum class DeskDisplayType(val width: Int, val modulePath: String) {
    TWO_DIGIT(2, "two_digit_display"),
    THREE_DIGIT(3, "three_digit_display");

    val pixelWidth: Int
        get() = CCServerConfig.pixelWidth(this)

    val pixelHeight: Int
        get() = CCServerConfig.pixelHeight(this)

    val pixelResolution: DeskDisplayResolution
        get() = DeskDisplayResolution(pixelWidth, pixelHeight)

    companion object {
        const val DEFAULT_SMALL_PIXEL_WIDTH: Int = 7
        const val DEFAULT_SMALL_PIXEL_HEIGHT: Int = 5
        const val DEFAULT_LARGE_PIXEL_WIDTH: Int = 11
        const val DEFAULT_LARGE_PIXEL_HEIGHT: Int = 5
    }
}
