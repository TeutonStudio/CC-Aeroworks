package de.teutonstudio.ccaeroworks.mixin;

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.console.CopycatConsoleDeskBlock;
import com.mred231.aeroworks.content.controls.console.DeskSkin;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes Copycat material payment and replacement atomic for a complete UUID row. */
@Mixin(value = CopycatConsoleDeskBlock.class, remap = false)
public abstract class CopycatConsoleDeskAssemblyMixin {
    @Inject(method = "applySkin", at = @At("HEAD"), cancellable = true)
    private void ccaeroworks$applyAssemblySkin(
        ConsoleBlockEntity desk,
        DeskSkin.Zone zone,
        BlockState material,
        ItemStack held,
        LivingEntity entity,
        CallbackInfo ci
    ) {
        if (ConsoleAssemblyStyles.applyPaidSkin(desk, zone, material, held, entity)) ci.cancel();
    }

    @Redirect(
        method = "onWrenchedFallback",
        at = @At(value = "INVOKE", target = "Lcom/mred231/aeroworks/content/controls/console/DeskSkin;clear(Lcom/mred231/aeroworks/content/controls/console/DeskSkin$Zone;)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack ccaeroworks$clearAssemblySkin(
        DeskSkin skin,
        DeskSkin.Zone zone,
        BlockState state,
        UseOnContext context
    ) {
        var blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (blockEntity instanceof ConsoleBlockEntity desk) {
            ItemStack result = ConsoleAssemblyStyles.clearPaidSkin(desk, zone, context.getPlayer());
            if (result != null) return result;
        }
        return skin.clear(zone);
    }
}
