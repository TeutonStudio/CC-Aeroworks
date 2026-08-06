package de.teutonstudio.ccaeroworks.mixin.compat;

import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.happysg.radar.block.datalink.DataLinkBlockEntity", remap = false)
public abstract class CreateRadarDataLinkMixin {
    @Inject(method = "updateGatheredData", at = @At("RETURN"), require = 0)
    private void ccaeroworks$captureRadarData(CallbackInfo callback) {
        CreateRadarCompat.capture(this);
    }
}
