package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Single ownership gate for Combined mouse input.
 *
 * The owner survives the Shift camera override. Shift therefore changes routing only; it never
 * destroys a valid session or forces a new raycast when the player releases Shift.
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
    fun hasOwner(): Boolean = owner != null

    /** True while Combined owns mouse motion rather than the camera. */
    @JvmStatic
    fun consumesMouseInput(): Boolean {
        val minecraft = Minecraft.getInstance()
        return owner != null && !isShiftCameraOnly(minecraft)
    }

    /**
     * Aeroworks must not receive the same raw delta while a Combined session exists, including
     * during Shift override where that delta belongs exclusively to the vanilla camera.
     */
    @JvmStatic
    fun reservesMouseFromAeroworks(): Boolean = owner != null

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
