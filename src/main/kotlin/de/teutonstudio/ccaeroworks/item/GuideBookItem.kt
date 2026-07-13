package de.teutonstudio.ccaeroworks.item

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.WrittenBookItem

class GuideBookItem(properties: Item.Properties) : WrittenBookItem(properties) {
    override fun getName(stack: ItemStack): Component = Component.translatable(descriptionId)
}
