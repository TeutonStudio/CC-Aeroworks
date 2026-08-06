package de.teutonstudio.ccaeroworks.mixin.client

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(CreativeModeTab::class)
interface CreativeModeTabAccessor {
    @Accessor("displayItems")
    fun ccaeroworks_setDisplayItems(items: Collection<ItemStack>)

    @Accessor("displayItemsSearchTab")
    fun ccaeroworks_getSearchTabDisplayItems(): MutableSet<ItemStack>
}
