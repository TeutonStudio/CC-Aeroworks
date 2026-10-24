package de.teutonstudio.ccaeroworks.radarcompat.display

import net.minecraft.core.Direction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadarTargetSelectionTest {
    @Test
    fun mapsFramedTouchCoordinatesToTheCenteredNativeRadarSquare() {
        assertEquals(RadarContactScreenPosition(0.5, 0.5), nativeRadarTouch(RadarDisplayType.SMALL, 0.5, 0.5))
        assertNull(nativeRadarTouch(RadarDisplayType.SMALL, 0.0, 0.5))
        assertNull(nativeRadarTouch(RadarDisplayType.LARGE, 0.0, 0.5))
    }

    @Test
    fun projectsNorthFacingContactsLikeTheNativeMonitorRenderer() {
        assertEquals(
            RadarContactScreenPosition(0.3125, 0.6875),
            projectRadarContact(relativeX = 50.0, relativeZ = 50.0, range = 100.0, Direction.NORTH)
        )
    }

    @Test
    fun rotatesProjectionWithTheMountedDisplay() {
        assertEquals(
            RadarContactScreenPosition(0.3125, 0.3125),
            projectRadarContact(relativeX = 50.0, relativeZ = 50.0, range = 100.0, Direction.EAST)
        )
        assertEquals(
            RadarContactScreenPosition(0.6875, 0.3125),
            projectRadarContact(relativeX = 50.0, relativeZ = 50.0, range = 100.0, Direction.SOUTH)
        )
        assertEquals(
            RadarContactScreenPosition(0.6875, 0.6875),
            projectRadarContact(relativeX = 50.0, relativeZ = 50.0, range = 100.0, Direction.WEST)
        )
    }

    @Test
    fun rejectsInvalidRangesAndContactsOutsideTheNativeDisplay() {
        assertNull(projectRadarContact(0.0, 0.0, 0.0, Direction.NORTH))
        assertNull(projectRadarContact(101.0, 0.0, 100.0, Direction.NORTH))
    }

    @Test
    fun appliesTheNativeShipRelativeRotationBeforeProjection() {
        assertEquals(
            RadarContactScreenPosition(0.5, 0.6875),
            projectRadarContact(50.0, 0.0, 100.0, Direction.NORTH, Math.PI / 2.0)
        )
    }
}
