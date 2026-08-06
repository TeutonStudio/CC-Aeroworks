package de.teutonstudio.ccaeroworks.mixin.compat;

import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity",
    remap = false
)
public abstract class CreateRadarNetworkControllerMixin {
    @Inject(method = "headlessTick", at = @At("RETURN"))
    private void ccaeroworks$refreshAdjacentDeskRadar(ServerLevel level, CallbackInfo callback) {
        CreateRadarCompat.refreshController(this);
    }
}
