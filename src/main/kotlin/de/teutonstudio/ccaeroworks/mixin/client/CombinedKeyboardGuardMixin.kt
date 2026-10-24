package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.input.CombinedKeyboardCapture
import net.minecraft.client.KeyboardHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Intercepts ControlDesk keyboard input before vanilla creates normal KeyMapping clicks from it.
 * InputEvent.Key is posted only after vanilla key processing, so an event-bus listener alone
 * cannot reserve keys such as inventory or mod-menu bindings while control focus is waiting for a
 * Combined activation edge.
 */
@Mixin(value = [KeyboardHandler::class], priority = 1100)
abstract class CombinedKeyboardGuardMixin {
    @Inject(method = ["keyPress(JIIII)V"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_captureCombinedKeyboardInput(
        windowPointer: Long,
        key: Int,
        scanCode: Int,
        action: Int,
        modifiers: Int,
        callback: CallbackInfo
    ) {
        if (CombinedKeyboardCapture.capture(windowPointer, key, scanCode, action)) {
            callback.cancel()
        }
    }
}
