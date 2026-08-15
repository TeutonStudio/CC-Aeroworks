package de.teutonstudio.ccaeroworks.display

import java.util.Base64

class DeskDisplayPixels private constructor(
    val width: Int,
    val height: Int,
    private val packedBits: ByteArray
) {
    init {
        require(width > 0 && height > 0) { "display dimensions must be positive" }
        require(packedBits.size == packedByteCount(width, height)) { "packed pixel payload has the wrong size" }
    }

    fun get(x: Int, y: Int): Boolean {
        require(x in 0 until width && y in 0 until height)
        val index = y * width + x
        val mask = 1 shl (index and 7)
        return packedBits[index ushr 3].toInt() and mask != 0
    }

    fun withPixel(x: Int, y: Int, enabled: Boolean): DeskDisplayPixels {
        require(x in 0 until width && y in 0 until height)
        val index = y * width + x
        val byteIndex = index ushr 3
        val mask = 1 shl (index and 7)
        val next = packedBits.copyOf()
        val current = next[byteIndex].toInt() and 0xFF
        next[byteIndex] = if (enabled) {
            (current or mask).toByte()
        } else {
            (current and mask.inv()).toByte()
        }
        return DeskDisplayPixels(width, height, next)
    }

    fun rows(): List<String> = List(height) { y ->
        CharArray(width) { x -> if (get(x, y)) '1' else '0' }.concatToString()
    }

    /**
     * Version 2 stores dimensions explicitly and bit-packs the raster before Base64 encoding.
     * A 160x112 display therefore stores 2,240 raw bytes instead of 17,920 ASCII bit characters.
     */
    fun encode(): String = buildString {
        append(PREFIX_V2)
        append(width).append(':').append(height).append(':')
        append(Base64.getUrlEncoder().withoutPadding().encodeToString(packedBits))
    }

    override fun equals(other: Any?): Boolean =
        other is DeskDisplayPixels &&
            width == other.width &&
            height == other.height &&
            packedBits.contentEquals(other.packedBits)

    override fun hashCode(): Int = 31 * (31 * width + height) + packedBits.contentHashCode()

    companion object {
        private const val PREFIX_V1: String = "@cca_pixels_1:"
        private const val PREFIX_V2: String = "@cca_pixels_2:"

        @JvmStatic
        fun pixelWidth(type: DeskDisplayType): Int = type.pixelWidth

        @JvmStatic
        fun pixelHeight(type: DeskDisplayType): Int = type.pixelHeight

        @JvmStatic
        fun isEncoded(stored: String): Boolean =
            stored.startsWith(PREFIX_V1) || stored.startsWith(PREFIX_V2)

        @JvmStatic
        fun blank(type: DeskDisplayType): DeskDisplayPixels =
            blank(type.pixelWidth, type.pixelHeight)

        @JvmStatic
        fun blank(width: Int, height: Int): DeskDisplayPixels =
            DeskDisplayPixels(width, height, ByteArray(packedByteCount(width, height)))

        @JvmStatic
        fun fromRows(type: DeskDisplayType, rows: List<String>): DeskDisplayPixels {
            val width = type.pixelWidth
            val height = type.pixelHeight
            require(rows.size == height) { "pixel table must contain exactly $height rows" }
            require(rows.all { it.length == width }) { "every pixel row must contain exactly $width characters" }
            require(rows.all { row -> row.all { it == '0' || it == '1' } }) { "pixel rows may contain only 0 and 1" }

            val packed = ByteArray(packedByteCount(width, height))
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, bit ->
                    if (bit == '1') setPacked(packed, y * width + x)
                }
            }
            return DeskDisplayPixels(width, height, packed)
        }

        /**
         * Returns null for an encoded raster whose stored dimensions no longer match the current
         * PPB. Callers can distinguish that migration case with [isEncoded] instead of treating
         * the payload as ordinary display text.
         */
        @JvmStatic
        fun decode(type: DeskDisplayType, stored: String): DeskDisplayPixels? = when {
            stored.startsWith(PREFIX_V2) -> decodeV2(type, stored.removePrefix(PREFIX_V2))
            stored.startsWith(PREFIX_V1) -> decodeV1(type, stored.removePrefix(PREFIX_V1))
            else -> null
        }

        private fun decodeV2(type: DeskDisplayType, payload: String): DeskDisplayPixels? {
            val first = payload.indexOf(':')
            if (first <= 0) return null
            val second = payload.indexOf(':', first + 1)
            if (second <= first + 1) return null
            val width = payload.substring(0, first).toIntOrNull() ?: return null
            val height = payload.substring(first + 1, second).toIntOrNull() ?: return null
            if (width != type.pixelWidth || height != type.pixelHeight) return null
            val packed = runCatching {
                Base64.getUrlDecoder().decode(payload.substring(second + 1))
            }.getOrNull() ?: return null
            if (packed.size != packedByteCount(width, height)) return null
            clearUnusedTailBits(packed, safePixelCount(width, height))
            return DeskDisplayPixels(width, height, packed)
        }

        private fun decodeV1(type: DeskDisplayType, bits: String): DeskDisplayPixels? {
            val width = type.pixelWidth
            val height = type.pixelHeight
            val expected = safePixelCount(width, height)
            if (bits.length != expected || bits.any { it != '0' && it != '1' }) return null
            val packed = ByteArray(packedByteCount(width, height))
            bits.forEachIndexed { index, bit -> if (bit == '1') setPacked(packed, index) }
            return DeskDisplayPixels(width, height, packed)
        }

        private fun setPacked(packed: ByteArray, index: Int) {
            val byteIndex = index ushr 3
            val mask = 1 shl (index and 7)
            packed[byteIndex] = ((packed[byteIndex].toInt() and 0xFF) or mask).toByte()
        }

        private fun clearUnusedTailBits(packed: ByteArray, pixelCount: Int) {
            val usedBits = pixelCount and 7
            if (usedBits == 0 || packed.isEmpty()) return
            val mask = (1 shl usedBits) - 1
            packed[packed.lastIndex] = ((packed.last().toInt() and 0xFF) and mask).toByte()
        }

        private fun packedByteCount(width: Int, height: Int): Int {
            val count = safePixelCount(width, height)
            return ((count.toLong() + 7L) / 8L).toInt()
        }

        private fun safePixelCount(width: Int, height: Int): Int {
            require(width > 0 && height > 0) { "display dimensions must be positive" }
            val count = width.toLong() * height.toLong()
            require(count <= Int.MAX_VALUE) { "display raster exceeds the JVM collection limit" }
            return count.toInt()
        }
    }
}
