package de.teutonstudio.ccaeroworks.mixin.client

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(AbstractContainerScreen::class)
interface AbstractContainerScreenAccessor {
    @Accessor("menu")
    fun ccaeroworks_getMenu(): AbstractContainerMenu

    @Accessor("leftPos")
    fun ccaeroworks_getLeftPos(): Int

    @Accessor("topPos")
    fun ccaeroworks_getTopPos(): Int

    @Accessor("imageWidth")
    fun ccaeroworks_getImageWidth(): Int

    @Accessor("imageHeight")
    fun ccaeroworks_getImageHeight(): Int
}
