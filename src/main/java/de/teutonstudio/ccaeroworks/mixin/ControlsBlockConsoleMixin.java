package de.teutonstudio.ccaeroworks.mixin;

import com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyPlacement;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyBlockEntity;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds opt-in Aeroworks console geometry to Create's original train-controls block. */
@Mixin(ControlsBlock.class)
public abstract class ControlsBlockConsoleMixin implements EntityBlock {
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        ItemInteractionResult placed = de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyPlacementHelper
            .tryPlace(stack, state, level, pos, player, hand, hit);
        return placed == null ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : placed;
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if ((Object)this instanceof ContraptionControlsBlock controls) {
            return controls.getBlockEntityType().create(pos, state);
        }
        return new ConsoleAssemblyBlockEntity(pos, state);
    }
    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void ccaeroworks$addConsoleState(
        StateDefinition.Builder<Block, BlockState> builder,
        CallbackInfo ci
    ) {
        builder.add(
            TrainControlConsoleIntegration.GEOMETRY,
            TrainControlConsoleIntegration.COPYCAT,
            ConsoleDeskBlock.OPEN_EAST,
            ConsoleDeskBlock.OPEN_WEST,
            ConsoleMultiblockSkinState.SKIN
        );
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void ccaeroworks$inheritClickedConsole(
        BlockPlaceContext context,
        CallbackInfoReturnable<BlockState> cir
    ) {
        ConsoleAssemblyPlacement.capture(context);
        cir.setReturnValue(TrainControlConsoleIntegration.stateForPlacement(cir.getReturnValue(), context));
    }

    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void ccaeroworks$updateConsoleConnections(
        BlockState state,
        Direction direction,
        BlockState neighbour,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighbourPos,
        CallbackInfoReturnable<BlockState> cir
    ) {
        cir.setReturnValue(TrainControlConsoleIntegration.applyConnections(level, pos, cir.getReturnValue()));
    }

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
