package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.input.DisplayPrimaryMouseCapture
import net.minecraft.client.MouseHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Intercepts Minecraft's native mouse-button callback before NeoForge/vanilla routing.
 *
 * Only LEFT/RIGHT while a Combined display owns the mouse are cancelled. Normal Combined controls,
 * menus, Shift camera-only mode and ordinary gameplay keep Minecraft's native behavior.
 */
@Mixin(MouseHandler::class)
abstract class CombinedMouseButtonGuardMixin {
    @Inject(method = ["onPress(JIII)V"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_captureDisplayPrimaryMouse(
        windowPointer: Long,
        button: Int,
        action: Int,
        modifiers: Int,
        callback: CallbackInfo
    ) {
        if (DisplayPrimaryMouseCapture.capture(windowPointer, button, action)) {
            callback.cancel()
        }
    }
}
