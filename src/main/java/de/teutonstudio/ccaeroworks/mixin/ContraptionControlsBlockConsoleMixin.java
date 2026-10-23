package de.teutonstudio.ccaeroworks.mixin;

import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ContraptionControlsBlock overrides both shape methods from ControlsBlock. The state,
 * placement and neighbour hooks remain supplied by the superclass mixin, while these
 * explicit hooks prevent subclass inheritance from producing partial compatibility.
 */
@Mixin(ContraptionControlsBlock.class)
public abstract class ContraptionControlsBlockConsoleMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void ccaeroworks$consoleOutline(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context,
        CallbackInfoReturnable<VoxelShape> cir
    ) {
        VoxelShape shape = TrainControlConsoleIntegration.integratedShape(state, level, pos, context, false);
        if (shape != null) cir.setReturnValue(shape);
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void ccaeroworks$consoleCollision(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context,
        CallbackInfoReturnable<VoxelShape> cir
    ) {
        VoxelShape shape = TrainControlConsoleIntegration.integratedShape(state, level, pos, context, true);
        if (shape != null) cir.setReturnValue(shape);
    }
}
