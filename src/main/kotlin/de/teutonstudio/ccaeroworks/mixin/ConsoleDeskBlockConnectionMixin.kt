package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.multiblock.ConsoleDeskConnections
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ConsoleDeskBlock::class], remap = false)
abstract class ConsoleDeskBlockConnectionMixin {
    @Inject(method = ["updateShape"], at = [At("RETURN")], cancellable = true)
    private fun ccaeroworks_connectComputerDesks(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos,
        callback: CallbackInfoReturnable<BlockState>
    ) {
        callback.returnValue = ConsoleDeskConnections.apply(level, pos, callback.returnValue)
    }
}
