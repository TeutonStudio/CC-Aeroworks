package de.teutonstudio.ccaeroworks.mixin.compat;

import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity",
    remap = false
)
public abstract class CreateRadarNetworkControllerMixin {
    @Inject(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/happysg/radar/block/controller/networkcontroller/NetworkFiltererBlockEntity;)V",
        at = @At("TAIL")
    )
    private static void ccaeroworks$refreshAdjacentDeskRadar(
        Level level,
        BlockPos position,
        BlockState state,
        @Coerce Object controller,
        CallbackInfo callback
    ) {
        CreateRadarCompat.refreshController(controller);
    }
}
