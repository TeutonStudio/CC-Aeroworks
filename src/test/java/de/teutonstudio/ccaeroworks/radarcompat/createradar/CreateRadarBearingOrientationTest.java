package de.teutonstudio.ccaeroworks.radarcompat.createradar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateRadarBearingOrientationTest {
    @Test
    void undersidePlacementFacesDown() {
        assertEquals(Direction.DOWN, CreateRadarBearingOrientation.placementFacing(Direction.DOWN));
    }

    @Test
    void allOtherPlacementFacesPreserveUpwardCompatibility() {
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN) {
                assertEquals(Direction.UP, CreateRadarBearingOrientation.placementFacing(direction));
            }
        }
    }

    @Test
    void horizontalStatesAreSanitisedToUp() {
        assertEquals(Direction.UP, CreateRadarBearingOrientation.verticalFacing(Direction.NORTH));
        assertEquals(Direction.UP, CreateRadarBearingOrientation.verticalFacing(Direction.EAST));
        assertEquals(Direction.UP, CreateRadarBearingOrientation.verticalFacing(null));
        assertEquals(Direction.DOWN, CreateRadarBearingOrientation.verticalFacing(Direction.DOWN));
    }

    @Test
    void attachmentPositionFollowsVerticalFacing() {
        BlockPos origin = new BlockPos(10, 64, -3);
        assertEquals(origin.above(), CreateRadarBearingOrientation.attachmentPosition(origin, Direction.UP));
        assertEquals(origin.below(), CreateRadarBearingOrientation.attachmentPosition(origin, Direction.DOWN));
    }
}
