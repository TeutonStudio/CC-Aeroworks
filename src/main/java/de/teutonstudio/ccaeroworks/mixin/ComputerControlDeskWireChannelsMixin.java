package de.teutonstudio.ccaeroworks.mixin;

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock;
import de.teutonstudio.ccaeroworks.registry.CCDataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve ComputerControlDesk logical wiring on the CC computer item during ejection. */
@Mixin(value = ComputerControlDeskBlock.class, remap = false)
public abstract class ComputerControlDeskWireChannelsMixin {
    @Inject(method = "standaloneComputer(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), remap = false)
    private void ccaeroworks$copyChannelConfiguration(final ItemStack source, final CallbackInfoReturnable<ItemStack> cir) {
        final String channels = source.get(CCDataComponents.WIRE_CHANNELS.get());
        if (channels != null && !channels.isBlank()) cir.getReturnValue().set(CCDataComponents.WIRE_CHANNELS.get(), channels);
        final String groups = source.get(CCDataComponents.CHANNEL_GROUPS.get());
        if (groups != null && !groups.isBlank()) cir.getReturnValue().set(CCDataComponents.CHANNEL_GROUPS.get(), groups);
    }
}
