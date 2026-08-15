package de.teutonstudio.ccaeroworks.compat.sable

import dev.ryanhcode.sable.Sable
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/**
 * Interaction geometry for ControlDesk packets which may target a Sable SubLevel plot.
 *
 * Sable stores SubLevel blocks at plot coordinates. Vanilla distance/mayInteract checks therefore
 * see a desk hundreds or thousands of blocks away even when its rendered rigid body is directly in
 * front of the player. Always project through Sable for player-facing reach/security checks while
 * keeping the original plot BlockPos for block-entity lookup.
 */
object SableInteractionGeometry {
    @JvmStatic
    fun withinReach(player: Player, level: Level, pos: BlockPos, padding: Double = 1.0): Boolean {
        val maximumDistance = player.blockInteractionRange() + padding
        val distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(
            level,
            player.position(),
            pos.center
        )
        return distanceSquared <= maximumDistance * maximumDistance
    }

    @JvmStatic
    fun mayInteract(player: Player, level: Level, pos: BlockPos): Boolean {
        val worldPosition = Sable.HELPER.projectOutOfSubLevel(level, pos.center)
        return level.mayInteract(player, BlockPos.containing(worldPosition))
    }
}
