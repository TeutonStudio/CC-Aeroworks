package de.teutonstudio.ccaeroworks.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeskDisplayResolutionTest {
    @Test fun `vanilla ppb follows physical model parts`() {
        assertEquals(7, DeskDisplayType.TWO_DIGIT.pixelWidthAt(16))
        assertEquals(7, DeskDisplayType.TWO_DIGIT.pixelHeightAt(16))
        assertEquals(10, DeskDisplayType.THREE_DIGIT.pixelWidthAt(16))
        assertEquals(7, DeskDisplayType.THREE_DIGIT.pixelHeightAt(16))
    }

    @Test fun `default ppb derives square-grid display resolutions`() {
        assertEquals(112, DeskDisplayType.TWO_DIGIT.pixelWidthAt(256))
        assertEquals(112, DeskDisplayType.TWO_DIGIT.pixelHeightAt(256))
        assertEquals(160, DeskDisplayType.THREE_DIGIT.pixelWidthAt(256))
        assertEquals(112, DeskDisplayType.THREE_DIGIT.pixelHeightAt(256))
    }

    @Test fun `non vanilla multiples stay inside the physical surface`() {
        assertEquals(7, DeskDisplayType.TWO_DIGIT.pixelWidthAt(17))
        assertEquals(10, DeskDisplayType.THREE_DIGIT.pixelWidthAt(17))
        assertEquals(7, DeskDisplayType.THREE_DIGIT.pixelHeightAt(17))
    }
}
