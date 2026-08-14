package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(value = [ConsoleScreen::class], remap = false)
interface ConsoleScreenAccessor {
    @Accessor("console")
    fun ccaeroworks_getConsole(): ConsoleBlockEntity
}
