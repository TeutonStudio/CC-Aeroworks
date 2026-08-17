package de.teutonstudio.ccaeroworks.radarcompat.createradar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Shared orientation rules for Create: Radars bearings.
 *
 * Create: Radars currently hard-codes its radar bearing to face upward. CC-Aeroworks
 * deliberately extends that contract only to the opposite vertical direction. A
 * horizontal radar bearing would require pitch-axis scanning semantics and is outside
 * this compatibility feature.
 */
public final class CreateRadarBearingOrientation {
    private CreateRadarBearingOrientation() {
    }

    /**
     * A radar bearing placed against the underside of a block faces down. Every other
     * placement keeps Create: Radars' historical upward orientation.
     */
    public static Direction placementFacing(Direction clickedFace) {
        return clickedFace == Direction.DOWN ? Direction.DOWN : Direction.UP;
    }

    /**
     * Keeps the compatibility layer vertical even if an invalid/hacked horizontal
     * block state reaches the assembly code.
     */
    public static Direction verticalFacing(Direction facing) {
        return facing != null && facing.getAxis() == Direction.Axis.Y ? facing : Direction.UP;
    }

    public static BlockPos attachmentPosition(BlockPos bearingPos, Direction facing) {
        return bearingPos.relative(verticalFacing(facing));
    }
}
