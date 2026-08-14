package de.teutonstudio.ccaeroworks.mixin;

import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import de.teutonstudio.ccaeroworks.registry.CCDataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve virtual wire definitions when a duplicate embedded computer is ejected from a desk row. */
@Mixin(ConsoleMultiblockManager.class)
public abstract class ConsoleMultiblockWireChannelsMixin {
    @Inject(method = "standaloneComputer", at = @At("RETURN"))
    private void ccaeroworks$copyWireChannels(
        final ItemStack source,
        final boolean advanced,
        final CallbackInfoReturnable<ItemStack> cir
    ) {
        final String channels = source.get(CCDataComponents.WIRE_CHANNELS.get());
        if (channels != null && !channels.isBlank()) {
            cir.getReturnValue().set(CCDataComponents.WIRE_CHANNELS.get(), channels);
        }
    }
}
