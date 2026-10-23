package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.input.CombinedKeyboardCapture
import net.minecraft.client.KeyboardHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Intercepts Combined activation keys before vanilla creates normal KeyMapping clicks from them.
 * InputEvent.Key is posted only after vanilla key processing, so an event-bus listener alone
 * cannot prevent bindings such as chat or an emote wheel from reacting to the same press.
 */
@Mixin(KeyboardHandler::class)
abstract class CombinedKeyboardGuardMixin {
    @Inject(method = ["keyPress(JIIII)V"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_captureCombinedActivationKey(
        windowPointer: Long,
        key: Int,
        scanCode: Int,
        action: Int,
        modifiers: Int,
        callback: CallbackInfo
    ) {
        if (CombinedKeyboardCapture.capture(windowPointer, key, action)) {
            callback.cancel()
        }
    }
}
