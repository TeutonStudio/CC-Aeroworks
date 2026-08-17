package de.teutonstudio.ccaeroworks.radarcompat.mixin.createradar;

import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import de.teutonstudio.ccaeroworks.radarcompat.createradar.CreateRadarBearingOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Create: Radars' hard-coded UP contraption facing with the vertical
 * facing stored on the radar bearing block before BearingContraption assembles.
 */
@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.radar.bearing.RadarContraption",
    remap = false
)
public abstract class CreateRadarBearingContraptionMixin extends BearingContraption {
    @Inject(
        method = "assemble(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        require = 1
    )
    private void ccaeroworks$useBearingFacing(
        Level level,
        BlockPos bearingPos,
        CallbackInfoReturnable<Boolean> callback
    ) {
        BlockState state = level.getBlockState(bearingPos);
        Direction stateFacing = state.hasProperty(BearingBlock.FACING)
            ? state.getValue(BearingBlock.FACING)
            : Direction.UP;
        this.facing = CreateRadarBearingOrientation.verticalFacing(stateFacing);
    }
}
