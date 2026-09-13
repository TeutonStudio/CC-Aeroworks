package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.ConsoleScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(value = [ConsoleScreen::class], remap = false)
interface ConsoleScreenAccessor {
    @Accessor("console")
    fun ccaeroworks_getConsole(): ConsoleBlockEntity

    @Accessor("windowLeft")
    fun ccaeroworks_getWindowLeft(): Int

    @Accessor("windowTop")
    fun ccaeroworks_getWindowTop(): Int
}
