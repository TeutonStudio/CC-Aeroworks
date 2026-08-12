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

    @Test
    fun mapsPointerCoordinatesBackToDisplayPlane() {
        assertEquals(DeskDisplayGeometry.MIN_X, DeskDisplayGeometry.localX(0.0), 1.0e-9)
        assertEquals(DeskDisplayGeometry.MAX_X, DeskDisplayGeometry.localX(1.0), 1.0e-9)
        assertEquals(DeskDisplayGeometry.MAX_Z, DeskDisplayGeometry.localZ(0.0), 1.0e-9)
        assertEquals(DeskDisplayGeometry.MIN_Z, DeskDisplayGeometry.localZ(1.0), 1.0e-9)
    }
}
