package de.teutonstudio.ccaeroworks.display

import de.teutonstudio.ccaeroworks.config.CCServerConfig

data class DeskDisplayResolution(val width: Int, val height: Int)

enum class DeskDisplayType(
    val width: Int,
    val modulePath: String,
    val surfaceWidthParts: Int,
    val surfaceHeightParts: Int
) {
    TWO_DIGIT(2, "two_digit_display", 7, 7),
    THREE_DIGIT(3, "three_digit_display", 10, 7);

    val partsPerBlock: Int
        get() = CCServerConfig.displayPartsPerBlockValue()

    val pixelWidth: Int
        get() = pixelWidthAt(partsPerBlock)

    val pixelHeight: Int
        get() = pixelHeightAt(partsPerBlock)

    val pixelResolution: DeskDisplayResolution
        get() = DeskDisplayResolution(pixelWidth, pixelHeight)

    /** Physical distance between two neighbouring pixel centres in block units. */
    val pixelPitchBlocks: Double
        get() = 1.0 / partsPerBlock.toDouble()

    /** Scale of the vanilla-16-PPB pixel partial required for the configured PPB. */
    val pixelModelScale: Float
        get() = VANILLA_PARTS_PER_BLOCK.toFloat() / partsPerBlock.toFloat()

    fun pixelWidthAt(partsPerBlock: Int): Int = pixelCount(surfaceWidthParts, partsPerBlock)

    fun pixelHeightAt(partsPerBlock: Int): Int = pixelCount(surfaceHeightParts, partsPerBlock)

    companion object {
        /** Minecraft block textures use 16 texels per block edge as the physical reference density. */
        const val VANILLA_PARTS_PER_BLOCK: Int = 16

        /** Default programmable display density. */
        const val DEFAULT_PARTS_PER_BLOCK: Int = 256

        @JvmStatic
        fun pixelCount(surfaceParts: Int, partsPerBlock: Int): Int {
            require(surfaceParts > 0) { "surfaceParts must be positive" }
            require(partsPerBlock > 0) { "partsPerBlock must be positive" }
            // Floor deliberately keeps the raster inside the physical display when PPB is not a
            // multiple of 16. The fractional remainder becomes an equal sub-pixel margin because
            // rendering centres the resulting raster on the display surface.
            val count = surfaceParts.toLong() * partsPerBlock.toLong() / VANILLA_PARTS_PER_BLOCK
            return count.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
