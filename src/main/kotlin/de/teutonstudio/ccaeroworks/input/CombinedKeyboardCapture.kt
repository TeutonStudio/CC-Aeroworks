package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.AeroworksKeys
import com.mred231.aeroworks.content.controls.console.ConsoleControlClient
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Routes raw keyboard edges into Combined control before vanilla key mappings see them.
 *
 * The whole ControlDesk focus owns ordinary keyboard bindings, including the idle phase before a
 * Combined activation key is pressed. Native Aeroworks bindings are forwarded here; unbound keys
 * are still consumed so inventory or mod-menu actions cannot steal potential control keys.
 */
object CombinedKeyboardCapture {
    @JvmStatic
    fun capture(windowPointer: Long, key: Int, scanCode: Int, action: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (windowPointer != minecraft.window.window) return false

        // Preserve the same priority as the NeoForge listeners: interactive displays get the
        // first opportunity, then ordinary analog controls. The coordinator prevents double use.
        val controlFocusBefore = ConsoleControlClient.isActive() && minecraft.screen == null
        val displayConsumed = DisplayCombinedInputController.captureKey(key, action, minecraft)
        val controlConsumed = CombinedLeverController.captureKey(key, action, minecraft)
        val routedConsumption = displayConsumed || controlConsumed
        val input = InputConstants.getKey(key, scanCode)
        val consume = shouldConsumeControlModeKey(
            controlFocusBefore = controlFocusBefore,
            routedConsumption = routedConsumption,
            key = key,
            freeCameraKey = AeroworksKeys.isFreeCameraKey(input)
        )

        // Aeroworks' own raw-key mixin would normally receive these edges later in the same method.
        // Because we reserve even unbound keys, forward native control edges before cancelling.
        if (consume && controlFocusBefore && !routedConsumption && action != GLFW.GLFW_REPEAT) {
            ConsoleControlClient.handleKey(input, action == GLFW.GLFW_PRESS)
        }
        return consume
    }
}

internal fun shouldConsumeControlModeKey(
    controlFocusBefore: Boolean,
    routedConsumption: Boolean,
    key: Int,
    freeCameraKey: Boolean
): Boolean {
    return routedConsumption ||
        (controlFocusBefore && key != GLFW.GLFW_KEY_ESCAPE && !freeCameraKey)
}
