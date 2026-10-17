package de.teutonstudio.ccaeroworks.mixin

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.computer.ConsoleComputerLifecycle
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(net.minecraft.world.level.block.state.BlockBehaviour::class)
abstract class ConsoleBlockComputerRemovalMixin {
    @Inject(method = ["onRemove"], at = [At("HEAD")])
    private fun ccaeroworks_transferComputer(state: BlockState, level: Level, pos: BlockPos, next: BlockState, moved: Boolean, ci: CallbackInfo) {
        if (level is ServerLevel && AeroworksTypes.isControlDesk(state.block))
            ConsoleComputerLifecycle.removing(level, pos, state, next)
    }

    @Inject(method = ["isSignalSource"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_signalSource(state: BlockState, cir: CallbackInfoReturnable<Boolean>) {
        if (AeroworksTypes.isControlDesk(state.block)) cir.returnValue = true
    }

    @Inject(method = ["getSignal", "getDirectSignal"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_signal(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction, cir: CallbackInfoReturnable<Int>) {
        if (AeroworksTypes.isControlDesk(state.block)) cir.returnValue = ConsoleComputers.get(level.getBlockEntity(pos))?.redstoneOutput(side) ?: 0
    }
}
