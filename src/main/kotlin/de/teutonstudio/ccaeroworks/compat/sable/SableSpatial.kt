package de.teutonstudio.ccaeroworks.compat.sable

import dev.ryanhcode.sable.Sable
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Coordinate-space bridge for gameplay logic which stores ControlDesk positions in Sable plot
 * coordinates while players and visible interaction distances live in world coordinates.
 *
 * This object intentionally uses Sable's logical pose through its public helper methods. That is
 * the correct authority for server validation and tick-stable gameplay checks. Client-side gaze
 * acquisition uses [SableClientSpatial] instead so moving ships are tested against the same
 * interpolated render pose that Sable uses for vanilla picking.
 */
object SableSpatial {
    /**
     * Projects a plot/local block position to the visible world block used by Vanilla permission
     * checks such as Level#mayInteract. Normal world positions pass through unchanged.
     */
    @JvmStatic
    fun worldBlockPos(level: Level, pos: BlockPos): BlockPos =
        BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, pos.center))

    /**
     * Distance comparison where either point may be a Sable plot coordinate. Delegating this to
     * Sable avoids comparing a world-space player directly with a plot-space desk position.
     */
    @JvmStatic
    fun distanceSquared(level: Level, first: Vec3, second: Vec3): Double =
        Sable.HELPER.distanceSquaredWithSubLevels(level, first, second)
}
