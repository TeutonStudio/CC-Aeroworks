package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ModuleScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(value = [ModuleScreen::class], remap = false)
interface ModuleScreenAccessor {
    @Accessor("capturingColumn")
    fun ccaeroworks_getCapturingColumn(): Int

    @Accessor("capturingColumn")
    fun ccaeroworks_setCapturingColumn(column: Int)
}
