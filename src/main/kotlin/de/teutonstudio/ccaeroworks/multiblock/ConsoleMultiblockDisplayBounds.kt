package de.teutonstudio.ccaeroworks.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.phys.AABB

/**
 * Resolves the Create Display Link selection bounds for an entire ControlDesk
 * multiblock. Create's DisplayLinkBlockItem renders this AABB with its own
 * Outliner, so returning one combined box keeps the native colour/line style
 * while removing all internal per-desk seams.
 */
object ConsoleMultiblockDisplayBounds {
    @JvmStatic
    fun resolve(levelAccessor: LevelAccessor, pos: BlockPos): AABB? {
        val level = levelAccessor as? Level ?: return null
        val network = ConsoleMultiblockManager.resolve(level, pos)
        if (network.members.isEmpty()) return null
        if (network.state == ConsoleNetworkState.PARTIALLY_LOADED ||
            network.state == ConsoleNetworkState.TOO_LARGE
        ) {
            return null
        }

        var bounds: AABB? = null
        for (segmentPos in network.segmentPositions) {
            val state = level.getBlockState(segmentPos)
            val shape = state.getShape(level, segmentPos)
            val memberBounds = if (shape.isEmpty) {
                AABB(segmentPos)
            } else {
                shape.bounds().move(segmentPos)
            }

            val current = bounds
            bounds = if (current == null) {
                memberBounds
            } else {
                AABB(
                    minOf(current.minX, memberBounds.minX),
                    minOf(current.minY, memberBounds.minY),
                    minOf(current.minZ, memberBounds.minZ),
                    maxOf(current.maxX, memberBounds.maxX),
                    maxOf(current.maxY, memberBounds.maxY),
                    maxOf(current.maxZ, memberBounds.maxZ)
                )
            }
        }
        return bounds
    }
}
