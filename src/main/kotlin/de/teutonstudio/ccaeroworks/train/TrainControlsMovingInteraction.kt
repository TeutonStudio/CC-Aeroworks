package de.teutonstudio.ccaeroworks.train

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInteractionBehaviour
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player

/** Uses Create's native controller session, but only for consoles carrying our module. */
class TrainControlsMovingInteraction : ControlsInteractionBehaviour() {
    override fun handlePlayerInteraction(
        player: Player,
        hand: InteractionHand,
        localPos: BlockPos,
        entity: AbstractContraptionEntity
    ): Boolean {
        val info = entity.contraption.blocks[localPos] ?: return false
        if (!TrainControlMounts.hasTrainControls(info.state(), info.nbt())) return false
        return super.handlePlayerInteraction(player, hand, localPos, entity)
    }
}
