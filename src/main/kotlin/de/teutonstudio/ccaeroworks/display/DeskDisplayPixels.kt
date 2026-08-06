package de.teutonstudio.ccaeroworks.display

data class DeskDisplayPixels(
    val width: Int,
    val height: Int,
    private val bits: String
) {
    init {
        require(bits.length == safePixelCount(width, height))
        require(bits.all { it == '0' || it == '1' })
    }

    fun get(x: Int, y: Int): Boolean = bits[y * width + x] == '1'

    fun withPixel(x: Int, y: Int, enabled: Boolean): DeskDisplayPixels {
        require(x in 0 until width && y in 0 until height)
        val index = y * width + x
        val next = bits.toCharArray()
        next[index] = if (enabled) '1' else '0'
        return DeskDisplayPixels(width, height, next.concatToString())
    }

    fun rows(): List<String> = (0 until height).map { y -> bits.substring(y * width, (y + 1) * width) }

    fun encode(): String = PREFIX + bits

    companion object {
        private const val PREFIX: String = "@cca_pixels_1:"

        @JvmStatic
        fun pixelWidth(type: DeskDisplayType): Int = type.pixelWidth

        @JvmStatic
        fun pixelHeight(type: DeskDisplayType): Int = type.pixelHeight

        @JvmStatic
        fun blank(type: DeskDisplayType): DeskDisplayPixels {
            val width = type.pixelWidth
            val height = type.pixelHeight
            return DeskDisplayPixels(width, height, "0".repeat(safePixelCount(width, height)))
        }

        @JvmStatic
        fun fromRows(type: DeskDisplayType, rows: List<String>): DeskDisplayPixels {
            val width = type.pixelWidth
            val height = type.pixelHeight
            require(rows.size == height) { "pixel table must contain exactly $height rows" }
            require(rows.all { it.length == width }) { "every pixel row must contain exactly $width characters" }
            require(rows.all { row -> row.all { it == '0' || it == '1' } }) { "pixel rows may contain only 0 and 1" }
            return DeskDisplayPixels(width, height, rows.joinToString(""))
        }

        @JvmStatic
        fun decode(type: DeskDisplayType, stored: String): DeskDisplayPixels? {
            if (!stored.startsWith(PREFIX)) return null
            val bits = stored.removePrefix(PREFIX)
            val width = type.pixelWidth
            val height = type.pixelHeight
            val expected = safePixelCount(width, height)
            return bits.takeIf { it.length == expected && it.all { bit -> bit == '0' || bit == '1' } }
                ?.let { DeskDisplayPixels(width, height, it) }
        }

        private fun safePixelCount(width: Int, height: Int): Int {
            require(width > 0 && height > 0) { "display dimensions must be positive" }
            val count = width.toLong() * height.toLong()
            require(count <= Int.MAX_VALUE) { "display raster exceeds the JVM string limit" }
            return count.toInt()
        }
    }
}
