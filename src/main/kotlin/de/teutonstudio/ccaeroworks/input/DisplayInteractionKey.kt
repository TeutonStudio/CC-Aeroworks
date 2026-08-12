package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

object DisplayInteractionKey {
    const val TRANSLATION_KEY: String = "item.cc_aeroworks.three_digit_display"

    @JvmField
    val KEY_MAPPING: KeyMapping = KeyMapping(
        TRANSLATION_KEY,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        KeyMapping.CATEGORY_MISC
    )

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerKeyMappings)
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(KEY_MAPPING)
    }
}
