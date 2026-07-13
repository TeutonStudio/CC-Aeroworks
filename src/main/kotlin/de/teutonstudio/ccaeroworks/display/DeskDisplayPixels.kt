package de.teutonstudio.ccaeroworks.display

data class DeskDisplayPixels(
    val width: Int,
    val height: Int,
    private val bits: String
) {
    init {
        require(bits.length == width * height)
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
        const val HEIGHT: Int = 5
        private const val PIXELS_PER_DIGIT: Int = 3
        private const val DIGIT_GAP: Int = 1
        private const val PREFIX: String = "@cca_pixels_1:"

        @JvmStatic
        fun pixelWidth(type: DeskDisplayType): Int = type.width * PIXELS_PER_DIGIT + (type.width - 1) * DIGIT_GAP

        @JvmStatic
        fun blank(type: DeskDisplayType): DeskDisplayPixels =
            DeskDisplayPixels(pixelWidth(type), HEIGHT, "0".repeat(pixelWidth(type) * HEIGHT))

        @JvmStatic
        fun fromRows(type: DeskDisplayType, rows: List<String>): DeskDisplayPixels {
            val width = pixelWidth(type)
            require(rows.size == HEIGHT) { "pixel table must contain exactly $HEIGHT rows" }
            require(rows.all { it.length == width }) { "every pixel row must contain exactly $width characters" }
            require(rows.all { row -> row.all { it == '0' || it == '1' } }) { "pixel rows may contain only 0 and 1" }
            return DeskDisplayPixels(width, HEIGHT, rows.joinToString(""))
        }

        @JvmStatic
        fun decode(type: DeskDisplayType, stored: String): DeskDisplayPixels? {
            if (!stored.startsWith(PREFIX)) return null
            val bits = stored.removePrefix(PREFIX)
            val width = pixelWidth(type)
            return bits.takeIf { it.length == width * HEIGHT && it.all { bit -> bit == '0' || bit == '1' } }
                ?.let { DeskDisplayPixels(width, HEIGHT, it) }
        }
    }
}
