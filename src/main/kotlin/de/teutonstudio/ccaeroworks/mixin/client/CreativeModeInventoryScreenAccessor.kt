package de.teutonstudio.ccaeroworks.mixin.client

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(CreativeModeInventoryScreen::class)
interface CreativeModeInventoryScreenAccessor {
    @Accessor("scrollOffs")
    fun ccaeroworks_getScrollOffset(): Float
}
