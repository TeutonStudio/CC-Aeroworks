package de.teutonstudio.ccaeroworks.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeskDisplayTouchResolverTest {
    @Test
    fun mapsNormalizedCoordinatesToOneBasedCells() {
        assertEquals(1, DeskDisplayTouchResolver.gridCoordinate(0.0, 11))
        assertEquals(1, DeskDisplayTouchResolver.gridCoordinate(0.09, 11))
        assertEquals(6, DeskDisplayTouchResolver.gridCoordinate(0.5, 11))
        assertEquals(11, DeskDisplayTouchResolver.gridCoordinate(0.999, 11))
        assertEquals(11, DeskDisplayTouchResolver.gridCoordinate(1.0, 11))
    }

    @Test
    fun clampsEdgesAndSingleCellGrids() {
        assertEquals(1, DeskDisplayTouchResolver.gridCoordinate(-1.0, 11))
        assertEquals(11, DeskDisplayTouchResolver.gridCoordinate(2.0, 11))
        assertEquals(1, DeskDisplayTouchResolver.gridCoordinate(0.5, 1))
    }
}
