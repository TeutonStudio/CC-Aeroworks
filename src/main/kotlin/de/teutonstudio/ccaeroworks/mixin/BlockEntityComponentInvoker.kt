package de.teutonstudio.ccaeroworks.mixin

import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(BlockEntity::class)
interface BlockEntityComponentInvoker {
    @Invoker("applyComponentsFromItemStack")
    fun ccaeroworks_applyComponentsFromItemStack(stack: ItemStack)
}
