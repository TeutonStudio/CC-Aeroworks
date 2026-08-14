package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Exclusive mouse ownership for Combined control focus.
 *
 * Ownership is deliberately non-preemptive: once a control or display session owns the mouse,
 * another binding cannot steal it halfway through the physical key press. Shift remains the
 * explicit camera-only escape hatch.
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
        return when (owner) {
            null, Owner.DISPLAY -> {
                owner = Owner.DISPLAY
                true
            }
            Owner.CONTROL -> false
        }
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
