package de.teutonstudio.ccaeroworks.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeskDisplayResolutionTest {
    @Test
    fun vanillaDensityMatchesPhysicalSurfaceParts() {
        assertEquals(7, DeskDisplayType.TWO_DIGIT.pixelWidthAt(16))
        assertEquals(7, DeskDisplayType.TWO_DIGIT.pixelHeightAt(16))
        assertEquals(10, DeskDisplayType.THREE_DIGIT.pixelWidthAt(16))
        assertEquals(7, DeskDisplayType.THREE_DIGIT.pixelHeightAt(16))
    }

    @Test
    fun defaultDensityProducesSquarePitchRaster() {
        assertEquals(112, DeskDisplayType.TWO_DIGIT.pixelWidthAt(256))
        assertEquals(112, DeskDisplayType.TWO_DIGIT.pixelHeightAt(256))
        assertEquals(160, DeskDisplayType.THREE_DIGIT.pixelWidthAt(256))
        assertEquals(112, DeskDisplayType.THREE_DIGIT.pixelHeightAt(256))
    }

    @Test
    fun nonMultipleDensityFloorsInsidePhysicalSurface() {
        assertEquals(111, DeskDisplayType.TWO_DIGIT.pixelWidthAt(255))
        assertEquals(111, DeskDisplayType.TWO_DIGIT.pixelHeightAt(255))
        assertEquals(159, DeskDisplayType.THREE_DIGIT.pixelWidthAt(255))
        assertEquals(111, DeskDisplayType.THREE_DIGIT.pixelHeightAt(255))
    }
}
