package de.teutonstudio.ccaeroworks.mixin.compat;

import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.happysg.radar.block.datalink.DataLinkBlockItem", remap = false)
public abstract class CreateRadarDataLinkItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void ccaeroworks$linkRadarDisplay(
        UseOnContext context,
        CallbackInfoReturnable<InteractionResult> callback
    ) {
        InteractionResult result = CreateRadarCompat.handleDataLinkUse(context);
        if (result != null) {
            callback.setReturnValue(result);
        }
    }
}
