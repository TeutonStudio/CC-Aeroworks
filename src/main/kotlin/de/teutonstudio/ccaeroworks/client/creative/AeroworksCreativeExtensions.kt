package de.teutonstudio.ccaeroworks.client.creative

import net.minecraft.world.item.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

object AeroworksCreativeExtensions {
    private val aeroworksItems = CopyOnWriteArrayList<() -> ItemStack>()

    fun registerAeroworksItem(supplier: () -> ItemStack) {
        aeroworksItems += supplier
    }

    fun items(): List<ItemStack> = aeroworksItems.map { it() }.filterNot(ItemStack::isEmpty)

    fun isAeroworksItem(stack: ItemStack): Boolean =
        items().any { ItemStack.isSameItemSameComponents(it, stack) }
}
