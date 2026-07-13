package de.teutonstudio.ccaeroworks.compat.aeroworks

import com.mred231.aeroworks.content.controls.MountedModule
import com.mred231.aeroworks.content.controls.ModuleTypes
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.resources.ResourceLocation

object AeroworksModuleAccess {
    @JvmStatic
    fun id(module: MountedModule): ResourceLocation = ModuleTypes.idOf(module.type())

    @JvmStatic
    fun isDisplay(module: MountedModule): Boolean = CCModuleTypes.displayType(module.type()) != null

    @JvmStatic
    fun kind(module: MountedModule): String = when (id(module).toString()) {
        "aeroworks:lever" -> "lever"
        "aeroworks:joystick" -> "joystick"
        "aeroworks:button", "aeroworks:button_panel", "aeroworks:button_keypad" -> "button"
        "aeroworks:wheel" -> "steering_wheel"
        "aeroworks:yoke" -> "yoke"
        "aeroworks:throttle_quadrant" -> "throttle_quadrant"
        else -> if (isDisplay(module)) "display" else "module"
    }

    @JvmStatic
    fun values(module: MountedModule): LinkedHashMap<String, Int> {
        val values = LinkedHashMap<String, Int>()
        module.channels().forEach { channel -> values[channel.id()] = module.value(channel.id()) }
        return values
    }
}
