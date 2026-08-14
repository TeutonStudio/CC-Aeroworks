package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSnapshot
import net.minecraft.world.level.Level
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Builds one selection shape for a complete ControlDesk multiblock.
 *
 * Every member contributes its normal Minecraft selection shape in anchor-local
 * coordinates. Boolean union plus VoxelShape.optimize() removes the shared faces
 * and seam edges between adjacent desks while preserving real outer/concave edges.
 */
object ConsoleMultiblockHighlightGeometry {
    @JvmStatic
    fun build(level: Level, snapshot: ConsoleMultiblockSnapshot): VoxelShape {
        if (snapshot.members.isEmpty()) return Shapes.empty()

        val anchor = snapshot.anchor
        var combined = Shapes.empty()
        snapshot.members.forEach { member ->
            val state = level.getBlockState(member.pos)
            val localShape = state.getShape(level, member.pos).move(
                (member.pos.x - anchor.x).toDouble(),
                (member.pos.y - anchor.y).toDouble(),
                (member.pos.z - anchor.z).toDouble()
            )
            combined = Shapes.or(combined, localShape)
        }
        return combined.optimize()
    }
}
