package de.teutonstudio.ccaeroworks.mixin;

import com.mred231.aeroworks.content.controls.console.ConsoleBlock;
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import de.teutonstudio.ccaeroworks.train.TrainControlMounts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes a correctly mounted train-control module participate in Create train assembly. */
@Mixin(CarriageContraption.class)
public abstract class CarriageContraptionTrainControlsMixin {
    @Shadow private Direction assemblyDirection;
    @Shadow private boolean forwardControls;
    @Shadow private boolean backwardControls;
    @Shadow private boolean sidewaysControls;

    @Inject(method = "capture", at = @At("HEAD"))
    private void ccaeroworks$detectMountedTrainControls(
        Level level,
        BlockPos worldPos,
        CallbackInfoReturnable<Pair<StructureBlockInfo, BlockEntity>> cir
    ) {
        final BlockState state = level.getBlockState(worldPos);
        if (!TrainControlMounts.isSuitableConsole(state)) return;
        if (!(level.getBlockEntity(worldPos) instanceof ConsoleBlockEntity desk)) return;
        if (!TrainControlMounts.hasTrainControls(desk)) return;

        final Direction facing = state.getValue(ConsoleBlock.FACING);
        switch (TrainControlMounts.alignment(assemblyDirection, facing)) {
            case FORWARD -> forwardControls = true;
            case BACKWARD -> backwardControls = true;
            case SIDEWAYS -> sidewaysControls = true;
        }
    }

    @Inject(method = "inControl", at = @At("HEAD"), cancellable = true)
    private void ccaeroworks$recognizeConsoleBesideSeat(
        BlockPos seatPos,
        Direction direction,
        CallbackInfoReturnable<Boolean> cir
    ) {
        final CarriageContraption self = (CarriageContraption) (Object) this;
        final StructureBlockInfo info = self.getBlocks().get(seatPos.relative(direction));
        if (info == null || !TrainControlMounts.hasTrainControls(info.state(), info.nbt())) return;
        if (info.state().getValue(ConsoleBlock.FACING) == direction.getOpposite()) {
            cir.setReturnValue(true);
        }
    }
}
