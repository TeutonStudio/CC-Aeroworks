package de.teutonstudio.ccaeroworks.radarcompat.mixin.createradar;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import de.teutonstudio.ccaeroworks.radarcompat.createradar.CreateRadarBearingOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes Create: Radars anchor the moved radar contraption on the side selected by
 * the bearing's vertical facing instead of always anchoring one block above it.
 *
 * The rotation axis remains Y for both UP and DOWN. Deliberately do not negate the
 * radar's global angle here: ControlledContraptionEntity rotates around an axis,
 * not a signed Direction, so negating only the scan angle would desynchronise the
 * reported contacts from the physically rendered receiver.
 */
@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity",
    remap = false
)
public abstract class CreateRadarBearingBlockEntityMixin {
    @ModifyExpressionValue(
        method = "createContraption",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;above()Lnet/minecraft/core/BlockPos;"
        ),
        require = 1,
        expect = 1
    )
    private BlockPos ccaeroworks$anchorContraptionOnFacingSide(BlockPos upstreamAnchor) {
        BlockEntity self = (BlockEntity) (Object) this;
        BlockState state = self.getBlockState();
        Direction stateFacing = state.hasProperty(BearingBlock.FACING)
            ? state.getValue(BearingBlock.FACING)
            : Direction.UP;
        Direction facing = CreateRadarBearingOrientation.verticalFacing(stateFacing);

        if (facing == Direction.UP) {
            return upstreamAnchor;
        }
        return CreateRadarBearingOrientation.attachmentPosition(self.getBlockPos(), facing);
    }
}
