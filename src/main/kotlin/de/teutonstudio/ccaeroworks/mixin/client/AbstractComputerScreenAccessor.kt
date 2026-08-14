package de.teutonstudio.ccaeroworks.mixin.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.shared.computer.core.ComputerFamily
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(value = [AbstractComputerScreen::class], remap = false)
interface AbstractComputerScreenAccessor {
    @Accessor("family")
    fun ccaeroworks_getFamily(): ComputerFamily

    @Accessor("sidebarYOffset")
    fun ccaeroworks_getSidebarYOffset(): Int
}
