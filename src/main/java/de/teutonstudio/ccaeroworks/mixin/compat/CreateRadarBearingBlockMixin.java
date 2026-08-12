package de.teutonstudio.ccaeroworks.mixin.compat;

import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarBearingOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows Create: Radars' bearing to be mounted below a ceiling while preserving
 * its existing floor-mounted behaviour everywhere else.
 */
@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.radar.bearing.RadarBearingBlock",
    remap = false
)
public abstract class CreateRadarBearingBlockMixin {
    @Inject(
        method = "getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
    )
    private void ccaeroworks$allowCeilingPlacement(
        BlockPlaceContext context,
        CallbackInfoReturnable<BlockState> callback
    ) {
        BlockState state = callback.getReturnValue();
        if (state == null || !state.hasProperty(BearingBlock.FACING)) {
            return;
        }

        Direction facing = CreateRadarBearingOrientation.placementFacing(context.getClickedFace());
        callback.setReturnValue(state.setValue(BearingBlock.FACING, facing));
    }
}
