package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Owns the mouse while a combined-input session is active.
 *
 * Display input deliberately has pre-emption priority over an already active analog control so
 * pressing the display activation key cannot leak the same mouse sample into a lever/joystick.
 * Shift is an absolute camera override and prevents either owner from being claimed.
 */
object CombinedInputCoordinator {
    enum class Owner {
        CONTROL,
        DISPLAY
    }

    private var owner: Owner? = null

    @JvmStatic
    fun isShiftCameraOnly(minecraft: Minecraft): Boolean {
        val window = minecraft.window.window
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
            InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
    }

    @JvmStatic
    fun claimControl(minecraft: Minecraft): Boolean {
        if (isShiftCameraOnly(minecraft)) return false
        return when (owner) {
            null, Owner.CONTROL -> {
                owner = Owner.CONTROL
                true
            }
            Owner.DISPLAY -> false
        }
    }

    @JvmStatic
    fun claimDisplay(minecraft: Minecraft): Boolean {
        if (isShiftCameraOnly(minecraft)) return false
        owner = Owner.DISPLAY
        return true
    }

    @JvmStatic
    fun ownsControl(): Boolean = owner == Owner.CONTROL

    @JvmStatic
    fun ownsDisplay(): Boolean = owner == Owner.DISPLAY

    @JvmStatic
    fun consumesMouseInput(): Boolean {
        val minecraft = Minecraft.getInstance()
        return !isShiftCameraOnly(minecraft) && owner != null
    }

    @JvmStatic
    fun releaseControl() {
        if (owner == Owner.CONTROL) owner = null
    }

    @JvmStatic
    fun releaseDisplay() {
        if (owner == Owner.DISPLAY) owner = null
    }

    @JvmStatic
    fun reset() {
        owner = null
    }
}
