package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.console.ConsoleScreenOpener;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.network.OpenConsoleInventoryPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Static injections must not introduce Kotlin companion classes into the Mixin package. */
@Mixin(value = ConsoleScreenOpener.class, remap = false)
public abstract class ConsoleScreenOpenerInventoryMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private static void ccaeroworks$openInventory(ConsoleBlockEntity console, CallbackInfo ci) {
        if (!AeroworksTypes.INSTANCE.isControlDesk(console.getBlockState().getBlock())) return;
        PacketDistributor.sendToServer(new OpenConsoleInventoryPayload(console.getBlockPos()));
        ci.cancel();
    }

    @Inject(method = "hasOverview", at = @At("HEAD"), cancellable = true)
    private static void ccaeroworks$alwaysOverview(ConsoleBlockEntity console, CallbackInfoReturnable<Boolean> cir) {
        if (AeroworksTypes.INSTANCE.isControlDesk(console.getBlockState().getBlock())) cir.setReturnValue(true);
    }
}
