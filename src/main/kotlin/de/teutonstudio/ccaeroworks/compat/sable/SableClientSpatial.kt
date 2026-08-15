package de.teutonstudio.ccaeroworks.compat.sable

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

/**
 * Client-only interaction-space bridge. Sable's vanilla GameRenderer picking uses the current
 * interpolated render pose while rendering a frame, so custom gaze acquisition must do the same
 * or a translating/rotating ship can be visible in one place and tested in another.
 */
object SableClientSpatial {
    data class RaySpace(
        val from: Vec3,
        val to: Vec3,
        val subLevel: ClientSubLevel?
    )

    /**
     * Converts a world-space interaction ray into the render-space plot coordinates containing
     * [blockEntity]. Normal-level block entities keep the original ray.
     */
    @JvmStatic
    fun localRay(blockEntity: BlockEntity, from: Vec3, to: Vec3): RaySpace {
        val subLevel = Sable.HELPER.getContainingClient(blockEntity)
            ?: return RaySpace(from, to, null)
        val pose = subLevel.renderPose()
        return RaySpace(
            pose.transformPositionInverse(from),
            pose.transformPositionInverse(to),
            subLevel
        )
    }

    /**
     * Returns the main-level ray plus one inverse-projected ray for every intersected client
     * SubLevel. This mirrors Sable's own BlockGetter#clip strategy but uses ClientSubLevel#renderPose
     * to match the currently displayed ship pose instead of the tick-only logical pose.
     */
    @JvmStatic
    fun raySpaces(level: Level, from: Vec3, to: Vec3): List<RaySpace> {
        val spaces = mutableListOf(RaySpace(from, to, null))
        Sable.HELPER.getAllIntersecting(level, BoundingBox3d(from, to)).forEach { candidate ->
            val subLevel = candidate as? ClientSubLevel ?: return@forEach
            val pose = subLevel.renderPose()
            spaces += RaySpace(
                pose.transformPositionInverse(from),
                pose.transformPositionInverse(to),
                subLevel
            )
        }
        return spaces
    }

    /**
     * Keeps scans inside the coordinate space represented by [subLevel], preventing a plot-local
     * corridor from accidentally accepting a block belonging to another Sable plot.
     */
    @JvmStatic
    fun belongsTo(level: Level, pos: BlockPos, subLevel: ClientSubLevel?): Boolean =
        Sable.HELPER.getContaining(level, pos) === subLevel
}
