package de.teutonstudio.ccaeroworks.compat.sable

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

/**
 * Small coordinate-space bridge for gameplay interaction code.
 *
 * ControlDesk block positions on a Sable SubLevel are plot/local coordinates while player eye,
 * reach and movement coordinates live in world space. Keeping those spaces implicit is harmless
 * on a normal level and catastrophically wrong once a ship is translated or rotated.
 */
object SableSpatial {
    data class RaySpace(
        val from: Vec3,
        val to: Vec3,
        val subLevel: dev.ryanhcode.sable.sublevel.SubLevel?
    )

    /**
     * Converts a world-space interaction ray into the coordinate space containing [blockEntity].
     * Normal-level block entities keep the original ray.
     */
    @JvmStatic
    fun localRay(blockEntity: BlockEntity, from: Vec3, to: Vec3): RaySpace {
        val subLevel = Sable.HELPER.getContaining(blockEntity)
            ?: return RaySpace(from, to, null)
        val pose = subLevel.logicalPose()
        return RaySpace(
            pose.transformPositionInverse(from),
            pose.transformPositionInverse(to),
            subLevel
        )
    }

    /**
     * Returns the main-level ray plus one inverse-projected ray for every intersected SubLevel.
     * This mirrors Sable's own sublevel-aware raycast strategy and is used only during Combined
     * target acquisition, never in the hot mouse-delta path.
     */
    @JvmStatic
    fun raySpaces(level: Level, from: Vec3, to: Vec3): List<RaySpace> {
        val spaces = mutableListOf(RaySpace(from, to, null))
        Sable.HELPER.getAllIntersecting(level, BoundingBox3d(from, to)).forEach { subLevel ->
            val pose = subLevel.logicalPose()
            spaces += RaySpace(
                pose.transformPositionInverse(from),
                pose.transformPositionInverse(to),
                subLevel
            )
        }
        return spaces
    }

    /**
     * Distance comparison where either point may be a Sable plot coordinate.
     */
    @JvmStatic
    fun distanceSquared(level: Level, first: Vec3, second: Vec3): Double =
        Sable.HELPER.distanceSquaredWithSubLevels(level, first, second)
}
