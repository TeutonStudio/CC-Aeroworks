package de.teutonstudio.ccaeroworks.client

import com.mojang.blaze3d.platform.InputConstants
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.client.KeyMapping
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

object CCKeyMappings {
    @JvmField
    val COMBINED_LEVER: KeyMapping = KeyMapping(
        "key.cc_aeroworks.combined_lever",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        "key.categories.cc_aeroworks"
    )

    fun register(bus: IEventBus) {
        bus.addListener(::registerMappings)
    }

    private fun registerMappings(event: RegisterKeyMappingsEvent) {
        event.register(COMBINED_LEVER)
        CCAeroworks.LOGGER.debug("[CC-Aeroworks] Registered combined lever key mapping")
    }
}
