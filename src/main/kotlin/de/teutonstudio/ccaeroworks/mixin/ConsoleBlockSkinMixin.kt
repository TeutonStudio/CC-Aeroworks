package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlock
import com.mred231.aeroworks.content.controls.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ConsoleBlock::class], remap = false)
abstract class ConsoleBlockSkinMixin {
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
