package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ModuleColumn
import com.mred231.aeroworks.content.controls.ModuleScreen
import com.mred231.aeroworks.content.controls.ModuleSetting
import com.mred231.aeroworks.content.controls.MountedModule
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(value = [ModuleScreen::class], remap = false)
interface ModuleScreenInvoker {
    @Invoker("module")
    fun ccaeroworks_module(): MountedModule?

    @Invoker("modeToggleAt")
    fun ccaeroworks_modeToggleAt(mouseX: Int, mouseY: Int): Int

    @Invoker("bindAreaAt")
    fun ccaeroworks_bindAreaAt(mouseX: Int, mouseY: Int): Int

    @Invoker("analogDriven")
    fun ccaeroworks_analogDriven(column: ModuleColumn): Boolean

    @Invoker("sendAnalogSource")
    fun ccaeroworks_sendAnalogSource(column: Int, source: String)

    @Invoker("sendBind")
    fun ccaeroworks_sendBind(column: Int, source: String)

    @Invoker("bindFor")
    fun ccaeroworks_bindFor(column: ModuleColumn): String

    @Invoker("sendChannelFlag")
    fun ccaeroworks_sendChannelFlag(column: ModuleColumn, setting: ModuleSetting, enabled: Boolean)
}
