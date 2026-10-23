package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ConsoleBlock::class], remap = false)
abstract class ConsoleBlockSkinMixin {
    @Inject(method = ["useItemOn"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_extendAssembly(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
        callback: CallbackInfoReturnable<ItemInteractionResult>
    ) {
        if ((this as Any) !is ConsoleDeskBlock) return
        de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyPlacementHelper.tryPlace(
            stack, state, level, pos, player, hand, hit
        )?.let { callback.returnValue = it }
    }

    @Inject(method = ["createBlockStateDefinition"], at = [At("TAIL")])
    private fun ccaeroworks_addMultiblockSkin(
        builder: StateDefinition.Builder<Block, BlockState>,
        callback: CallbackInfo
    ) {
        if ((this as Any) is ConsoleDeskBlock) {
            builder.add(ConsoleMultiblockSkinState.SKIN)
        }
    }
}
