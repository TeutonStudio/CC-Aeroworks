package de.teutonstudio.ccaeroworks.input

import net.minecraft.client.Minecraft

/** Routes raw keyboard edges into Combined control before vanilla key mappings see them. */
object CombinedKeyboardCapture {
    @JvmStatic
    fun capture(windowPointer: Long, key: Int, action: Int): Boolean {
        val minecraft = Minecraft.getInstance()
        if (windowPointer != minecraft.window.window) return false

        // Preserve the same priority as the NeoForge listeners: interactive displays get the
        // first opportunity, then ordinary analog controls. The coordinator prevents double use.
        val displayConsumed = DisplayCombinedInputController.captureKey(key, action, minecraft)
        val controlConsumed = CombinedLeverController.captureKey(key, action, minecraft)
        return displayConsumed || controlConsumed
    }
}
