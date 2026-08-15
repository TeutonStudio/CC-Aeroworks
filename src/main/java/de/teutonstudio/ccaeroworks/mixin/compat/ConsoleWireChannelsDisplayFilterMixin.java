package de.teutonstudio.ccaeroworks.mixin.compat;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes display-pointer x/y from Aeroworks' own DBW channel catalogue. */
@Pseudo
@Mixin(targets = "com.mred231.aeroworks.compat.drivebywire.ConsoleWireChannels", remap = false, priority = 2000)
public abstract class ConsoleWireChannelsDisplayFilterMixin {
    @Inject(method = "channelsFor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ccaeroworks$filterDisplayPointerChannels(
        final ConsoleBlockEntity desk,
        final CallbackInfoReturnable<List<String>> cir
    ) {
        final List<String> channels = cir.getReturnValue();
        if (channels == null || channels.isEmpty()) {
            return;
        }
        cir.setReturnValue(NativeDriveByWireChannels.INSTANCE.filterExposedIds(desk, channels));
    }
}
