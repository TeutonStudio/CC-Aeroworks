package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.foundation.input.InputSource
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object DisplayInteractionKey {
    const val TRANSLATION_KEY: String = "key.cc_aeroworks.display_interaction"

    @JvmStatic
    fun binding(): String = CCClientConfig.displayInteractionBinding.get().trim()

    @JvmStatic
    fun setBinding(key: InputConstants.Key?) {
        CCClientConfig.displayInteractionBinding.set(key?.name.orEmpty())
    }

    @JvmStatic
    fun clearBinding() {
        CCClientConfig.displayInteractionBinding.set("")
    }

    @JvmStatic
    fun isPhysicallyDown(minecraft: Minecraft): Boolean {
        val binding = binding()
        return binding.isNotBlank() && CombinedActivationKey.isDown(binding, minecraft)
    }

    @JvmStatic
    fun isDown(minecraft: Minecraft): Boolean =
        !CombinedInputCoordinator.isShiftCameraOnly(minecraft) && isPhysicallyDown(minecraft)

    @JvmStatic
    fun displayMessage(): Component {
        val binding = binding()
        return if (binding.isBlank()) {
            Component.translatable("gui.aeroworks.joystick.bind_unbound")
        } else {
            InputSource.displayName(binding)
        }
    }
}
