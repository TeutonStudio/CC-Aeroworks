package de.teutonstudio.ccaeroworks.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeskDisplayPixelsTest {
    @Test
    fun packedVersionTwoRoundTripsDefaultLargeRaster() {
        val original = DeskDisplayPixels.blank(160, 112)
            .withPixel(0, 0, true)
            .withPixel(159, 111, true)
            .withPixel(80, 56, true)

        val encoded = original.encode()
        assertTrue(encoded.startsWith("@cca_pixels_2:"))
        assertTrue(encoded.length < 4_000)

        val decoded = DeskDisplayPixels.decode(DeskDisplayType.THREE_DIGIT, encoded)
        assertEquals(original, decoded)
        assertTrue(decoded!!.get(0, 0))
        assertTrue(decoded.get(159, 111))
        assertTrue(decoded.get(80, 56))
        assertFalse(decoded.get(1, 1))
    }

    @Test
    fun batchedPatchCopiesOnceAndCountsOnlyActualChanges() {
        val original = DeskDisplayPixels.blank(8, 4).withPixel(1, 1, true)

        val patch = original.withPixels(
            listOf(
                1 to 1, // already enabled
                2 to 1,
                2 to 1, // duplicate must not count twice
                7 to 3
            ),
            true
        )

        assertEquals(2, patch.changed)
        assertTrue(patch.pixels.get(1, 1))
        assertTrue(patch.pixels.get(2, 1))
        assertTrue(patch.pixels.get(7, 3))
        assertFalse(original.get(2, 1))
        assertFalse(original.get(7, 3))
    }

    @Test
    fun legacyOrWrongSizedRasterIsRecognizedButNotDecodedAsCurrentPixels() {
        val legacy = "@cca_pixels_1:" + "0".repeat(55)
        assertTrue(DeskDisplayPixels.isEncoded(legacy))
        assertNull(DeskDisplayPixels.decode(DeskDisplayType.THREE_DIGIT, legacy))
    }
}
