package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.multiblock.ConsoleDeskConnections
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.context.BlockPlaceContext
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ConsoleDeskBlock::class], remap = false)
abstract class ConsoleDeskBlockConnectionMixin {
    @Inject(method = ["getStateForPlacement"], at = [At("RETURN")], cancellable = true)
    private fun ccaeroworks_captureAssemblyPlacement(
        context: BlockPlaceContext,
        callback: CallbackInfoReturnable<BlockState>
    ) {
        de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyPlacement.capture(context)
        val state = callback.returnValue ?: return
        if (context.player?.isShiftKeyDown == true || context.replacingClickedOnBlock()) return
        val targetPos = context.clickedPos.relative(context.clickedFace.opposite)
        val target = context.level.getBlockState(targetPos)
        if (!target.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) return
        val targetFacing = target.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
        if (context.clickedFace != targetFacing.clockWise && context.clickedFace != targetFacing.counterClockWise) return
        if (de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyAdapters.profile(target) == null &&
            !de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration.isConsoleControl(target)
        ) return
        callback.returnValue = state.setValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
            targetFacing
        )
    }

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
