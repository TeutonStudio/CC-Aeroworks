package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object CombinedActivationKey {
    fun isDown(binding: String, minecraft: Minecraft): Boolean {
        if (binding.isBlank()) return false
        val key = try {
            InputConstants.getKey(binding)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val window = minecraft.window.window
        return when (key.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(window, key.value) == GLFW.GLFW_PRESS
            InputConstants.Type.KEYSYM -> InputConstants.isKeyDown(window, key.value)
            // Aeroworks normally stores keyboard captures as KEYSYM. A raw scancode is not
            // safely interchangeable with a GLFW keycode, so an unusual SCANCODE stays inactive.
            InputConstants.Type.SCANCODE -> false
        }
    }
}
