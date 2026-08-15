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
        get() = CCServerConfig.pixelWidth(this)

    val pixelHeight: Int
        get() = CCServerConfig.pixelHeight(this)

    val pixelResolution: DeskDisplayResolution
        get() = DeskDisplayResolution(pixelWidth, pixelHeight)

    /** Distance between neighboring logical pixel centers in block units. */
    val pixelPitchBlocks: Double
        get() = 1.0 / partsPerBlock.toDouble()

    /**
     * The pixel partial model was authored for the vanilla 16-parts-per-block density.
     * Scaling it with this factor keeps its footprint proportional to the logical pixel cell.
     */
    val pixelModelScale: Float
        get() = VANILLA_PARTS_PER_BLOCK.toFloat() / partsPerBlock.toFloat()

    fun pixelWidthAt(partsPerBlock: Int): Int = pixelCount(surfaceWidthParts, partsPerBlock)

    fun pixelHeightAt(partsPerBlock: Int): Int = pixelCount(surfaceHeightParts, partsPerBlock)

    companion object {
        /** Minecraft's standard texture density: one block side is 16 texture pixels/parts. */
        const val VANILLA_PARTS_PER_BLOCK: Int = 16

        /** Default logical display density requested for programmable displays. */
        const val DEFAULT_PARTS_PER_BLOCK: Int = 256

        @JvmStatic
        fun pixelCount(surfaceParts: Int, partsPerBlock: Int): Int {
            require(surfaceParts > 0) { "surfaceParts must be positive" }
            require(partsPerBlock > 0) { "partsPerBlock must be positive" }

            // Floor keeps the raster inside the physical surface for PPB values that are not
            // divisible by 16. The remaining sub-pixel margin is centered by the renderer.
            return ((surfaceParts.toLong() * partsPerBlock.toLong()) / VANILLA_PARTS_PER_BLOCK)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }
}
